/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.runtime.JVMCI;

/**
 * @test id=default
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *
 * @compile PlaceholderEventWriter.java
 * @compile PlaceholderEventWriterFactory.java
 * @compile E.java
 * @compile NonEvent.java
 * @compile RegisteredTrueEvent.java
 * @compile RegisteredFalseEvent.java
 * @compile MyCommitRegisteredTrueEvent.java
 * @compile MyCommitRegisteredFalseEvent.java
 * @compile StaticCommitEvent.java
 *
 * @run main/othervm jdk.jfr.jvm.TestGetEventWriter
 *
 * @run main/othervm/timeout=300 -Xint -XX:+UseInterpreter -Dinterpreted=true
 *      jdk.jfr.jvm.TestGetEventWriter
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:-UseInterpreter -Dinterpreted=false
 *      jdk.jfr.jvm.TestGetEventWriter
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:TieredStopAtLevel=1 -XX:-UseInterpreter -Dinterpreted=false
 *      jdk.jfr.jvm.TestGetEventWriter
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:TieredStopAtLevel=4 -XX:-TieredCompilation -XX:-UseInterpreter -Dinterpreted=false
 *      jdk.jfr.jvm.TestGetEventWriter
 */

/**
 * @test id=jvmci
 * @key jfr
 * @requires vm.hasJFR
 * @requires vm.jvmci
 * @library /test/lib
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *
 * @compile PlaceholderEventWriter.java
 * @compile PlaceholderEventWriterFactory.java
 * @compile E.java
 * @compile NonEvent.java
 * @compile RegisteredTrueEvent.java
 * @compile RegisteredFalseEvent.java
 * @compile MyCommitRegisteredTrueEvent.java
 * @compile MyCommitRegisteredFalseEvent.java
 * @compile StaticCommitEvent.java
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -Dtest.jvmci=true --add-exports=jdk.jfr/jdk.jfr.internal.event=ALL-UNNAMED
 *      jdk.jfr.jvm.TestGetEventWriter
 */

public class TestGetEventWriter {

    static class InitializationEvent extends Event {
    }

    public static void main(String... args) throws Throwable {
        try (Recording r = new Recording()) {
            r.start();
            // Unlocks access to jdk.jfr.internal.event
            InitializationEvent e  = new InitializationEvent();
            e.commit();
        }
        // Make sure EventWriterFactory can be accessed.
        Class<?> clazz = Class.forName("jdk.jfr.internal.event.EventWriterFactory");
        if (clazz == null) {
            throw new Exception("Test error, not able to access jdk.jfr.internal.event.EventWriterFactory class");
        }
        testRegisteredTrueEvent();
        testRegisteredFalseEvent();
        testMyCommitRegisteredTrue();
        testMyCommitRegisteredFalse();
        testStaticCommit();
        testMethodHandleEvent();
        testReflectionEvent();
        testNonEvent();
    }

