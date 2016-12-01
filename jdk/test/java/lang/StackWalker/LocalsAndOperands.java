/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8020968 8147039
 * @summary Tests for locals and operands
 * @modules java.base/java.lang:open
 * @run testng LocalsAndOperands
 */

import org.testng.annotations.*;
import java.lang.StackWalker.StackFrame;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

public class LocalsAndOperands {
    static final boolean debug = true;

    static Class<?> liveStackFrameClass;
    static Class<?> primitiveValueClass;
    static StackWalker extendedWalker;
    static Method getLocals;
    static Method getOperands;
    static Method getMonitors;
    static Method primitiveType;

    static {
        try {
            liveStackFrameClass = Class.forName("java.lang.LiveStackFrame");
            primitiveValueClass = Class.forName("java.lang.LiveStackFrame$PrimitiveValue");

            getLocals = liveStackFrameClass.getDeclaredMethod("getLocals");
            getLocals.setAccessible(true);

            getOperands = liveStackFrameClass.getDeclaredMethod("getStack");
            getOperands.setAccessible(true);

            getMonitors = liveStackFrameClass.getDeclaredMethod("getMonitors");
            getMonitors.setAccessible(true);

            primitiveType = primitiveValueClass.getDeclaredMethod("type");
            primitiveType.setAccessible(true);

            Method method = liveStackFrameClass.getMethod("getStackWalker");
            method.setAccessible(true);
            extendedWalker = (StackWalker) method.invoke(null);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Helper method to return a StackFrame's locals */
    static Object[] invokeGetLocals(StackFrame arg) {
        try {
            return (Object[]) getLocals.invoke(arg);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /*****************
     * DataProviders *
     *****************/

    /** Calls testLocals() and provides LiveStackFrames for testLocals* methods */
    @DataProvider
    public static StackFrame[][] provider() {
        return new StackFrame[][] {
            new Tester().testLocals()
        };
    }

    /**
     * Calls testLocalsKeepAlive() and provides LiveStackFrames for testLocals* methods.
     * Local variables in testLocalsKeepAlive() are ensured to not become dead.
     */
    @DataProvider
    public static StackFrame[][] keepAliveProvider() {
        return new StackFrame[][] {
            new Tester().testLocalsKeepAlive()
        };
    }

    /**
     * Provides StackFrames from a StackWalker without the LOCALS_AND_OPERANDS
     * option.
     */
    @DataProvider
    public static StackFrame[][] noLocalsProvider() {
        // Use default StackWalker
        return new StackFrame[][] {
            new Tester(StackWalker.getInstance(), true).testLocals()
        };
    }

    /**
     * Calls testLocals() and provides LiveStackFrames for *all* called methods,
     * including test infrastructure (jtreg, testng, etc)
     *
     */
    @DataProvider
    public static StackFrame[][] unfilteredProvider() {
        return new StackFrame[][] {
            new Tester(extendedWalker, false).testLocals()
        };
    }

    /****************
     * Test methods *
     ****************/

    /**
     * Check for expected local values and types in the LiveStackFrame
     */
    @Test(dataProvider = "keepAliveProvider")
    public static void checkLocalValues(StackFrame... frames) {
        if (debug) {
            System.out.println("Running checkLocalValues");
            dumpStackWithLocals(frames);
        }
        Arrays.stream(frames).filter(f -> f.getMethodName()
                                           .equals("testLocalsKeepAlive"))
                                           .forEach(
            f -> {
                Object[] locals = invokeGetLocals(f);
                for (int i = 0; i < locals.length; i++) {
                    // Value
                    String expected = Tester.LOCAL_VALUES[i];
                    Object observed = locals[i];
                    if (expected != null /* skip nulls in golden values */ &&
                            !expected.equals(observed.toString())) {
                        System.err.println("Local value mismatch:");
                        if (!debug) { dumpStackWithLocals(frames); }
                        throw new RuntimeException("local " + i + " value is " +
                                observed + ", expected " + expected);
                    }

                    // Type
                    expected = Tester.LOCAL_TYPES[i];
                    observed = type(locals[i]);
                    if (expected != null /* skip nulls in golden values */ &&
                            !expected.equals(observed)) {
                        System.err.println("Local type mismatch:");
                        if (!debug) { dumpStackWithLocals(frames); }
                        throw new RuntimeException("local " + i + " type is " +
                                observed + ", expected " + expected);
                    }
                }
            }
        );
    }

    /**
     * Basic sanity check for locals and operands
     */
    @Test(dataProvider = "provider")
    public static void sanityCheck(StackFrame... frames) {
        if (debug) {
            System.out.println("Running sanityCheck");
        }
        try {
            Stream<StackFrame> stream = Arrays.stream(frames);
            if (debug) {
                stream.forEach(LocalsAndOperands::printLocals);
            } else {
                System.out.println(stream.count() + " frames");
            }
        } catch (Throwable t) {
            dumpStackWithLocals(frames);
            throw t;
        }
    }

    /**
     * Sanity check for locals and operands, including testng/jtreg frames
     */
    @Test(dataProvider = "unfilteredProvider")
    public static void unfilteredSanityCheck(StackFrame... frames) {
        if (debug) {
            System.out.println("Running unfilteredSanityCheck");
        }
        try {
            Stream<StackFrame> stream = Arrays.stream(frames);
            if (debug) {
                stream.forEach(f -> { System.out.println(f + ": " +
                        invokeGetLocals(f).length + " locals"); } );
            } else {
                System.out.println(stream.count() + " frames");
            }
        } catch (Throwable t) {
            dumpStackWithLocals(frames);
            throw t;
        }
    }

    /**
     * Test that LiveStackFrames are not provided with the default StackWalker
     * options.
     */
    @Test(dataProvider = "noLocalsProvider")
    public static void withoutLocalsAndOperands(StackFrame... frames) {
        for (StackFrame frame : frames) {
            if (liveStackFrameClass.isInstance(frame)) {
                throw new RuntimeException("should not be LiveStackFrame");
            }
        }
    }

    static class Tester {
        private StackWalker walker;
        private boolean filter = true; // Filter out testng/jtreg/etc frames?

        Tester() {
            this.walker = extendedWalker;
        }

        Tester(StackWalker walker, boolean filter) {
            this.walker = walker;
            this.filter = filter;
        }

        /**
         * Perform stackwalk without keeping local variables alive and return an
         * array of the collected StackFrames
         */
        private synchronized StackFrame[] testLocals() {
            // Unused local variables will become dead
            int x = 10;
            char c = 'z';
            String hi = "himom";
            long l = 1000000L;
            double d =  3.1415926;

            if (filter) {
                return walker.walk(s -> s.filter(f -> TEST_METHODS.contains(f
                        .getMethodName())).collect(Collectors.toList()))
                        .toArray(new StackFrame[0]);
            } else {
                return walker.walk(s -> s.collect(Collectors.toList()))
                        .toArray(new StackFrame[0]);
            }
        }

        /**
         * Perform stackwalk, keeping local variables alive, and return a list of
         * the collected StackFrames
         */
        private synchronized StackFrame[] testLocalsKeepAlive() {
            int x = 10;
            char c = 'z';
            String hi = "himom";
            long l = 1000000L;
            double d =  3.1415926;

            List<StackWalker.StackFrame> frames;
            if (filter) {
                frames = walker.walk(s -> s.filter(f -> TEST_METHODS.contains(f
                        .getMethodName())).collect(Collectors.toList()));
            } else {
                frames = walker.walk(s -> s.collect(Collectors.toList()));
            }

            // Use local variables so they stay alive
            System.out.println("Stayin' alive: "+x+" "+c+" "+hi+" "+l+" "+d);
            return frames.toArray(new StackFrame[0]); // FIXME: convert to Array here
        }

        // Expected values for locals in testLocals() & testLocalsKeepAlive()
        // TODO: use real values instead of Strings, rebuild doubles & floats, etc
        private final static String[] LOCAL_VALUES = new String[] {
            null, // skip, LocalsAndOperands$Tester@XXX identity is different each run
            "10",
            "122",
            "himom",
            "0",
            null, // skip, fix in 8156073
            null, // skip, fix in 8156073
            null, // skip, fix in 8156073
            "0"
        };

        // Expected types for locals in testLocals() & testLocalsKeepAlive()
        // TODO: use real types
        private final static String[] LOCAL_TYPES = new String[] {
            null, // skip
            "I",
            "I",
            "java.lang.String",
            "I",
            "I",
            "I",
            "I",
            "I"
        };

        final static Map NUM_LOCALS = Map.of("testLocals", 8,
                                             "testLocalsKeepAlive",
                                             LOCAL_VALUES.length);
        private final static Collection<String> TEST_METHODS = NUM_LOCALS.keySet();
    }

    /**
     * Print stack trace with locals
     */
    public static void dumpStackWithLocals(StackFrame...frames) {
        Arrays.stream(frames).forEach(LocalsAndOperands::printLocals);
    }

    /**
     * Print the StackFrame and an indexed list of its locals
     */
    public static void printLocals(StackWalker.StackFrame frame) {
        try {
            System.out.println(frame);
            Object[] locals = (Object[]) getLocals.invoke(frame);
            for (int i = 0; i < locals.length; i++) {
                System.out.format("  local %d: %s type %s\n", i, locals[i], type(locals[i]));
            }

            Object[] operands = (Object[]) getOperands.invoke(frame);
            for (int i = 0; i < operands.length; i++) {
                System.out.format("  operand %d: %s type %s%n", i, operands[i],
                                  type(operands[i]));
            }

            Object[] monitors = (Object[]) getMonitors.invoke(frame);
            for (int i = 0; i < monitors.length; i++) {
                System.out.format("  monitor %d: %s%n", i, monitors[i]);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String type(Object o) {
        try {
            if (o == null) {
                return "null";
            } else if (primitiveValueClass.isInstance(o)) {
                char c = (char)primitiveType.invoke(o);
                return String.valueOf(c);
            } else {
                return o.getClass().getName();
            }
        } catch(Exception e) { throw new RuntimeException(e); }
    }
}
