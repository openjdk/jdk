/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280440
 * @summary Test that speculative types are properly handled by scalarization.
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestWithSpeculativeTypes::*
 *                   -XX:TypeProfileLevel=222 -XX:-TieredCompilation -Xbatch
 *                   compiler.valhalla.inlinetypes.TestWithSpeculativeTypes
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class TestWithSpeculativeTypes {

    static value class MyValue {
        int x = 0;
    }

    static MyValue getNull() {
        return null;
    }

    // Return value has speculative type NULL
    static boolean test1() {
        return getNull() == null;
    }

    // Argument has speculative type NULL
    static boolean test2(MyValue vt) {
        return vt == null;
    }

    public static void main(String[] args) {
        // Make sure class is loaded
        MyValue val = new MyValue();
        for (int i = 0; i < 100_000; ++i) {
            Asserts.assertTrue(test1());
            Asserts.assertTrue(test2(null));
        }
    }
}
