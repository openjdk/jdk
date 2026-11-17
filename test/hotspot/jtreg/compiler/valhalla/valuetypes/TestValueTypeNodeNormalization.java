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
 * @bug 8393865
 * @summary [Valhalla] C2: assert(_gvn.type(stored_value_casted) == TypePtr::NULL_PTR) failed: Unexpected value
 * @enablePreview
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
                     -Xcomp ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.valhalla.valuetypes;

public class TestValueTypeNodeNormalization {
    static int[] array1 = new int[1];

    static void test1() {
        helper1(42,false);
    }

    static int helper1(Object obj, boolean b) {
        if (array1 != null && obj != null && array1.length > 0) {
            return 0;
        }
        Integer val = (Integer) obj;
        if (b) {
            val = val;
        }
        return val;
    }

    static Object[] array2 = new Long[1];

    static void test2() {
        helper2(0L);
    }

    static void helper2(Object val) {
        if (array2 != null && val != null && array2.length > 0) {
            return;
        }
        array2[0] = val;
    }

    public static void main(String[] args) {
        test1();
        test2();
    }
}
