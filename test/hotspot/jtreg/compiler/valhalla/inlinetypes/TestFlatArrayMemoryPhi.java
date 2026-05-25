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

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

/*
 * @test
 * @summary Test EA memory splitting with pre-existing flat array memory Phis.
 * @requires vm.gc.Z
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:+UseZGC -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

public class TestFlatArrayMemoryPhi {
    static value class MyValue {
        byte b1 = 42;
        byte b2 = 43;
    }

    static MyValue[] array = (MyValue[])ValueClass.newReferenceArray(MyValue.class, 2);

    static void equals(MyValue a, MyValue b) {
        if (a != b && !a.equals(b)) {
            throw new RuntimeException("Unexpected result");
        }
    }

    public static void test() {
        MyValue[] flatArray = new MyValue[2];

        equals(array[0], array[0]);
        equals(flatArray[1], flatArray[1]);
        equals(flatArray[0], array[0]);
    }

    public static void main(String[] args) {
        test();
    }
}

