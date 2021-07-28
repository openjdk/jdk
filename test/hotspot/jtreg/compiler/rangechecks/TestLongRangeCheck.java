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
 * @bug 8259609
 * @summary C2: optimize long range checks in long counted loops
 * @requires vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /
 * @modules java.base/jdk.internal.util
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm -ea -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation TestLongRangeCheck
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

        {
            Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod("testStridePosScalePos", long.class, long.class, long.class, long.class);
            m.invoke(null, 0, 100, 100, 0);
            compile(m);

            m.invoke(null, 0, 100, 100, 0);
            assertIsCompiled(m);
            try {
                m.invoke(null, 0, 100, 100, Long.MAX_VALUE - 50);
                throw new RuntimeException("should have thrown");
            } catch(InvocationTargetException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException)) {
                    throw new RuntimeException("unexpected exception");
                }
            }
            assertIsNotCompiled(m);
        }

        // no spurious deopt if the range check doesn't fail because not executed
        {
            Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod("testStridePosScalePosConditional", long.class, long.class, long.class, long.class, long.class, long.class);
            m.invoke(null, 0, 100, 100, 0, 0, 100);
            compile(m);

            m.invoke(null, 0, 100, 100, -50, 50, 100);
            assertIsCompiled(m);
        }
        {
            Method m = newClassLoader().loadClass("TestLongRangeCheck").getDeclaredMethod("testStridePosScalePosConditional", long.class, long.class, long.class, long.class, long.class, long.class);
            m.invoke(null, 0, 100, 100, 0, 0, 100);
            compile(m);

            m.invoke(null, 0, 100, Long.MAX_VALUE, Long.MAX_VALUE - 50, 0, 50);
            assertIsCompiled(m);
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
}