    // The class does not inherit jdk.jfr.Event and, as such, does not implement the
    // API. It has its own stand-alone "commit()V", which is not an override, that
    // attempts to resolve and link against EventWriterFactory. This user implementation
    // is not blessed for linkage.
    private static void testNonEvent() throws Throwable {
        Runnable e = newEventObject("NonEvent");
        try {
            e.run(); // invokes commit()
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "commit");
            return;
        }
    }

    // The user has defined a class which overrides and implements the "commit()V"
    // method declared final in jdk.jfr.Event.
    // This user implementation is not blessed for linkage.
    private static void testRegisteredTrueEvent() throws Throwable {
        Event e = newEventObject("RegisteredTrueEvent");
        try {
            e.commit(); // throws
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "commit");
            return;
        }
    }

    // The user has defined a class which overrides and implements the "commit()V"
    // method declared final in jdk.jfr.Event. This user implementation is not
    // blessed for linkage. If a class have user-defined implementations
    // of any methods declared final, it is not instrumented.
    // Although it is a subclass of jdk.jfr.Event, on initial load, we will
    // classify it as being outside of the JFR system. Attempting to register
    // such a class throws an IllegalArgumentException. The user-defined
    // "commit()V" method is still not blessed for linkage, even after registration.
    private static void testRegisteredFalseEvent() throws Throwable {
        Event e = newEventObject("RegisteredFalseEvent");
        try {
            e.commit(); // throws
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "commit");
        }
        try {
            FlightRecorder.register(e.getClass());
        } catch (IllegalArgumentException iae) {
            // OK, as expected.
            // Can't register an event class where the user has managed to override
            // methods in jdk.jfr.Event
        }
    }

    // The user has implemented another method, "myCommit()V", not an override nor
    // overload. that attempts to resolve and link EventWriterFactory. This will fail,
    // because "myCommit()V" is not blessed for linkage.
    private static void testMyCommitRegisteredTrue() throws Throwable {
        Runnable e = newEventObject("MyCommitRegisteredTrueEvent");
        try {
            e.run(); // Invoking the user-defined method throws.
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "myCommit");
            return;
        }
    }

    // The user has implemented another method, "myCommit()V", not an override,
    // nor overload. This linkage will fail because "myCommit()V" is not blessed.
    // Since the user has not defined any final methods in jdk.jfr.Event,
    // the class is not excluded wholesale from the JFR system.
    // Invoking the real "commit()V", installed by the framework, is OK.
    private static void testMyCommitRegisteredFalse() throws Throwable {
        Runnable e = newEventObject("MyCommitRegisteredFalseEvent");
        try {
            e.run(); // Invoking the user-defined method throws.
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "myCommit");
        }
        // Instrumentation added.
        FlightRecorder.register(e.getClass().asSubclass(Event.class));
        Event event = (Event) e;
        event.commit(); // Invoking the JFR provided method is OK
    }

    // Events located in the boot class loader can create a static
    // commit-method to emit events. It must not be used by code
    // outside of the boot class loader.
    private static void testStaticCommit() throws Throwable {
        Runnable e = newEventObject("StaticCommitEvent");
        try {
            e.run(); // Invokes commit(long, long, String, int)
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessError iae) {
            // OK, as expected
            maybeCheckJVMCI(e.getClass(), "commit");
        }
    }

    static class MethodHandleEvent extends Event {
        public void myCommit() throws Throwable {
            try {
                Class<?> ew = Class.forName("jdk.jfr.internal.event.EventWriter");
                MethodType t = MethodType.methodType(ew, List.of(long.class));
                Class<?> factory = Class.forName("jdk.jfr.internal.event.EventWriterFactory");
                MethodHandle mh = MethodHandles.lookup().findStatic(factory, "getEventWriter", t);
                mh.invoke(Long.valueOf(4711)); // throws IllegalAccessException
            } catch (ClassNotFoundException | SecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // The user has implemented another method, "myCommit()V", not an override,
    // nor overload. This linkage will fail, because "myCommit()V" is not blessed.
    // Using a MethodHandle for linkage is transparent and immaterial.
    private static void testMethodHandleEvent() throws Throwable {
        MethodHandleEvent e = new MethodHandleEvent();
        try {
            e.myCommit();
            throw new RuntimeException("Should not reach here");
        } catch (IllegalAccessException iaex) {
            if (iaex.getCause() instanceof IllegalAccessError iae) {
                if (iae.getMessage().contains("getEventWriter(long)")) {
                    // OK, as expected
                    return;
                }
            }
        }
    }

    static class ReflectionEvent extends Event {
        public void myCommit() throws Throwable {
            Class<?> c;
            try {
                c = Class.forName("jdk.jfr.internal.event.EventWriterFactory");
                Method m = c.getMethod("getEventWriter", new Class[] {long.class});
                m.invoke(null, Long.valueOf(4711)); // throws InternalError
            } catch (ClassNotFoundException | SecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // The user has implemented another method, "myCommit()V", not an override,
    // nor overload, that uses Reflection. This linkage will fail, because
    // "myCommit()V" is not blessed. Reflection is using method handles,
    // but using a MethodHandle for linkage is transparent and immaterial.
    private static void testReflectionEvent() throws Throwable {
        ReflectionEvent e = new ReflectionEvent();
        try {
            e.myCommit(); // throws
            throw new RuntimeException("Should not reach here");
        } catch (InternalError ie) {
            if (ie.getCause() instanceof IllegalAccessException iaex) {
                if (iaex.getCause() instanceof IllegalAccessError iae) {
                    if (iae.getMessage().contains("getEventWriter(long)")) {
                        // OK, as expected
                        return;
                    }
                }
            }
        }
    }

    private static class BytesClassLoader extends ClassLoader {
        private final byte[] bytes;
        private final String className;

        BytesClassLoader(byte[] bytes, String name) {
            this.bytes = bytes;
            this.className = name;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                return super.loadClass(name);
            }
        }
    }

    private static byte[] replace(byte[] bytes, String match, String replacement) {
        if (match.length() != replacement.length()) {
            throw new IllegalArgumentException("Match must be same size as replacement");
        }
        for (int i = 0; i < bytes.length - match.length(); i++) {
            if (match(bytes, i, match)) {
                for (int j = 0; j < replacement.length(); j++) {
                    bytes[i + j] = (byte) replacement.charAt(j);
                }
            }
        }
        return bytes;
    }

    private static boolean match(byte[] bytes, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (bytes[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> T newEventObject(String name) throws Throwable {
        String r = name + ".class";
        String fullName = "jdk.jfr.jvm." + name;
        var is = TestGetEventWriter.class.getResourceAsStream(r);
        if (is == null) {
            throw new Exception("Test error, could not located class file for " + name);
        }
        byte[] bytes = is.readAllBytes();
        is.close();
        bytes = replace(bytes, "jdk/jfr/jvm/E", "jdk/jfr/Event");
        bytes = replace(bytes, "jdk/jfr/jvm/PlaceholderEventWriterFactory", "jdk/jfr/internal/event/EventWriterFactory");
        bytes = replace(bytes, "jdk/jfr/jvm/PlaceholderEventWriter", "jdk/jfr/internal/event/EventWriter");
        BytesClassLoader bc = new BytesClassLoader(bytes, fullName);
        Class<?> clazz = bc.loadClass(fullName);
        Constructor<?> constructor = clazz.getConstructor(new Class[0]);
        System.out.println("About to invoke " + fullName + ".commit()");
        return (T) constructor.newInstance();
    }

    private static ResolvedJavaMethod findCommitMethod(MetaAccessProvider metaAccess, Class<?> eventClass, String commitName) {
        for (Method m : eventClass.getMethods()) {
            if (m.getName().equals(commitName)) {
                return metaAccess.lookupJavaMethod(m);
            }
        }
        throw new AssertionError("could not find " + commitName + " method in " + eventClass);
    }

    // Factor out test.jvmci system property check to reduce unecessary work in -Xcomp.
    private static void maybeCheckJVMCI(Class<?> eventClass, String commitName) throws Throwable {
        if (!Boolean.getBoolean("test.jvmci")) {
            return;
        }
        checkJVMCI(eventClass, commitName);
    }

    /**
     * Checks that JVMCI prevents unblessed access to {@code EventWriterFactory.getEventWriter(long)}.
     */
    private static void checkJVMCI(Class<?> eventClass, String commitName) throws Throwable {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaMethod commit = findCommitMethod(metaAccess, eventClass, commitName);
        ConstantPool cp = commit.getConstantPool();

        // Search for first INVOKESTATIC instruction in commit method which is expected
        // to be the call to jdk.jfr.internal.event.EventWriterFactory.getEventWriter(long).
        final int INVOKESTATIC = 184;
        byte[] code = commit.getCode();
        for (int bci = 0; bci < code.length; bci++) {
            int b = code[bci] & 0xff;
            if (b == INVOKESTATIC) {
                int cpi = ((code[bci + 1] & 0xff) << 8) | (code[bci + 2] & 0xff);
                try {
                    cp.lookupMethod(cpi, 184, commit);
                    throw new AssertionError("Expected IllegalAccessError");
                } catch (IllegalAccessError e) {
                }
                try {
                    // Test looking up with null caller
                    cp.lookupMethod(cpi, 184, null);
                    throw new AssertionError("Expected IllegalAccessError");
                } catch (IllegalAccessError e) {
                }

                // Ignore all subsequent instructions
                return;
            }
        }
        throw new AssertionError(eventClass + ": did not find INVOKESTATIC in " + commit.format("%H.%n(%p)"));
    }
}
