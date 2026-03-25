/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=int
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:-TieredCompilation
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention Interpreted
 */

/*
 * @test id=c1
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @requires vm.flagless
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                   compiler.valhalla.inlinetypes.TestOopsInReturnConvention C1
 */

/*
 * @test id=c2
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention C2
 */

/*
 * @test id=int-stress-cc
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:-TieredCompilation
 *                               -XX:+IgnoreUnrecognizedVMOptions -XX:+StressCallingConvention
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                                compiler.valhalla.inlinetypes.TestOopsInReturnConvention Interpreted
 */

/*
 * @test id=c1-stress-cc
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @requires vm.flagless
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:+StressCallingConvention
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention C1
 */

/*
 * @test id=c2-stress-cc
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:+StressCallingConvention
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention C2
 */

/*
 * @test id=int-no-preload
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:-TieredCompilation
 *                               -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention Interpreted
 */

/*
 * @test id=c1-no-preload
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @requires vm.flagless
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch
 *                               -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention C1
 */

/*
 * @test id=c2-no-preload
 * @summary Test that oop fields in scalarized returns are properly handled.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=300 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:-TieredCompilation
 *                               -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention::callee
 *                               -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestOopsInReturnConvention*::verify
 *                               compiler.valhalla.inlinetypes.TestOopsInReturnConvention C2
 */


package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class TestOopsInReturnConvention {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_FULL_OPTIMIZATION = 4; // C2 or JVMCI

    // Large value class with oops
    static value class LargeValueWithOops {
        Object x1;
        Object x2;
        Object x3;
        Object x4;
        Object x5;

        public LargeValueWithOops(Object obj) {
            this.x1 = obj;
            this.x2 = obj;
            this.x3 = obj;
            this.x4 = obj;
            this.x5 = obj;
        }

        public static void verify(LargeValueWithOops val, Object obj, boolean useNull) {
            if (useNull) {
                Asserts.assertEQ(val, null);
            } else {
                Asserts.assertEQ(val.x1, obj);
                Asserts.assertEQ(val.x2, obj);
                Asserts.assertEQ(val.x3, obj);
                Asserts.assertEQ(val.x4, obj);
                Asserts.assertEQ(val.x5, obj);
            }
        }
    }

    // Pass some unused args to make sure that the (return) registers are trashed
    public static LargeValueWithOops callee(int unused1, int unused2, int unused3, int unused4, int unused5, LargeValueWithOops val) {
        return val;
    }

    public static void caller(LargeValueWithOops val, Object obj, boolean useNull) {
        // Below call will return a LargeValueWithOops in scalarized form.
        // If it's null, the x1 - x5 oop fields need to be zeroed to make the GC happy.
        val = callee(1, 2, 3, 4, 5, val);
        LargeValueWithOops.verify(val, obj, useNull);
    }

    static class GarbageProducerThread extends Thread {
        public void run() {
            for (;;) {
                // Produce some garbage and then let the GC do its work
                Object[] arrays = new Object[1024];
                for (int i = 0; i < arrays.length; i++) {
                    arrays[i] = new int[1024];
                }
                System.gc();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("Interpreted") || args[0].equals("C1")) {
            // Prevent callee method from being C2 compiled to ensure it's interpreted or C1 compiled
            Method m = TestOopsInReturnConvention.class.getDeclaredMethod("callee", int.class, int.class, int.class, int.class, int.class, LargeValueWithOops.class);
            WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
        }

        // Start another thread that does some allocations and calls System.gc() to trigger frequent GCs
        Thread garbage_producer = new GarbageProducerThread();
        garbage_producer.setDaemon(true);
        garbage_producer.start();

        // Trigger compilation
        for (int i = 0; i < 100_000; i++) {
            boolean useNull = (i % 2) == 0;
            LargeValueWithOops val = useNull ? null : new LargeValueWithOops(i);
            caller(val, i, useNull);
        }
    }
}
