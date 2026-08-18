/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8354981 8359345
 * @summary Test that membars are emitted around flat, atomic loads and stores.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch compiler.valhalla.inlinetypes.TestMemBars
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;

public class TestMemBars {
    static long VAL = 42; // Prevent constant folding

    public TestMemBars() {
        field1 = new MyValue1();
        field2 = new MyValue2();
        super();
    }

    static value class MyValue1 {
        @NullRestricted
        MyValue3 val = new MyValue3(); // Too large to be flattened

        int unused = 42; // Make sure it's not naturally atomic
    }

    static value class MyValue2 {
        MyValue3 val = new MyValue3(); // Too large to be flattened

        int unused = 42; // Make sure it's not naturally atomic
    }

    static value class MyValue3 {
        long l0 = VAL;
        long l1 = VAL;
    }

    @NullRestricted
    MyValue1 field1;

    @NullRestricted
    MyValue2 field2;

    static MyValue1[] array1 = new MyValue1[1];
    static MyValue1[] array2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1());
    static MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedAtomicArray(MyValue1.class, 1, new MyValue1());
    static MyValue1[] array4 = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, 1);
    static {
        array1[0] = new MyValue1();
        array4[0] = new MyValue1();
    }

    static MyValue2[] array5 = new MyValue2[1];
    static MyValue2[] array6 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2());
    static MyValue2[] array7 = (MyValue2[])ValueClass.newNullRestrictedAtomicArray(MyValue2.class, 1, new MyValue2());
    static MyValue2[] array8 = (MyValue2[])ValueClass.newNullableAtomicArray(MyValue2.class, 1);
    static {
        array5[0] = new MyValue2();
        array8[0] = new MyValue2();
    }

    public long testFieldLoad1() {
        return field1.val.l0;
    }

    public void testFieldStore1(MyValue1 val) {
        field1 = val;
    }

    public long testFieldLoad2() {
        return field2.val.l0;
    }

    public void testFieldStore2(MyValue2 val) {
        field2 = val;
    }

    public long testArrayLoad1() {
        return array1[0].val.l0;
    }

    public void testArrayStore1(MyValue1 val) {
        array1[0] = val;
    }

    public long testArrayLoad2() {
        return array2[0].val.l0;
    }

    public void testArrayStore2(MyValue1 val) {
        array2[0] = val;
    }

    public long testArrayLoad3() {
        return array3[0].val.l0;
    }

    public void testArrayStore3(MyValue1 val) {
        array3[0] = val;
    }

    public long testArrayLoad4() {
        return array4[0].val.l0;
    }

    public void testArrayStore4(MyValue1 val) {
        array4[0] = val;
    }

    public long testArrayLoad5() {
        return array5[0].val.l0;
    }

    public void testArrayStore5(MyValue2 val) {
        array5[0] = val;
    }

    public long testArrayLoad6() {
        return array6[0].val.l0;
    }

    public void testArrayStore6(MyValue2 val) {
        array6[0] = val;
    }

    public long testArrayLoad7() {
        return array7[0].val.l0;
    }

    public void testArrayStore7(MyValue2 val) {
        array7[0] = val;
    }

    public long testArrayLoad8() {
        return array8[0].val.l0;
    }

    public void testArrayStore8(MyValue2 val) {
        array8[0] = val;
    }

    public long testFieldLoadStore1(MyValue1 val) {
        long res = field1.val.l0;
        field1 = val;
        return res;
    }

    public long testFieldLoadStore1Independent(MyValue1 val) {
        long res = field2.val.l0;
        field1 = val;
        return res;
    }

    public long testFieldStoreLoad1(MyValue1 val) {
        field1 = val;
        return field1.val.l0;
    }

    public long testFieldStoreLoad1Independent(MyValue1 val) {
        field1 = val;
        return field2.val.l0;
    }

    public long testArrayLoadStore1(MyValue1 val) {
        long res = array3[0].val.l0;
        array3[0] = val;
        return res;
    }

    public long testArrayLoadStore1Independent(MyValue1 val) {
        long res = array7[0].val.l0;
        array3[0] = val;
        return res;
    }

    public long testArrayStoreLoad1(MyValue1 val) {
        array3[0] = val;
        return array3[0].val.l0;
    }

    public long testArrayStoreLoad1Independent(MyValue1 val) {
        array3[0] = val;
        return array7[0].val.l0;
    }

    public static void main(String[] args) {
        TestMemBars t = new TestMemBars();
        for (int i = 0; i < 50_000; ++i) {
            VAL++;
            MyValue1 val1 = new MyValue1();
            MyValue2 val2 = new MyValue2();

            t.testFieldStore1(val1);
            Asserts.assertEQ(t.testFieldLoad1(), VAL);
            t.testFieldStore2(val2);
            Asserts.assertEQ(t.testFieldLoad2(), VAL);
            t.testArrayStore1(val1);
            Asserts.assertEQ(t.testArrayLoad1(), VAL);
            t.testArrayStore2(val1);
            Asserts.assertEQ(t.testArrayLoad2(), VAL);
            t.testArrayStore3(val1);
            Asserts.assertEQ(t.testArrayLoad3(), VAL);
            t.testArrayStore4(val1);
            Asserts.assertEQ(t.testArrayLoad4(), VAL);
            t.testArrayStore5(val2);
            Asserts.assertEQ(t.testArrayLoad5(), VAL);
            t.testArrayStore6(val2);
            Asserts.assertEQ(t.testArrayLoad6(), VAL);
            t.testArrayStore7(val2);
            Asserts.assertEQ(t.testArrayLoad7(), VAL);
            t.testArrayStore8(val2);
            Asserts.assertEQ(t.testArrayLoad8(), VAL);

            VAL++;
            val1 = new MyValue1();
            val2 = new MyValue2();

            Asserts.assertEQ(t.testFieldLoadStore1(val1), VAL-1);
            Asserts.assertEQ(t.field1, val1);
            Asserts.assertEQ(t.testFieldLoadStore1Independent(val1), VAL-1);
            Asserts.assertEQ(t.field1, val1);

            VAL++;
            val1 = new MyValue1();
            val2 = new MyValue2();

            Asserts.assertEQ(t.testFieldStoreLoad1(val1), VAL);
            Asserts.assertEQ(t.field1, val1);
            Asserts.assertEQ(t.testFieldStoreLoad1Independent(val1), VAL-2);
            Asserts.assertEQ(t.field1, val1);

            VAL++;
            val1 = new MyValue1();
            val2 = new MyValue2();

            Asserts.assertEQ(t.testArrayLoadStore1(val1), VAL-3);
            Asserts.assertEQ(t.array3[0], val1);
            Asserts.assertEQ(t.testArrayLoadStore1Independent(val1), VAL-3);
            Asserts.assertEQ(t.array3[0], val1);

            VAL++;
            val1 = new MyValue1();
            val2 = new MyValue2();

            Asserts.assertEQ(t.testArrayStoreLoad1(val1), VAL);
            Asserts.assertEQ(t.array3[0], val1);
            Asserts.assertEQ(t.testArrayStoreLoad1Independent(val1), VAL-4);
            Asserts.assertEQ(t.array3[0], val1);
        }
    }
}

