/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package org.graalvm.compiler.test;

import static org.graalvm.compiler.debug.DebugContext.DEFAULT_LOG_STREAM;
import static org.graalvm.compiler.debug.DebugContext.NO_DESCRIPTION;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.internal.ComparisonCriteria;
import org.junit.internal.ExactComparisonCriteria;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

/**
 * Base class that contains common utility methods and classes useful in unit tests.
 */
public class GraalTest {

    public static final Unsafe UNSAFE;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe", e);
        }
    }

    public static final boolean Java8OrEarlier = JavaVersionUtil.Java8OrEarlier;
    public static final boolean Java11OrEarlier = JavaVersionUtil.Java11OrEarlier;

    protected Method getMethod(String methodName) {
        return getMethod(getClass(), methodName);
    }

    protected Method getMethod(Class<?> clazz, String methodName) {
        Method found = null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                Assert.assertNull(found);
                found = m;
            }
        }
        if (found == null) {
            /* Now look for non-public methods (but this does not look in superclasses). */
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    Assert.assertNull(found);
                    found = m;
                }
            }
        }
        if (found != null) {
            return found;
        } else {
            throw new RuntimeException("method not found: " + methodName);
        }
    }

    protected Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("method not found: " + methodName + "" + Arrays.toString(parameterTypes));
        }
    }

    /**
     * Compares two given objects for {@linkplain Assert#assertEquals(Object, Object) equality}.
     * Does a deep copy equality comparison if {@code expected} is an array.
     */
    protected void assertDeepEquals(Object expected, Object actual) {
        assertDeepEquals(null, expected, actual);
    }

    /**
     * Compares two given objects for {@linkplain Assert#assertEquals(Object, Object) equality}.
     * Does a deep copy equality comparison if {@code expected} is an array.
     *
     * @param message the identifying message for the {@link AssertionError}
     */
    protected void assertDeepEquals(String message, Object expected, Object actual) {
        if (ulpsDelta() > 0) {
            assertDeepEquals(message, expected, actual, ulpsDelta());
        } else {
            assertDeepEquals(message, expected, actual, equalFloatsOrDoublesDelta());
        }
    }

    /**
     * Compares two given values for equality, doing a recursive test if both values are arrays of
     * the same type.
     *
     * @param message the identifying message for the {@link AssertionError}
     * @param delta the maximum delta between two doubles or floats for which both numbers are still
     *            considered equal.
     */
    protected void assertDeepEquals(String message, Object expected, Object actual, double delta) {
        if (expected != null && actual != null) {
            Class<?> expectedClass = expected.getClass();
            Class<?> actualClass = actual.getClass();
            if (expectedClass.isArray()) {
                Assert.assertEquals(message, expectedClass, actual.getClass());
                if (expected instanceof int[]) {
                    Assert.assertArrayEquals(message, (int[]) expected, (int[]) actual);
                } else if (expected instanceof byte[]) {
                    Assert.assertArrayEquals(message, (byte[]) expected, (byte[]) actual);
                } else if (expected instanceof char[]) {
                    Assert.assertArrayEquals(message, (char[]) expected, (char[]) actual);
                } else if (expected instanceof short[]) {
                    Assert.assertArrayEquals(message, (short[]) expected, (short[]) actual);
                } else if (expected instanceof float[]) {
                    Assert.assertArrayEquals(message, (float[]) expected, (float[]) actual, (float) delta);
                } else if (expected instanceof long[]) {
                    Assert.assertArrayEquals(message, (long[]) expected, (long[]) actual);
                } else if (expected instanceof double[]) {
                    Assert.assertArrayEquals(message, (double[]) expected, (double[]) actual, delta);
                } else if (expected instanceof boolean[]) {
                    new ExactComparisonCriteria().arrayEquals(message, expected, actual);
                } else if (expected instanceof Object[]) {
                    new ComparisonCriteria() {
                        @Override
                        protected void assertElementsEqual(Object e, Object a) {
                            assertDeepEquals(message, e, a, delta);
                        }
                    }.arrayEquals(message, expected, actual);
                } else {
                    Assert.fail((message == null ? "" : message) + "non-array value encountered: " + expected);
                }
            } else if (expectedClass.equals(double.class) && actualClass.equals(double.class)) {
                Assert.assertEquals((double) expected, (double) actual, delta);
            } else if (expectedClass.equals(float.class) && actualClass.equals(float.class)) {
                Assert.assertEquals((float) expected, (float) actual, delta);
            } else {
                Assert.assertEquals(message, expected, actual);
            }
        } else {
            Assert.assertEquals(message, expected, actual);
        }
    }

    /**
     * Compares two given values for equality, doing a recursive test if both values are arrays of
     * the same type. Uses {@linkplain StrictMath#ulp(float) ULP}s for comparison of floats.
     *
     * @param message the identifying message for the {@link AssertionError}
     * @param ulpsDelta the maximum allowed ulps difference between two doubles or floats for which
     *            both numbers are still considered equal.
     */
    protected void assertDeepEquals(String message, Object expected, Object actual, int ulpsDelta) {
        ComparisonCriteria doubleUlpsDeltaCriteria = new ComparisonCriteria() {
            @Override
            protected void assertElementsEqual(Object e, Object a) {
                assertTrue(message, e instanceof Double && a instanceof Double);
                // determine acceptable error based on whether it is a normal number or a NaN/Inf
                double de = (Double) e;
                double epsilon = (!Double.isNaN(de) && Double.isFinite(de) ? ulpsDelta * Math.ulp(de) : 0);
                Assert.assertEquals(message, (Double) e, (Double) a, epsilon);
            }
        };

        ComparisonCriteria floatUlpsDeltaCriteria = new ComparisonCriteria() {
            @Override
            protected void assertElementsEqual(Object e, Object a) {
                assertTrue(message, e instanceof Float && a instanceof Float);
                // determine acceptable error based on whether it is a normal number or a NaN/Inf
                float fe = (Float) e;
                float epsilon = (!Float.isNaN(fe) && Float.isFinite(fe) ? ulpsDelta * Math.ulp(fe) : 0);
                Assert.assertEquals(message, (Float) e, (Float) a, epsilon);
            }
        };

        if (expected != null && actual != null) {
            Class<?> expectedClass = expected.getClass();
            Class<?> actualClass = actual.getClass();
            if (expectedClass.isArray()) {
                Assert.assertEquals(message, expectedClass, actualClass);
                if (expected instanceof double[] || expected instanceof Object[]) {
                    doubleUlpsDeltaCriteria.arrayEquals(message, expected, actual);
                    return;
                } else if (expected instanceof float[] || expected instanceof Object[]) {
                    floatUlpsDeltaCriteria.arrayEquals(message, expected, actual);
                    return;
                }
            } else if (expectedClass.equals(double.class) && actualClass.equals(double.class)) {
                doubleUlpsDeltaCriteria.arrayEquals(message, expected, actual);
                return;
            } else if (expectedClass.equals(float.class) && actualClass.equals(float.class)) {
                floatUlpsDeltaCriteria.arrayEquals(message, expected, actual);
                return;
            }
        }
        // anything else just use the non-ulps version
        assertDeepEquals(message, expected, actual, equalFloatsOrDoublesDelta());
    }

    /**
     * @see "https://bugs.openjdk.java.net/browse/JDK-8076557"
     */
    public static void assumeManagementLibraryIsLoadable() {
        try {
            /* Trigger loading of the management library using the bootstrap class loader. */
            GraalServices.getCurrentThreadAllocatedBytes();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | UnsupportedOperationException e) {
            throw new AssumptionViolatedException("Management interface is unavailable: " + e);
        }
    }

    /**
     * Gets the value used by {@link #assertDeepEquals(Object, Object)} and
     * {@link #assertDeepEquals(String, Object, Object)} for the maximum delta between two doubles
     * or floats for which both numbers are still considered equal.
     */
    protected double equalFloatsOrDoublesDelta() {
        return 0.0D;
    }

    // unless overridden ulpsDelta is not used
    protected int ulpsDelta() {
        return 0;
    }

    @SuppressWarnings("serial")
    public static class MultiCauseAssertionError extends AssertionError {

        private Throwable[] causes;

        public MultiCauseAssertionError(String message, Throwable... causes) {
            super(message);
            this.causes = causes;
        }

        @Override
        public void printStackTrace(PrintStream out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++));
                    cause.printStackTrace(out);
                }
            }
        }

        @Override
        public void printStackTrace(PrintWriter out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++) + ": ");
                    cause.printStackTrace(out);
                }
            }
        }
    }

    /*
     * Overrides to the normal JUnit {@link Assert} routines that provide varargs style formatting
     * and produce an exception stack trace with the assertion frames trimmed out.
     */

    /**
     * Fails a test with the given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     *            okay)
     * @see AssertionError
     */
    public static void fail(String message, Object... objects) {
        AssertionError e;
        if (message == null) {
            e = new AssertionError();
        } else {
            e = new AssertionError(String.format(message, objects));
        }
        // Trim the assert frames from the stack trace
        StackTraceElement[] trace = e.getStackTrace();
        int start = 1; // Skip this frame
        String thisClassName = GraalTest.class.getName();
        while (start < trace.length && trace[start].getClassName().equals(thisClassName) && (trace[start].getMethodName().equals("assertTrue") || trace[start].getMethodName().equals("assertFalse"))) {
            start++;
        }
        e.setStackTrace(Arrays.copyOfRange(trace, start, trace.length));
        throw e;
    }

    /**
     * Asserts that a condition is true. If it isn't it throws an {@link AssertionError} with the
     * given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     *            okay)
     * @param condition condition to be checked
     */
    public static void assertTrue(String message, boolean condition) {
        assertTrue(condition, message);
    }

    /**
     * Asserts that a condition is true. If it isn't it throws an {@link AssertionError} without a
     * message.
     *
     * @param condition condition to be checked
     */
    public static void assertTrue(boolean condition) {
        assertTrue(condition, null);
    }

    /**
     * Asserts that a condition is false. If it isn't it throws an {@link AssertionError} with the
     * given message.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     *            okay)
     * @param condition condition to be checked
     */
    public static void assertFalse(String message, boolean condition) {
        assertTrue(!condition, message);
    }

    /**
     * Asserts that a condition is false. If it isn't it throws an {@link AssertionError} without a
     * message.
     *
     * @param condition condition to be checked
     */
    public static void assertFalse(boolean condition) {
        assertTrue(!condition, null);
    }

    /**
     * Asserts that a condition is true. If it isn't it throws an {@link AssertionError} with the
     * given message.
     *
     * @param condition condition to be checked
     * @param message the identifying message for the {@link AssertionError}
     * @param objects arguments to the format string
     */
    public static void assertTrue(boolean condition, String message, Object... objects) {
        if (!condition) {
            fail(message, objects);
        }
    }

    /**
     * Asserts that a condition is false. If it isn't it throws an {@link AssertionError} with the
     * given message produced by {@link String#format}.
     *
     * @param condition condition to be checked
     * @param message the identifying message for the {@link AssertionError}
     * @param objects arguments to the format string
     */
    public static void assertFalse(boolean condition, String message, Object... objects) {
        assertTrue(!condition, message, objects);
    }

    /**
     * Gets the {@link DebugHandlersFactory}s available for a {@link DebugContext}.
     */
    protected Collection<DebugHandlersFactory> getDebugHandlersFactories() {
        return Collections.emptyList();
    }

    /**
     * Gets a {@link DebugContext} object corresponding to {@code options}, creating a new one if
     * none currently exists. Debug contexts created by this method will have their
     * {@link DebugDumpHandler}s closed in {@link #afterTest()}.
     */
    protected DebugContext getDebugContext(OptionValues options) {
        return getDebugContext(options, null, null);
    }

    /**
     * Gets a {@link DebugContext} object corresponding to {@code options}, creating a new one if
     * none currently exists. Debug contexts created by this method will have their
     * {@link DebugDumpHandler}s closed in {@link #afterTest()}.
     *
     * @param options currently active options
     * @param id identification of the compilation or {@code null}
     * @param method method to use for a proper description of the context or {@code null}
     * @return configured context for compilation
     */
    protected DebugContext getDebugContext(OptionValues options, String id, ResolvedJavaMethod method) {
        List<DebugContext> cached = cachedDebugs.get();
        if (cached == null) {
            cached = new ArrayList<>();
            cachedDebugs.set(cached);
        }
        for (DebugContext debug : cached) {
            if (debug.getOptions() == options) {
                return debug;
            }
        }
        final DebugContext.Description descr;
        if (method == null) {
            descr = NO_DESCRIPTION;
        } else {
            descr = new DebugContext.Description(method, id == null ? method.getName() : id);
        }
        DebugContext debug = DebugContext.create(options, descr, globalMetrics, DEFAULT_LOG_STREAM, getDebugHandlersFactories());
        cached.add(debug);
        return debug;
    }

    private static final GlobalMetrics globalMetrics = new GlobalMetrics();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread("GlobalMetricsPrinter") {
            @Override
            public void run() {
                globalMetrics.print(new OptionValues(OptionValues.newOptionMap()));
            }
        });
    }
    private final ThreadLocal<List<DebugContext>> cachedDebugs = new ThreadLocal<>();

    @After
    public void afterTest() {
        List<DebugContext> cached = cachedDebugs.get();
        if (cached != null) {
            for (DebugContext debug : cached) {
                debug.close();
                debug.closeDumpHandlers(true);
            }
        }
    }

    private static final double TIMEOUT_SCALING_FACTOR = Double.parseDouble(System.getProperty("graaltest.timeout.factor", "1.0"));

    /**
     * Creates a {@link TestRule} that applies a given timeout.
     *
     * A test harness can scale {@code length} with a factor specified by the
     * {@code graaltest.timeout.factor} system property.
     */
    public static TestRule createTimeout(long length, TimeUnit timeUnit) {
        Timeout timeout = new Timeout((long) (length * TIMEOUT_SCALING_FACTOR), timeUnit);
        try {
            return new DisableOnDebug(timeout);
        } catch (LinkageError ex) {
            return timeout;
        }
    }

    /**
     * @see #createTimeout
     */
    public static TestRule createTimeoutSeconds(int seconds) {
        return createTimeout(seconds, TimeUnit.SECONDS);
    }

    /**
     * @see #createTimeout
     */
    public static TestRule createTimeoutMillis(long milliseconds) {
        return createTimeout(milliseconds, TimeUnit.MILLISECONDS);
    }
}
