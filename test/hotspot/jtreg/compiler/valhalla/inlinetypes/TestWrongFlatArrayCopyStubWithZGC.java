/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.gc.Z
 * @bug 8313667
 * @summary Test that GenZ uses correct array copy stub for flat value class arrays when expanding ArrayCopyNode.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:+UseZGC
 *                   -XX:CompileCommand=exclude,compiler.valhalla.inlinetypes.TestWrongFlatArrayCopyStubWithZGC::check
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestWrongFlatArrayCopyStubWithZGC::test*
 *                   compiler.valhalla.inlinetypes.TestWrongFlatArrayCopyStubWithZGC
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestWrongFlatArrayCopyStubWithZGC {

    public static void main(String[] args) {
        ValueWithLong[] arrWithLong = (ValueWithLong[])ValueClass.newNullRestrictedNonAtomicArray(ValueWithLong.class, 3, new ValueWithLong(0));
        arrWithLong[0] = new ValueWithLong(0x408BE000000fffffL);
        arrWithLong[1] = new ValueWithLong(0x408BE0000000000L);
        long randomValue = Utils.getRandomInstance().nextLong();
        arrWithLong[2] = new ValueWithLong(randomValue);

        for (int i = 0; i < 10000; i++) {
            ValueWithLong[] result = testLong(arrWithLong);
            check(result[0].l, 0x408BE000000fffffL);
            check(result[1].l, 0x408BE0000000000L);
            check(result[2].l, randomValue);
        }

        ValueWithOop[] arrWithOop = (ValueWithOop[])ValueClass.newNullRestrictedNonAtomicArray(ValueWithOop.class, 2, new ValueWithOop());
        for (int i = 0; i < 10000; i++) {
            testOop(arrWithOop);
        }
    }

    static void check(long result, long expected) {
        Asserts.assertEQ(result, expected);
    }

    static ValueWithLong[] testLong(ValueWithLong[] arr) {
        return arr.clone();
    }

    static ValueWithOop[] testOop(ValueWithOop[] arr) {
        return arr.clone();
    }
}

@LooselyConsistentValue
value class ValueWithLong {
    long l;

    public ValueWithLong(long l) {
        this.l = l;
    }
}

@LooselyConsistentValue
value class ValueWithOop {
    Object v;

    public ValueWithOop() {
        this.v = new Object();
    }
}

