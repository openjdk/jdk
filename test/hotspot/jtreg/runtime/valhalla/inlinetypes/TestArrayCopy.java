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
 * @test TestArrayCopy
 * @bug 8388480
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm runtime.valhalla.inlinetypes.TestArrayCopy
 */

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

public class TestArrayCopy {

    static value record SmallPoint(byte x, byte y) { }
    static interface MyInterface {}

    @LooselyConsistentValue
    static value class MyValue implements MyInterface {
        int i;
        short s;

        public MyValue(int i, short s) {
            this.i = i;
            this.s = s;
        }
    }

    static value record Complex(double r, double i) { }

    static void test_0() {
        var a = new MyValue[10];
        Object npe = null;
        try {
            System.arraycopy(null, 0, a, 1, 2);
        } catch (NullPointerException e) {
            npe = e;
        }
        Asserts.assertNotNull(npe, "Missing NullPointerException");
    }

    static void test_1() {
        var a = new MyValue[10];
        Object npe = null;
        try {
            System.arraycopy(a, 0, null, 1, 2);
        } catch (NullPointerException e) {
            npe = e;
        }
      Asserts.assertNotNull(npe, "Missing NullPointerException");
    }

    static void test_2() {
        var a = new MyValue[10];
        Object ase = null;
        try {
            System.arraycopy(a, 0, new Object(), 1, 2);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase, "Missing ArrayStoreException");
    }

    static void test_3() {
        var a = new MyValue[10];
        Object ase = null;
        try {
            System.arraycopy(new Object(), 0, a, 1, 2);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase, "Missing ArrayStoreException");
    }

    static void test_4() {
        var a = new MyValue[10];
        Object ase = null;
        try {
            System.arraycopy(a, 0, new int[10], 1, 2);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase, "Missing ArrayStoreException");
    }

    static void test_5() {
        var a = new MyValue[10];
        Object ase = null;
        try {
            System.arraycopy(new int[10], 0, a, 1, 2);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase, "Missing ArrayStoreException");
    }

    static void test_6() {
        var a = new MyValue[10];
        var b = new MyValue[8];
        Object ioobe = null;
        try {
            System.arraycopy(a, -1, b, 1, 2);
        } catch (IndexOutOfBoundsException e) {
            ioobe = e;
        }
        Asserts.assertNotNull(ioobe, "Missing IndexOutOfBoundsException");
    }

    static void test_7() {
        var a = new MyValue[10];
        var b = new MyValue[8];
        Object ioobe = null;
        try {
            System.arraycopy(a, 0, b, -1, 2);
        } catch (IndexOutOfBoundsException e) {
            ioobe = e;
        }
        Asserts.assertNotNull(ioobe, "Missing IndexOutOfBoundsException");
    }

    static void test_8() {
        var a = new MyValue[10];
        var b = new MyValue[8];
        Object ioobe = null;
        try {
            System.arraycopy(a, 0, b, 1, -2);
        } catch (IndexOutOfBoundsException e) {
            ioobe = e;
        }
        Asserts.assertNotNull(ioobe, "Missing IndexOutOfBoundsException");
    }

    static void test_9() {
        var a = new MyValue[5];
        var b = new MyValue[10];
        Object ioobe = null;
        try {
            System.arraycopy(a, 0, b, 1, 6);
        } catch (IndexOutOfBoundsException e) {
            ioobe = e;
        }
        Asserts.assertNotNull(ioobe, "Missing IndexOutOfBoundsException");
    }

    static void test_10() {
        var a = new MyValue[10];
        var b = new MyValue[5];
        Object ioobe = null;
        try {
            System.arraycopy(a, 0, b, 1, 5);
        } catch (IndexOutOfBoundsException e) {
            ioobe = e;
        }
        Asserts.assertNotNull(ioobe, "Missing IndexOutOfBoundsException");
    }

    static void test_11() {
        var a = new SmallPoint[10];
        var b = new MyValue[10];
        var sp = new SmallPoint((byte)1, (byte)2);
        var mv = new MyValue(2, (short)3);
        for (int i = 0; i < 10; i++) {
            a[i] = sp;
            b[i] = mv;
        }
        // Must not throw ArrayStoreException (nothing is copied)
        System.arraycopy(a, 0, b, 0, 0);
    }

