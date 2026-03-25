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

/**
 * @test
 * @bug 8209687
 * @summary Verify that Parse::optimize_cmp_with_klass() works with value classes.
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -Xbatch compiler.valhalla.inlinetypes.TestOptimizeKlassCmp
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

value class MyValueOptKlassCmp {
    public int x;

    public MyValueOptKlassCmp(int x) {
        this.x = x;
    }
}

public class TestOptimizeKlassCmp {

    public static boolean test1(MyValueOptKlassCmp v1, MyValueOptKlassCmp v2) {
        return v1.equals(v2);
    }

    public static boolean test2(MyValueOptKlassCmp v1, MyValueOptKlassCmp v2) {
        return v1.getClass().equals(v2.getClass());
    }

    public static boolean test3(Object o1, Object o2) {
        return o1.getClass().equals(o2.getClass());
    }

    public static void main(String[] args) {
        MyValueOptKlassCmp v1 = new MyValueOptKlassCmp(0);
        MyValueOptKlassCmp v2 = new MyValueOptKlassCmp(1);
        for (int i = 0; i < 10_000; ++i) {
            Asserts.assertFalse(test1(v1, v2));
            Asserts.assertTrue(test1(v1, v1));
            Asserts.assertTrue(test2(v1, v2));
            Asserts.assertTrue(test2(v1, v1));
            Asserts.assertTrue(test3(v1, v2));
            Asserts.assertTrue(test3(v1, v1));
        }
    }
}
