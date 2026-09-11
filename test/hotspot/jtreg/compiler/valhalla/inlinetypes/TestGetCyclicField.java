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

import jdk.internal.misc.Unsafe;

/*
 * @test
 * @bug 8378686
 * @summary Cyclic fields should be scalarized at getfield.
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @run main ${test.main.class}
 */
public class TestGetCyclicField {
    private static value class MyValue {
        MyValue other;

        MyValue(MyValue other) {
            this.other = other;
        }
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long OTHER_OFFSET = U.objectFieldOffset(MyValue.class, "other");

    private static MyValue testGetField(MyValue a) {
        MyValue b = (a.other == null) ? new MyValue(a) : a.other;
        MyValue c = (b.other == null) ? new MyValue(b) : b.other;
        return c;
    }

    private static MyValue testUnsafe(MyValue a) {
        MyValue b = (U.getReference(a, OTHER_OFFSET) == null) ? new MyValue(a) : (MyValue)U.getReference(a, OTHER_OFFSET);
        MyValue c = (U.getReference(b, OTHER_OFFSET) == null) ? new MyValue(b) : (MyValue)U.getReference(b, OTHER_OFFSET);
        return c;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testGetField(new MyValue(null));
            testUnsafe(new MyValue(null));
        }
    }
}