    static void test_12() {
        var a = new SmallPoint[10];
        var b = new MyValue[10];
        // Must not throw ArrayStoreException (copying nulls)
        System.arraycopy(a, 0, b, 1, 5);
    }

    static void test_13() {
        var a = new SmallPoint[10];
        var b = new SmallPoint[10];
        var c = new SmallPoint[10];
        for (int i = 0; i < 10; i++) {
            a[i] = new SmallPoint((byte)i, (byte)(i+1));
            b[i] = new SmallPoint((byte)(i*10), (byte)((i+1)*10));;
            c[i] = b[i];
        }
        System.arraycopy(a, 2, c, 4, 3);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
        for (int i = 0; i < 3; i++) {
            Asserts.assertEquals(c[4 + i], a[2 + i]);
        }
        for (int i = 7; i < 10; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
    }

    static void test_14() {
        var a = new SmallPoint[10];
        var b = new MyValue[10];
        var sp = new SmallPoint((byte)1, (byte)2);
        for (int i = 0; i < 10; i++) {
            a[i] = sp;
        }
        // Must not throw ArrayStoreException (copying nulls)
        System.arraycopy(b, 3, a, 4, 5);
        for (int i = 0 ; i < 4; i++) {
            Asserts.assertNotNull(a[i]);
        }
        for (int i = 4; i < 9; i++) {
            Asserts.assertNull(a[i]);
        }
        Asserts.assertNotNull(a[9]);
    }

    static void test_15() {
        var a = new SmallPoint[10];
        var b = new MyValue[10];
        var sp = new SmallPoint((byte)1, (byte)2);
        for (int i = 0; i < 10; i++) {
            a[i] = sp;
        }
        b[7] = new MyValue(1, (short)2);
        Object ase = null;
        try {
          System.arraycopy(b, 3, a, 4, 5);
        } catch (ArrayStoreException e) {
          ase  = e;
        }
        Asserts.assertNotNull(ase);
        for (int i = 0 ; i < 4; i++) {
            Asserts.assertNotNull(a[i]);
        }
        for (int i = 4; i < 8; i++) {
            Asserts.assertNull(a[i]);
        }
        Asserts.assertNotNull(a[8]);
        Asserts.assertNotNull(a[9]);
    }

    static void test_16() {
        var a = new MyValue[10];
        var mv = new MyValue(1, (short)2);
        for (int i = 0; i < 10; i++) {
            a[i] = mv;
        }
        var b = new MyInterface[10];
        System.arraycopy(a, 1, b, 2, 3);
        for (int i = 0 ; i < 2; i++) {
            Asserts.assertNull(b[i]);
        }
        for (int i = 0; i < 3; i++) {
            Asserts.assertEquals(b[2+i], a[1+i]);
        }
        for (int i = 5; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

      static void test_17() {
        var a =  new MyInterface[10];
        var mv = new MyValue(1, (short)2);
        for (int i = 0; i < 10; i++) {
            a[i] = mv;
        }
        var b = new MyValue[10];
        System.arraycopy(a, 1, b, 2, 3);
        for (int i = 0 ; i < 2; i++) {
            Asserts.assertNull(b[i]);
        }
        for (int i = 0; i < 3; i++) {
            Asserts.assertEquals(b[2+i], a[1+i]);
        }
        for (int i = 5; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

    static void test_18() {
        var a = new MyValue[10];
        var mv = new MyValue(1, (short)2);
        for (int i = 5; i < 10; i++) {
            a[i] = mv;
        }
        var b = new Comparable[10];
        var mi = Integer.valueOf(3);
        for (int i = 0; i < 10; i++) {
            b[i] = mi;
        }
        Object ase = null;
        try {
            System.arraycopy(a, 1, b, 2, 6);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase);
        Asserts.assertEQ(b[0], mi);
        Asserts.assertEQ(b[1], mi);
        for (int i = 2; i < 6; i++) {
            Asserts.assertNull(b[i]);
        }
        for (int i = 6; i < 10; i++) {
            Asserts.assertEQ(b[i], mi);
        }
    }

    static void test_19() {
        var a = new MyValue[10];
        var mv = new MyValue(1, (short)2);
        for (int i = 5; i < 10; i++) {
            a[i] = mv;
        }
        var b = new Complex[10];
        var mc = new Complex(4.0d, 2.0d);
        for (int i = 0; i < 10; i++) {
            b[i] = mc;
        }
        Object ase = null;
        try {
            System.arraycopy(a, 1, b, 2, 6);
        } catch (ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase);
        Asserts.assertEQ(b[0], mc);
        Asserts.assertEQ(b[1], mc);
        for (int i = 2; i < 6; i++) {
            Asserts.assertNull(b[i]);
        }
        for (int i = 6; i < 10; i++) {
            Asserts.assertEQ(b[i], mc);
        }
    }

    static void test_20() {
        var a = new SmallPoint[10];
        var sp = new SmallPoint((byte)1, (byte)2);
        var b = (SmallPoint[])ValueClass.newNullRestrictedAtomicArray(SmallPoint.class, 10, sp);
        var c = (SmallPoint[])ValueClass.newNullRestrictedAtomicArray(SmallPoint.class, 10, sp);
        for (int i = 0; i < 10; i++) {
            a[i] = new SmallPoint((byte)i, (byte)(i+1));
            b[i] = new SmallPoint((byte)(i*10), (byte)((i+1)*10));;
            c[i] = b[i];
        }
        System.arraycopy(a, 2, c, 4, 3);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
        for (int i = 0; i < 3; i++) {
            Asserts.assertEquals(c[4 + i], a[2 + i]);
        }
        for (int i = 7; i < 10; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
    }

    static void test_21() {
        var a = new SmallPoint[10];
        var sp = new SmallPoint((byte)1, (byte)2);
        var b = (SmallPoint[])ValueClass.newNullRestrictedAtomicArray(SmallPoint.class, 10, sp);
        var c = (SmallPoint[])ValueClass.newNullRestrictedAtomicArray(SmallPoint.class, 10, sp);
        for (int i = 0; i < 10; i++) {
            a[i] = new SmallPoint((byte)i, (byte)(i+1));
            b[i] = new SmallPoint((byte)(i*10), (byte)((i+1)*10));
            c[i] = b[i];
        }
        a[4] = null;
        Object npe = null;
        try {
            System.arraycopy(a, 2, c, 4, 3);
        } catch (NullPointerException e) {
            npe = e;
        }
        Asserts.assertNotNull(npe);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
        for (int i = 0; i < 2; i++) {
            Asserts.assertEquals(c[4 + i], a[2 + i]);
        }
        for (int i = 6; i < 10; i++) {
            Asserts.assertEquals(c[i], b[i]);
        }
    }

    static void test_22() {
        var a = new SmallPoint[10];
        var c = new Complex(2.0d, 4.0d);
        var b = (Complex[])ValueClass.newNullRestrictedAtomicArray(Complex.class, 10, c);
        Object npe = null;
        try {
            System.arraycopy(a, 2, b, 4, 3);
        } catch (NullPointerException e) {
            npe = e;
        }
        Asserts.assertNotNull(npe);
        for (int i = 0; i < 10; i++) {
            Asserts.assertEquals(b[i], c);
        }
    }

    static void test_23() {
        var a = (SmallPoint[])ValueClass.newReferenceArray(SmallPoint.class, 10);
        var b = new SmallPoint[10];
        SmallPoint sp0 = new SmallPoint((byte)1, (byte)2);
        SmallPoint sp1 = new SmallPoint((byte)3, (byte)4);
        for (int i = 0; i < 10; i++) {
            a[i] = sp0;
            b[i] = sp1;
        }
        System.arraycopy(a, 2, b, 4, 3);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(b[i], sp1);
        }
        for (int i = 4; i < 7; i++) {
            Asserts.assertEquals(b[i], sp0);
        }
        for (int i = 7; i < 10; i++) {
            Asserts.assertEquals(b[i], sp1);
        }
    }

    static void test_24() {
        SmallPoint sp0 = new SmallPoint((byte)1, (byte)2);
        SmallPoint sp1 = new SmallPoint((byte)3, (byte)4);
        var a = (SmallPoint[])ValueClass.newReferenceArray(SmallPoint.class, 10);
        var b = (SmallPoint[])ValueClass.newNullRestrictedAtomicArray(SmallPoint.class, 10, sp1);
        for (int i = 0; i < 10; i++) {
            a[i] = sp0;
        }
        a[4] = null;
        Object npe = null;
        try {
            System.arraycopy(a, 2, b, 4, 3);
        } catch(NullPointerException e) {
            npe = e;
        }
        Asserts.assertNotNull(npe);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(b[i], sp1);
        }
        for (int i = 4; i < 6; i++) {
            Asserts.assertEquals(b[i], sp0);
        }
        for (int i = 6; i < 10; i++) {
            Asserts.assertEquals(b[i], sp1);
        }
    }

    static void test_25() {
        var a = new SmallPoint[10];
        var b = new SmallPoint[10];
        for (int i = 0; i < 10; i++) {
            a[i] = new SmallPoint((byte)i, (byte)(i+1));
            b[i] = a[i];
        }
        System.arraycopy(b, 2, b, 4, 5);
        for (int i = 0; i < 4; i++) {
            Asserts.assertEquals(b[i], a[i]);
        }
        for (int i = 0; i < 5; i++) {
            Asserts.assertEquals(b[4 + i], a[2 + i]);
        }
        for (int i = 9; i < 10; i++) {
            Asserts.assertEquals(b[i], a[i]);
        }
    }

    static void test_26() {
        var a = new SmallPoint[10];
        var mv = new MyValue(1, (short)2);
        var b = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 10, mv);
        Object npe = null;
        try {
            System.arraycopy(a, 1, b, 3, 2);
        } catch (NullPointerException e) {
            npe = e;
        }
        Asserts.assertNotNull(npe);
        for (int i = 0; i < 10; i++) {
            Asserts.assertEquals(b[i], mv);
        }
    }

    static void test_27() {
        var a = new String[10];
        for (int i = 0; i < 10; i++) {
            a[i] = "hello";
        }
        var b = new SmallPoint[10];
        Object ase = null;
        try {
            System.arraycopy(a, 2, b, 3, 2);
        } catch(ArrayStoreException e) {
            ase = e;
        }
        Asserts.assertNotNull(ase);
        for (int i = 0; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

    static void test_28() {
        var a = new String[10];
        var b = new SmallPoint[10];
        System.arraycopy(a, 2, b, 3, 2);
        for (int i = 0; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

    static void test_29() {
        var a = (SmallPoint[])ValueClass.newReferenceArray(SmallPoint.class, 10);
        var b = (SmallPoint[])ValueClass.newNullableAtomicArray(SmallPoint.class, 10);
        var sp = new SmallPoint((byte)1, (byte)3);
        for (int i = 2; i < 5; i++) {
            a[i] = sp;
        }
        System.arraycopy(a, 2, b, 3, 5);
        for (int i = 0; i < 3; i++) {
            Asserts.assertNull(b[i]);
        }
        for (int i = 3; i < 6; i++) {
            Asserts.assertEquals(b[i], sp);
        }
        for (int i = 6; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

    static void test_30() {
        var mv = new MyValue(3, (short)5);
        var a = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 10, mv);
        var b = new MyValue[10];
        System.arraycopy(a, 3, b, 1, 4);
        Asserts.assertNull(b[0]);
        for (int i = 1; i < 5; i++) {
            Asserts.assertEquals(b[i], mv);
        }
        for (int i = 5; i < 10; i++) {
            Asserts.assertNull(b[i]);
        }
    }

    public static void main(String[] args) {
        var test = new TestArrayCopy();
        Class c = test.getClass();
        ArrayList<String> failures = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().startsWith("test_")) {
                try {
                    System.out.println("Running " + m.getName());
                    m.invoke(null);
                } catch (Throwable t) {
                    t.printStackTrace();
                    failures.add(m.getName());
                }
            }
        }
        if (!failures.isEmpty()) {
            System.out.print("Failed tests: ");
            for (String s: failures) {
                System.out.print(s + " ");
            }
            System.out.println("");
            throw new RuntimeException("Some tests failed");
        }
    }
}
