/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295486
 * @summary Verify that constant folding of field loads observes consistent values during compilation.
 * @library /test/lib /
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/bootclasspath/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                 -XX:-TieredCompilation -Xbatch -XX:PerMethodRecompilationCutoff=-1
 *                                 -XX:CompileCommand=compileonly,compiler.stable.TestUnstableStable::test*
 *                                 compiler.stable.TestUnstableStable
 */

package compiler.stable;

import compiler.whitebox.CompilerWhiteBoxTest;

import java.lang.reflect.Method;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;
import jdk.test.whitebox.WhiteBox;

public class TestUnstableStable {
    static final Unsafe U = Unsafe.getUnsafe();
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    static final TestUnstableStable HOLDER = new TestUnstableStable();

    @Stable Integer stableField = null;
    static @Stable Integer staticStableField = null;
    static @Stable Integer[] stableArray0 = new Integer[1];
    static @Stable Integer[][] stableArray1 = new Integer[1][1];

    static final Integer finalField = 43;

    static final long FIELD_OFFSET;
    static {
        try {
            FIELD_OFFSET = U.staticFieldOffset(TestUnstableStable.class.getDeclaredField("finalField"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found", e);
        }
    }

    static class Writer implements Runnable {
        public void run() {
            while (true) {
                HOLDER.stableField = null;
                HOLDER.stableField = 42;
                HOLDER.stableField = 43;
                staticStableField = null;
                staticStableField = 42;
                staticStableField = 43;
                stableArray0[0] = null;
                stableArray0[0] = 42;
                stableArray0[0] = 43;
                stableArray1[0] = null;
                Integer[] tmp1 = {null};
                stableArray1[0] = tmp1;
                Integer[] tmp2 = {42};
                stableArray1[0] = tmp2;
                Integer[] tmp3 = {43};
                stableArray1[0] = tmp3;
                stableArray1[0][0] = null;
                stableArray1[0][0] = 42;
                stableArray1[0][0] = 43;
                U.putReference(TestUnstableStable.class, FIELD_OFFSET, null);
                U.putReference(TestUnstableStable.class, FIELD_OFFSET, 42);
                U.putReference(TestUnstableStable.class, FIELD_OFFSET, 43);
            }
        }
    }

    static Object testNonStatic() {
        // Trigger PhaseCCP and LoadNode::Value -> Type::make_constant_from_field
        // which may observe different values of the stable field when invoked twice.
        Integer val = HOLDER.stableField;
        if (val == null) {
            val = null;
        }
        return val;
    }

    static Object testStatic() {
        Integer val = staticStableField;
        if (val == null) {
            val = null;
        }
        return val;
    }

    static Object testArray0() {
        Integer val = stableArray0[0];
        if (val == null) {
            val = null;
        }
        return val;
    }

    static Object testArray1() {
        Integer[] val = stableArray1[0];
        if (val == null) {
            val = null;
        } else {
            return val[0];
        }
        return val;
    }

    static Object testFinal() {
        Integer val = finalField;
        if (val == null) {
            val = null;
        }
        return val;
    }

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(new Writer());
        t.start();
        Method testNonStatic = TestUnstableStable.class.getDeclaredMethod("testNonStatic");
        Method testStatic = TestUnstableStable.class.getDeclaredMethod("testStatic");
        Method testArray0 = TestUnstableStable.class.getDeclaredMethod("testArray0");
        Method testArray1 = TestUnstableStable.class.getDeclaredMethod("testArray1");
        Method testFinal = TestUnstableStable.class.getDeclaredMethod("testFinal");
        testFinal();
        for (int i = 0; i < 1000; ++i) {
            WHITE_BOX.deoptimizeMethod(testNonStatic, false);
            WHITE_BOX.enqueueMethodForCompilation(testNonStatic, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
            WHITE_BOX.deoptimizeMethod(testStatic, false);
            WHITE_BOX.enqueueMethodForCompilation(testStatic, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
            WHITE_BOX.deoptimizeMethod(testArray0, false);
            WHITE_BOX.enqueueMethodForCompilation(testArray0, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
            WHITE_BOX.deoptimizeMethod(testArray1, false);
            WHITE_BOX.enqueueMethodForCompilation(testArray1, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
            WHITE_BOX.deoptimizeMethod(testFinal, false);
            WHITE_BOX.enqueueMethodForCompilation(testFinal, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        }
    }
}
