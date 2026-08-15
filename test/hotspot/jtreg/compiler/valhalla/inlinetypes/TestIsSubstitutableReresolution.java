/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8234108
 * @library /testlibrary /test/lib
 * @summary Verify that call reresolution works for C2 compiled calls to java.lang.runtime.ValueObjectMethods::isSubstitutable0.
 * @enablePreview
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestIsSubstitutableReresolution::test
 *                   compiler.valhalla.inlinetypes.TestIsSubstitutableReresolution
 */

value class MyValueIsSubstReresolution {
    int x;

    public MyValueIsSubstReresolution(int x) {
        this.x = x;
    }
}

public class TestIsSubstitutableReresolution {

    static boolean test(Object obj) {
        MyValueIsSubstReresolution vt = new MyValueIsSubstReresolution(42);
        return vt == obj;
    }

    public static void main(String[] args) throws Exception {
        MyValueIsSubstReresolution vt1 = new MyValueIsSubstReresolution(42);
        MyValueIsSubstReresolution vt2 = new MyValueIsSubstReresolution(43);
        for (int i = 0; i < 1_000_000; ++i) {
            Asserts.assertEQ(test(vt1), true);
            Asserts.assertEQ(test(vt2), false);
        }
    }
}
