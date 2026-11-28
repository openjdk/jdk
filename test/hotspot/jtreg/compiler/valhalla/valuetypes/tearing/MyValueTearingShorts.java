/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.valhalla.valuetypes.tearing;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

@LooselyConsistentValue
value class MyValueTearing {
    short x;
    short z;
    short y;

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long X_OFFSET;
    private static final long Y_OFFSET;
    static {
        try {
            X_OFFSET = U.objectFieldOffset(MyValueTearing.class, "x");
            Y_OFFSET = U.objectFieldOffset(MyValueTearing.class, "y");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final MyValueTearing DEFAULT = new MyValueTearing(0, 0);
    private static final boolean SHOULD_CHECK_UNSAFE;
    static {
        var array = new MyValueTearing[1];
        SHOULD_CHECK_UNSAFE = ValueClass.isFlatArray(array);
    }

    MyValueTearing(int x, int y) {
        this.x = (short) x;
        this.y = (short) y;
        this.z = (short) 0;
    }

    MyValueTearing incrementAndCheck() {
        verify();
        return new MyValueTearing(x + 1, y + 1);
    }

    MyValueTearing incrementAndCheckUnsafe() {
        Asserts.assertTrue(SHOULD_CHECK_UNSAFE);
        verify();
        MyValueTearing[] vt = (MyValueTearing[]) ValueClass.newNullRestrictedAtomicArray(MyValueTearing.class, 1, this);
        long baseOffset = U.arrayInstanceBaseOffset(vt) - U.valueHeaderSize(MyValueTearing.class);
        U.putShort(vt, baseOffset + X_OFFSET, (short)(x + 1));
        U.putShort(vt, baseOffset + Y_OFFSET, (short)(y + 1));
        return vt[0];
    }

    void verify() {
        Asserts.assertEQ(x, y, "Inconsistent field values");
    }
}

public class MyValueTearingShorts {
}
