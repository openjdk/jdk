/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8384101
 * @summary Test that references to boxed primitives are not considered final.
 * @library /test/lib /
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:+StressIncrementalInlining -XX:StressSeed=2
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.c2;

import jdk.test.lib.Asserts;

public class TestWrappedBoxedPrimitive {
    private static final class Holder {
        Integer notZero = 0xbad;
        private void touch () {
            notZero = 1234;
        }
    }

    private static int testHolderKlass() {
        Holder holder = new Holder();
        holder.touch();
        return holder.notZero;
    }

    private static void touchArray(Integer[] arr) {
        arr[0] = 12345;
    }

    private static int testArray() {
        Integer[] ary = new Integer[] { 0xbad };
        touchArray(ary);
        return ary[0];

    }

    public static void main(String[] args) {
        int intHolderKlass = testHolderKlass();
        int intHolderArray = testArray();
        for (int i = 0; i < 10_000; i++) {
            testHolderKlass();
            testArray();
        }
        int c2HolderKlass = testHolderKlass();
        int c2HolderArray = testArray();

        Asserts.assertEQ(intHolderKlass, c2HolderKlass);
        Asserts.assertEQ(intHolderArray, c2HolderArray);
    }
}
