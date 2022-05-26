/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8259609 8276116
 * @summary C2: optimize long range checks in long counted loops
 * @requires vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /
 * @modules java.base/jdk.internal.util
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm -ea -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestLongRangeCheck
 *
 */

import jdk.internal.util.Preconditions;
import sun.hotspot.WhiteBox;
import java.lang.reflect.Method;
import compiler.whitebox.CompilerWhiteBoxTest;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.lang.reflect.InvocationTargetException;

public class TestLongRangeCheck {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

     private static void assertIsCompiled(Method m) {
         if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
             throw new RuntimeException("should still be compiled");
         }
    }

     private static void assertIsNotCompiled(Method m) {
         if (WHITE_BOX.isMethodCompiled(m) && WHITE_BOX.getMethodCompilationLevel(m) == CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
             throw new RuntimeException("should have been deoptimized");
         }
    }

    private static void compile(Method m) {
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        assertIsCompiled(m);
    }

    public static ClassLoader newClassLoader() {
        try {
            return new URLClassLoader(new URL[] {
                    Paths.get(System.getProperty("test.classes",".")).toUri().toURL(),
            }, null);
        } catch (MalformedURLException e){
            throw new RuntimeException("Unexpected URL conversion failure", e);
        }
    }

    private static void test(String method, long start, long stop, long length, long offset) throws Exception {
        Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class);
        m.invoke(null, start, stop, length, offset); // run once so all classes are loaded
        compile(m);

        m.invoke(null, start, stop, length, offset);
        assertIsCompiled(m);
        try {
            m.invoke(null, start-1, stop, length, offset);
            throw new RuntimeException("should have thrown");
        } catch(InvocationTargetException e) {
            if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception");
            }
        }
        assertIsNotCompiled(m);

        m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class);
        m.invoke(null, start, stop, length, offset); // run once so all classes are loaded
        compile(m);
        assertIsCompiled(m);

        m.invoke(null, start, stop, length, offset);
        assertIsCompiled(m);
        try {
            m.invoke(null, stop, stop + 100, length, offset);
            throw new RuntimeException("should have thrown");
        } catch(InvocationTargetException e) {
            if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception");
            }
        }
        assertIsNotCompiled(m);

        m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class);
        m.invoke(null, start, stop, length, offset); // run once so all classes are loaded
        compile(m);

        m.invoke(null, start, stop, length, offset);
        assertIsCompiled(m);
        try {
            m.invoke(null, start, stop+1, length, offset);
            throw new RuntimeException("should have thrown");
        } catch(InvocationTargetException e) {
            if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception");
            }
        }
        assertIsNotCompiled(m);
    }

    private static void testOverflow(String method, long start, long stop, long length, long offset0, long offset1) throws Exception {
        Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class);
        m.invoke(null, start, stop, length, offset0);
        compile(m);

        m.invoke(null, start, stop, length, offset0);
        assertIsCompiled(m);
        try {
            m.invoke(null, start, stop, length, offset1);
            throw new RuntimeException("should have thrown");
        } catch(InvocationTargetException e) {
            if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception");
            }
        }
        assertIsNotCompiled(m);
    }

    private static void testConditional(String method, long start, long stop, long length, long offset0, long offset1, long start1, long stop1) throws Exception {
        Method m;

        if (start1 != start) {
            m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class, long.class, long.class);
            m.invoke(null, start, stop, length, offset0, start, stop);
            compile(m);

            m.invoke(null, start, stop, length, offset0, start, stop);
            assertIsCompiled(m);
            try {
                m.invoke(null, start, stop, length, offset1, start1-1, stop1);
                throw new RuntimeException("should have thrown");
            } catch(InvocationTargetException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                    throw new RuntimeException("unexpected exception");
                }
            }
            assertIsNotCompiled(m);
        }

        if (stop1 != stop) {
            m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class, long.class, long.class);
            m.invoke(null, start, stop, length, offset0, start, stop);
            compile(m);

            m.invoke(null, start, stop, length, offset0, start, stop);
            assertIsCompiled(m);
            try {
                m.invoke(null, start, stop, length, offset1, start1, stop1+1);
                throw new RuntimeException("should have thrown");
            } catch(InvocationTargetException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                    throw new RuntimeException("unexpected exception");
                }
            }
            assertIsNotCompiled(m);
        }

        m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod(method, long.class, long.class, long.class, long.class, long.class, long.class);
        m.invoke(null, start, stop, length, offset0, start, stop);
        compile(m);

        m.invoke(null, start, stop, length, offset0, start, stop);
        assertIsCompiled(m);

        m.invoke(null, start, stop, length, offset1, start1, stop1);
        assertIsCompiled(m);
    }

    public static void main(String[] args) throws Exception {

        test("testStridePosScalePos", 0, 100, 100, 0);

        test("testStrideNegScaleNeg", 0, 100, 100, 100);

        test("testStrideNegScalePos", 0, 100, 100, 0);

        test("testStridePosScaleNeg", 0, 100, 100, 99);

        test("testStridePosScalePosNotOne", 0, 100, 1090, 0);

        test("testStrideNegScaleNegNotOne", 0, 100, 1090, 1100);

        test("testStrideNegScalePosNotOne", 0, 100, 1090, 0);

        test("testStridePosScaleNegNotOne", 0, 100, 1090, 1089);

        long v = ((long)Integer.MAX_VALUE / 10000) * 250000;

        test("testStridePosNotOneScalePos", -v, v, v * 2, v);

        test("testStrideNegNotOneScaleNeg", -v, v, v * 2, v);

        test("testStrideNegNotOneScalePos", -v, v, v * 2, v);

        test("testStridePosNotOneScaleNeg", -v, v, v * 2, v-1);

        // offset causes overflow
        testOverflow("testStridePosScalePos", 0, 100, 100, 0, Long.MAX_VALUE - 50);
        testOverflow("testStrideNegScaleNeg", 0, 100, 100, 100, Long.MIN_VALUE + 50);
        testOverflow("testStrideNegScalePos", 0, 100, 100, 0, Long.MAX_VALUE - 50);
        testOverflow("testStridePosScaleNeg", 0, 100, 100, 99, Long.MIN_VALUE + 50);

        // no spurious deopt if the range check doesn't fail because not executed
        testConditional("testStridePosScalePosConditional", 0, 100, 100, 0, -50, 50, 100);
        testConditional("testStridePosScalePosConditional", 0, 100, Long.MAX_VALUE, 0, Long.MAX_VALUE - 50, 0, 50);
        testConditional("testStrideNegScaleNegConditional", 0, 100, 100, 100, 50, 0, 51);
        testConditional("testStrideNegScaleNegConditional", 0, 100, Long.MAX_VALUE, 100, Long.MIN_VALUE + 50, 52, 100);
        testConditional("testStrideNegScalePosConditional", 0, 100, 100, 0, -50, 50, 100);
        testConditional("testStrideNegScalePosConditional", 0, 100, Long.MAX_VALUE, 100, Long.MAX_VALUE - 50, 0, 50);
        testConditional("testStridePosScaleNegConditional", 0, 100, 100, 99, 50, 0, 51);
        testConditional("testStridePosScaleNegConditional", 0, 100, Long.MAX_VALUE, 99, Long.MIN_VALUE + 50, 52, 100);

        test("testStridePosScalePosInIntLoop", 0, 100, 100, 0);

        test("testStrideNegScaleNegInIntLoop", 0, 100, 100, 100);

        test("testStrideNegScalePosInIntLoop", 0, 100, 100, 0);

        test("testStridePosScaleNegInIntLoop", 0, 100, 100, 99);

        test("testStridePosScalePosNotOneInIntLoop", 0, 100, 1090, 0);

        test("testStrideNegScaleNegNotOneInIntLoop", 0, 100, 1090, 1100);

        test("testStrideNegScalePosNotOneInIntLoop", 0, 100, 1090, 0);

        test("testStridePosScaleNegNotOneInIntLoop", 0, 100, 1090, 1089);

        v = ((long)Integer.MAX_VALUE / 10000) * 9999;

        test("testStridePosNotOneScalePosInIntLoop", -v, v, v * 4, 2 * v);

        test("testStrideNegNotOneScaleNegInIntLoop", -v, v, v * 4, 2 * v);

        test("testStrideNegNotOneScalePosInIntLoop", -v, v, v * 4, 2 * v);

        test("testStridePosNotOneScaleNegInIntLoop", -v, v, v * 4, 2 * v - 1);

        // offset causes overflow
        testOverflow("testStridePosScalePosInIntLoop", 0, 100, 100, 0, Long.MAX_VALUE - 50);
        testOverflow("testStrideNegScaleNegInIntLoop", 0, 100, 100, 100, Long.MIN_VALUE + 50);
        testOverflow("testStrideNegScalePosInIntLoop", 0, 100, 100, 0, Long.MAX_VALUE - 50);
        testOverflow("testStridePosScaleNegInIntLoop", 0, 100, 100, 99, Long.MIN_VALUE + 50);
        // no spurious deopt if the range check doesn't fail because not executed
        testConditional("testStridePosScalePosConditionalInIntLoop", 0, 100, 100, 0, -50, 50, 100);
        testConditional("testStridePosScalePosConditionalInIntLoop", 0, 100, Long.MAX_VALUE, 0, Long.MAX_VALUE - 50, 0, 50);
        testConditional("testStrideNegScaleNegConditionalInIntLoop", 0, 100, 100, 100, 50, 0, 51);
        testConditional("testStrideNegScaleNegConditionalInIntLoop", 0, 100, Long.MAX_VALUE, 100, Long.MIN_VALUE + 50, 52, 100);
        testConditional("testStrideNegScalePosConditionalInIntLoop", 0, 100, 100, 0, -50, 50, 100);
        testConditional("testStrideNegScalePosConditionalInIntLoop", 0, 100, Long.MAX_VALUE, 100, Long.MAX_VALUE - 50, 0, 50);
        testConditional("testStridePosScaleNegConditionalInIntLoop", 0, 100, 100, 99, 50, 0, 51);
        testConditional("testStridePosScaleNegConditionalInIntLoop", 0, 100, Long.MAX_VALUE, 99, Long.MIN_VALUE + 50, 52, 100);

        test("testStridePosScalePosNotOneInIntLoop2", 0, 100, 1090, 0);

        test("testStrideNegScaleNegNotOneInIntLoop2", 0, 100, 1090, 1100);

        test("testStrideNegScalePosNotOneInIntLoop2", 0, 100, 1090, 0);

        test("testStridePosScaleNegNotOneInIntLoop2", 0, 100, 1090, 1089);

        {
            Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod("testStridePosScalePosInIntLoopOverflow", long.class, long.class, long.class, long.class);
            long stride = 1 << 14;
            long scale = 1 << 15;
            long offset = stride * scale * 4;
            long length = offset + stride * scale * 3 + 1;
            long stop = stride * 5;

            m.invoke(null, 0, stop, length, offset);
            compile(m);

            m.invoke(null, 0, stop, length, offset);
            // deoptimizes even though no range check fails
        }
        {
            Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod("testStridePosScalePosInIntLoopOverflow", long.class, long.class, long.class, long.class);
            long stride = 1 << 14;
            long scale = 1 << 15;
            long offset = stride * scale * 4;
            long length = offset + stride * scale * 3 + 1;
            long stop = stride * 5;

            m.invoke(null, 0, stop, length, offset);
            compile(m);

            offset = 0;
            stop = stride * 5;

            try {
                m.invoke(null, 0, stop, length, offset);
                throw new RuntimeException("should have thrown");
            } catch(InvocationTargetException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                    throw new RuntimeException("unexpected exception");
                }
            }
            assertIsNotCompiled(m);
        }
    }

    public static void testStridePosScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = 1;
        for (long i = stop; i > start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = 1;
        for (long i = stop-1; i >= start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScalePosNotOne(long start, long stop, long length, long offset) {
        final long scale = 11;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScaleNegNotOne(long start, long stop, long length, long offset) {
        final long scale = -11;
        final long stride = 1;
        for (long i = stop; i > start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScalePosNotOne(long start, long stop, long length, long offset) {
        final long scale = 11;
        final long stride = 1;
        for (long i = stop-1; i >= start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScaleNegNotOne(long start, long stop, long length, long offset) {
        final long scale = -11;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosNotOneScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = Integer.MAX_VALUE / 10000;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegNotOneScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = Integer.MAX_VALUE / 10000;
        for (long i = stop; i > start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegNotOneScalePos(long start, long stop, long length, long offset) {
        final long scale = 1;
        final long stride = Integer.MAX_VALUE / 10000;
        for (long i = stop-1; i >= start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosNotOneScaleNeg(long start, long stop, long length, long offset) {
        final long scale = -1;
        final long stride = Integer.MAX_VALUE / 10000;
        for (long i = start; i < stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScalePosConditional(long start, long stop, long length, long offset, long start2, long stop2) {
        Preconditions.checkIndex(0, length, null);
        final long scale = 1;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            if (i >= start2 && i < stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStrideNegScaleNegConditional(long start, long stop, long length, long offset, long start2, long stop2) {
        final long scale = -1;
        final long stride = 1;
        for (long i = stop; i > start; i -= stride) {
            if (i >= start2 && i < stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStrideNegScalePosConditional(long start, long stop, long length, long offset, long start2, long stop2) {
        final long scale = 1;
        final long stride = 1;
        for (long i = stop-1; i >= start; i -= stride) {
            if (i >= start2 && i < stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStridePosScaleNegConditional(long start, long stop, long length, long offset, long start2, long stop2) {
        final long scale = -1;
        final long stride = 1;
        for (long i = start; i < stop; i += stride) {
            if (i >= start2 && i < stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    private static void checkInputs(long... inputs) {
        for (int i = 0; i < inputs.length; i++) {
            if ((long)((int)inputs[i]) != inputs[i]) {
                throw new RuntimeException("bad arguments");
            }
        }
    }

    public static void testStridePosScalePosInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 1;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScaleNegInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -1;
        final int stride = 1;
        for (int i = (int)stop; i > (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScalePosInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 1;
        final int stride = 1;
        for (int i = (int)(stop-1); i >= (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScaleNegInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -1;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScalePosNotOneInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 11;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScaleNegNotOneInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -11;
        final int stride = 1;
        for (int i = (int)stop; i > (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScalePosNotOneInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 11;
        final int stride = 1;
        for (int i = (int)(stop-1); i >= (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScaleNegNotOneInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -11;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosNotOneScalePosInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 2;
        final int stride = Integer.MAX_VALUE / 10000;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegNotOneScaleNegInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -2;
        final int stride = Integer.MAX_VALUE / 10000;
        for (int i = (int)stop; i > (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegNotOneScalePosInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = 2;
        final int stride = Integer.MAX_VALUE / 10000;
        for (int i = (int)(stop-1); i >= (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosNotOneScaleNegInIntLoop(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final long scale = -2;
        final int stride = Integer.MAX_VALUE / 10000;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScalePosConditionalInIntLoop(long start, long stop, long length, long offset, long start2, long stop2) {
        checkInputs(start, stop, start2, stop2);
        final long scale = 1;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            if (i >= (int)start2 && i < (int)stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStrideNegScaleNegConditionalInIntLoop(long start, long stop, long length, long offset, long start2, long stop2) {
        checkInputs(start, stop, start2, stop2);
        final long scale = -1;
        final int stride = 1;
        for (int i = (int)stop; i > (int)start; i -= stride) {
            if (i >= (int)start2 && i < (int)stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStrideNegScalePosConditionalInIntLoop(long start, long stop, long length, long offset, long start2, long stop2) {
        checkInputs(start, stop, start2, stop2);
        final long scale = 1;
        final int stride = 1;
        for (int i = (int)(stop-1); i >= (int)start; i -= stride) {
            if (i >= (int)start2 && i < (int)stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStridePosScaleNegConditionalInIntLoop(long start, long stop, long length, long offset, long start2, long stop2) {
        checkInputs(start, stop, start2, stop2);
        final long scale = -1;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            if (i >= (int)start2 && i < (int)stop2) {
                Preconditions.checkIndex(scale * i + offset, length, null);
            }
        }
    }

    public static void testStridePosScalePosNotOneInIntLoop2(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final int scale = 11;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScaleNegNotOneInIntLoop2(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final int scale = -11;
        final int stride = 1;
        for (int i = (int)stop; i > (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStrideNegScalePosNotOneInIntLoop2(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final int scale = 11;
        final int stride = 1;
        for (int i = (int)(stop-1); i >= (int)start; i -= stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScaleNegNotOneInIntLoop2(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final int scale = -11;
        final int stride = 1;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }

    public static void testStridePosScalePosInIntLoopOverflow(long start, long stop, long length, long offset) {
        checkInputs(start, stop);
        final int scale = 1 << 15;
        final int stride = 1 << 14;
        for (int i = (int)start; i < (int)stop; i += stride) {
            Preconditions.checkIndex(scale * i + offset, length, null);
        }
    }
}
