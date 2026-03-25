/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

/* @test
 * @summary test JNI functions with instances of value classes
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED -XX:+UseNullableValueFlattening runtime.valhalla.inlinetypes.InlineWithJni
 */


import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import jdk.test.lib.Asserts;

public value class InlineWithJni {

    static int returnValue = 999;
    static final int JNI_ERR = -1;  // from jni.h

    static {
        System.loadLibrary("InlineWithJni");
    }

    public static void main(String[] args) {
        testJniMonitorOps();
        testJniFieldAccess();
        testJniArrayAccess();
    }

    final int x;

    public InlineWithJni(int x) {
        this.x = x;
    }

    public native void doJniMonitorEnter();
    public native void doJniMonitorExit();

    public static void testJniMonitorOps() {
        boolean sawIe = false;
        boolean sawImse = false;
        try {
            new InlineWithJni(0).doJniMonitorEnter();
        } catch (IdentityException ie) {
            sawIe = true;
        }
        Asserts.assertTrue(sawIe, "Missing IdentityException");
        Asserts.assertEQ(returnValue, JNI_ERR);
        try {
            new InlineWithJni(0).doJniMonitorExit();
        } catch (IllegalMonitorStateException imse) {
            sawImse = true;
        }
        Asserts.assertTrue(sawImse, "Missing IllegalMonitorStateException");
    }

    public static native Object readInstanceField(Object obj, String name, String signature);
    public static native void writeInstanceField(Object obj, String name, String signature, Object value);

    public static native Object readArrayElement(Object[] array, int index);
    public static native void writeArrayElement(Object[] array, int index, Object value);


    static value class SmallValue {
        byte b;
        SmallValue() { b = 1; }
        SmallValue(byte b0) { b = b0; }
        static public SmallValue getValueA() { return new SmallValue((byte)42); }
        static public SmallValue getValueB() { return new SmallValue((byte)111); }
    }

    static value class MediumValue {
        int i0;
        int i1;
        MediumValue() {
            i0 = 2;
            i1 = 3;
        }
        MediumValue(int ia, int ib) {
            i0 = ia;
            i1 = ib;
        }
        static public MediumValue getValueA() { return new MediumValue(23, 64); }
        static public MediumValue getValueB() { return new MediumValue(-51, -1023); }
    }

    static value class BigValue {
        long l0;
        long l1;
        long l2;
        BigValue() {
            l0 = 4L;
            l1 = 5L;
            l2 = 6L;
        }
        BigValue(long la, long lb, long lc) {
            l0 = la;
            l1 = lb;
            l2 = lc;
        }
        static public BigValue getValueA() { return new BigValue(0L, 65525L, Long.MIN_VALUE); }
        static public BigValue getValueB() { return new BigValue(Long.MIN_VALUE, 32000L, 0L); }
    }

    static value class ValueWithOop {
        String s;
        byte b;
        ValueWithOop() {
            s = "Hello Duke!";
            b = (byte)7;
        }
        ValueWithOop(String s0, byte b0) {
            s = s0;
            b = b0;
        }
        static public ValueWithOop getValueA() { return new ValueWithOop("Bretagne", (byte)123); }
        static public ValueWithOop getValueB() { return new ValueWithOop("Alsace", (byte)-31); }
    }

    // Container with nullable fields (potentially flattened)
    static class Container0 {
        SmallValue sv = new SmallValue();
        MediumValue mv = new MediumValue();
        BigValue bv = new BigValue();
        ValueWithOop vwo = new ValueWithOop();
    }

    // Container with null-restricted fields (potentially flattened)
    static class Container1 {
        @NullRestricted
        SmallValue sv;
        @NullRestricted
        MediumValue mv;
        @NullRestricted
        BigValue bv;
        @NullRestricted
        ValueWithOop vwo;

        Container1() {
            sv = new SmallValue();
            mv = new MediumValue();
            bv = new BigValue();
            vwo = new ValueWithOop();
            super();
        }
    }

    static String getFieldSignature(Class c, String name) {
        try {
            return "L"+c.getDeclaredField(name).getType().getName().replaceAll("\\.", "/")+";";
        } catch(NoSuchFieldException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static void testJniFieldAccessHelper(Object c, boolean nullRestriction) {

        String smallSignature = getFieldSignature(c.getClass(), "sv");
        String mediumSignature = getFieldSignature(c.getClass(), "mv");
        String bigSignature = getFieldSignature(c.getClass(), "bv");
        String withOopSignature = getFieldSignature(c.getClass(), "vwo");


        // Reading nullable value fields
        SmallValue sv = (SmallValue)readInstanceField(c, "sv", smallSignature);
        Asserts.assertEQ(sv, new SmallValue());
        Asserts.assertTrue(sv.b == 1);
        MediumValue mv = (MediumValue)readInstanceField(c, "mv", mediumSignature);
        Asserts.assertEQ(mv, new MediumValue());
        Asserts.assertTrue(mv.i0 == 2);
        Asserts.assertTrue(mv.i1 == 3);
        BigValue bv = (BigValue)readInstanceField(c, "bv", bigSignature);
        Asserts.assertEQ(bv, new BigValue());
        Asserts.assertTrue(bv.l0 == 4);
        Asserts.assertTrue(bv.l1 == 5);
        Asserts.assertTrue(bv.l2 == 6);
        ValueWithOop vwo = (ValueWithOop)readInstanceField(c, "vwo", withOopSignature);
        Asserts.assertEQ(vwo, new ValueWithOop());
        Asserts.assertTrue(vwo.s.equals("Hello Duke!"));
        Asserts.assertTrue(vwo.b == 7);


        // Writing non-null value to nullable field
        SmallValue nsv = new SmallValue((byte)8);
        writeInstanceField(c, "sv", smallSignature, nsv);
        sv = (SmallValue)readInstanceField(c, "sv", smallSignature);
        Asserts.assertTrue(sv == nsv);
        MediumValue nmv = new MediumValue(9, 10);
        writeInstanceField(c, "mv", mediumSignature, nmv);
        mv = (MediumValue)readInstanceField(c, "mv", mediumSignature);
        Asserts.assertTrue(mv == nmv);
        BigValue nbv = new BigValue(11L, 12L, 13L);
        writeInstanceField(c, "bv", bigSignature, nbv);
        bv = (BigValue)readInstanceField(c, "bv", bigSignature);
        Asserts.assertTrue(bv == nbv);
        ValueWithOop nvwo = new ValueWithOop("Bye Duke!", (byte)14);
        writeInstanceField(c, "vwo", withOopSignature, nvwo);
        vwo = (ValueWithOop)readInstanceField(c, "vwo", withOopSignature);
        Asserts.assertTrue(vwo == nvwo);


        // Writing null to nullable field
        Exception ex = null;
        try {
            writeInstanceField(c, "sv", smallSignature, null);
            sv = (SmallValue)readInstanceField(c, "sv", smallSignature);
            Asserts.assertTrue(sv == null);
        } catch(NullPointerException npe) {
            ex = npe;
        }
        Asserts.assertTrue((nullRestriction && ex != null) || (!nullRestriction && ex == null));
        ex = null;
        try {
            writeInstanceField(c, "mv", mediumSignature, null);
            mv = (MediumValue)readInstanceField(c, "mv", mediumSignature);
            Asserts.assertTrue(mv == null);
         } catch(NullPointerException npe) {
            ex = npe;
        }
        System.out.println(ex + " / " + nullRestriction);
        Asserts.assertTrue((nullRestriction && ex != null) || (!nullRestriction && ex == null));
        ex = null;
        try {
            writeInstanceField(c, "bv", bigSignature, null);
            bv = (BigValue)readInstanceField(c, "bv", bigSignature);
            Asserts.assertTrue(bv == null);
        } catch(NullPointerException npe) {
            ex = npe;
        }
        Asserts.assertTrue((nullRestriction && ex != null) || (!nullRestriction && ex == null));
        ex = null;
        try {
            writeInstanceField(c, "vwo", withOopSignature, null);
            vwo = (ValueWithOop)readInstanceField(c, "vwo", withOopSignature);
            Asserts.assertTrue(vwo == null);
        } catch(NullPointerException npe) {
            ex = npe;
        }
        Asserts.assertTrue((nullRestriction && ex != null) || (!nullRestriction && ex == null));
    }

    static void testJniFieldAccess() {
        // Reading nullable field
        try {
            Container0 c0 = new Container0();
            testJniFieldAccessHelper(c0, false);
            Container1 c1 = new Container1();
            testJniFieldAccessHelper(c1, true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
    }

    static void testJniArrayAccessHelper(Object[] array, boolean nullRestriction) {
        Object valueA = getValueA(array.getClass().getComponentType());
        Object valueB = getValueB(array.getClass().getComponentType());
        int length = array.length;

        // Reading elements
        for (int i = 0; i < length; i++) {
            Object element = readArrayElement(array, i);
            Asserts.assertTrue(element == valueA);
        }

        // Writing elements
        for (int i = 0; i < length; i++) {
            writeArrayElement(array, i, valueB);
        }
        for (int i = 0; i < length; i++) {
            Object element = readArrayElement(array, i);
            Asserts.assertTrue(element == valueB);
        }

        // Writing null
        for (int i = 0; i < length; i++) {
            Exception ex = null;
            try {
                writeArrayElement(array, i, null);
                Object element = readArrayElement(array, i);
                Asserts.assertTrue(element == null);
            } catch(NullPointerException npe) {
                ex = npe;
            }
            Asserts.assertTrue((nullRestriction && ex != null) || (!nullRestriction && ex == null));
        }
    }

    static Object getValueA(Class c) {
        try {
            Method mA = c.getMethod("getValueA");
            return mA.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static Object getValueB(Class c) {
        try {
            Method mB = c.getMethod("getValueB");
            return mB.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static void fillArrayWithValueA(Object[] array) {
        Object valueA = getValueA(array.getClass().getComponentType());
        for (int i = 0; i < array.length; i++) {
            array[i] = valueA;
        }
    }

    static void testJniArrayAccessHelper2(Class c) {

        Object[] array0 = (Object[])Array.newInstance(c, 10);
        fillArrayWithValueA(array0);
        testJniArrayAccessHelper(array0, false);

        Object[] array1 = ValueClass.newNullableAtomicArray(c, 31);
        fillArrayWithValueA(array1);
        testJniArrayAccessHelper(array1, false);

        Object[] array2 = ValueClass.newNullRestrictedAtomicArray(c, 127, getValueA(c));
        fillArrayWithValueA(array2);
        testJniArrayAccessHelper(array2, true);
    }

    static void testJniArrayAccess() {
        testJniArrayAccessHelper2(SmallValue.class);
        testJniArrayAccessHelper2(MediumValue.class);
        testJniArrayAccessHelper2(BigValue.class);
        testJniArrayAccessHelper2(ValueWithOop.class);

    }
}
