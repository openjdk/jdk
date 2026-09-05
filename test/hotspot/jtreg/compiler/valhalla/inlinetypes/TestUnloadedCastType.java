/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test casting of value objects to an unloaded type.
 * @enablePreview
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestUnloadedCastType::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedCastType
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class TestUnloadedCastType {

    static value class MyValue {
        int x = 0;
    }

    static class Unloaded { }

    public static Object test(MyValue val) {
        Object obj = val;
        return (Unloaded)obj;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            Asserts.assertEQ(test(null), null);
        }
        try {
            test(new MyValue());
            throw new RuntimeException("No ClassCastException thrown");
        } catch (ClassCastException e) {
            // Expected
        }
    }
}
