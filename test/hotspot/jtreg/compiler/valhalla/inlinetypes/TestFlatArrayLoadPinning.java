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

/**
 * @test
 * @summary Test correct pinning of loads from flat arrays.
 * @bug 8384405
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestFlatArrayLoadPinning {

    @LooselyConsistentValue
    static value class MyValueWithInts {
        int i1;
        int i2;

        MyValueWithInts(int i) {
            i1 = i;
            i2 = i;
        }
    }

    static int test(MyValueWithInts[] array, int limit) {
        int res = 0;
        for (int i = 0; i < limit; ++i) {
            int index = (i == 7) ? Integer.MAX_VALUE : i;
            // If below accesses are not pinned to both the flat-array layout check,
            // as well as the range check, they can float above and access out-of-bounds.
            MyValueWithInts val = array[index];
            res += val.i1 + val.i2;
        }
        return res;
    }


    public static void main(String[] args) {
        MyValueWithInts[] array = (MyValueWithInts[])ValueClass.newNullRestrictedAtomicArray(MyValueWithInts.class, 10, new MyValueWithInts(42));
        for (int i = 0; i < 10; ++i) {
            try {
                test(array, 10);
                throw new RuntimeException("No ArrayIndexOutOfBoundsException thrown!");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // Expected
            }
        }
    }
}

