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



/*
 * @test UnsafeTest
 * @requires vm.debug == true
 * @summary unsafe get/put/with inline type
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.value
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @requires vm.flagless
 * @compile Point.java UnsafeTest.java
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions
                     -XX:+UseNullableAtomicValueFlattening -XX:+UseArrayFlattening -XX:+UseFieldFlattening
                     -XX:+PrintInlineLayout -Xlog:valuetypes=trace runtime.valhalla.inlinetypes.UnsafeTest
 */

// TODO 8350865 Implement unsafe intrinsics for nullable flat fields/arrays in C2

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

import java.lang.reflect.*;
import java.util.function.*;
import java.util.List;
import static jdk.test.lib.Asserts.*;

public class UnsafeTest {
    static final Unsafe U = Unsafe.getUnsafe();

    @LooselyConsistentValue
    static value class Value1 {
        @NullRestricted
        Point point;
        Point[] array;
        Value1(Point p, Point... points) {
            this.point = p;
            this.array = points;
        }
    }

    @LooselyConsistentValue
    static value class Value2 {
        int i;
        @NullRestricted
        Value1 v1;

        Value2(Value1 v, int i) {
            this.v1 = v;
            this.i = i;
        }
    }

    @LooselyConsistentValue
    static value class Value3 {
        Object o;
        @NullRestricted
        Value2 v2;

        Value3(Value2 v, Object ref) {
            this.v2 = v;
            this.o = ref;
        }

    }

    public static void test0() throws Throwable {
        printValueClass(Value3.class, 0);

        Value1 v1 = new Value1(new Point(10,10), new Point(20,20), new Point(30,30));
        Value2 v2 = new Value2(v1, 20);
        Value3 v3 = new Value3(v2, List.of("Value3"));
        long off_o = U.objectFieldOffset(Value3.class, "o");
        long off_v2 = U.objectFieldOffset(Value3.class, "v2");
        long off_i = U.objectFieldOffset(Value2.class, "i");
        long off_v1 = U.objectFieldOffset(Value2.class, "v1");

        long off_point = U.objectFieldOffset(Value1.class, "point");
        int layout_point = U.fieldLayout(Value1.class.getDeclaredField("point"));

        List<String> list = List.of("Value1", "Value2", "Value3");
        Value3 v;
        Value3[] array = (Value3[]) ValueClass.newNullRestrictedNonAtomicArray(Value3.class, 1, v3);
        try {
            long baseOff = U.arrayInstanceBaseOffset(array) - U.valueHeaderSize(Value3.class);
            // patch v3.o
            U.putReference(array, baseOff + off_o, list);
            // patch v3.v2.i;
            U.putInt(array, baseOff + off_v2 + off_i - U.valueHeaderSize(Value2.class), 999);
            // patch v3.v2.v1.point
            U.putFlatValue(array, baseOff + off_v2 + off_v1 - U.valueHeaderSize(Value2.class) + off_point - U.valueHeaderSize(Value1.class),
                           layout_point, Point.class, new Point(100, 100));
        } finally {
            v = array[0];
        }

        assertEquals(v.v2.v1.point, new Point(100, 100));
        assertEquals(v.v2.i, 999);
        assertEquals(v.o, list);
        assertEquals(v.v2.v1.array, v1.array);

        Value1 nv1 = new Value1(new Point(70,70), new Point(80,80), new Point(90,90));
        Value2 nv2 = new Value2(nv1, 100);
        Value3 nv3 = new Value3(nv2, list);

        int layout_v2 = U.fieldLayout(Value3.class.getDeclaredField("v2"));

        array = (Value3[]) ValueClass.newNullRestrictedNonAtomicArray(Value3.class, 1, v);
        try {
            long baseOff = U.arrayInstanceBaseOffset(array) - U.valueHeaderSize(Value3.class);
            // patch v3.v2
            U.putFlatValue(array, baseOff + off_v2, layout_v2, Value2.class, nv2);
        } finally {
            v = array[0];
        }
        assertEquals(v, nv3);
    }

    static void printValueClass(Class<?> vc, int level) {
        String indent = "";
        for (int i=0; i < level; i++) {
            indent += "  ";
        }
        System.out.format("%s%s header size %d%n", indent, vc, U.valueHeaderSize(vc));
        for (Field f : vc.getDeclaredFields()) {
            System.out.format("%s%s: %s%s offset %d%n", indent, f.getName(),
                              U.isFlatField(f) ? "flattened " : "", f.getType(),
                              U.objectFieldOffset(vc, f.getName()));
            if (U.isFlatField(f)) {
                printValueClass(f.getType(), level+1);
            }
        }
    }

    // Requires -XX:+UseNullableAtomicValueFlattening
    static value class MyValue0 {
        int val;

        public MyValue0(int i) {
            val = i;
        }
    }

    static class Container0 {
        MyValue0 v;
    }

    public static void test1() throws Throwable {
        Container0 c = new Container0();
        Class<?> cc = Container0.class;
        Field[] fields = cc.getDeclaredFields();
        Asserts.assertEquals(fields.length, 1);
        Field f = fields[0];
        System.out.println("Field found: " + f);
        Asserts.assertTrue(U.isFlatField(f));
        Asserts.assertTrue(U.hasNullMarker(f));
        int nmOffset = U.nullMarkerOffset(f);
        Asserts.assertNotEquals(nmOffset, -1);
        byte nm = U.getByte(c, nmOffset);
        Asserts.assertEquals(nm, (byte)0);
        c.v = new MyValue0(42);
        Asserts.assertNotNull(c.v);
        nm = U.getByte(c, nmOffset);
        Asserts.assertNotEquals(nm, 0);
        U.getAndSetByteRelease(c, nmOffset, (byte)0);
        Asserts.assertNull(c.v);
    }

    static value record E() {}

    static value record B(byte b) {}

    static value record BC(@NullRestricted B b) {}

    static value record BBC(@NullRestricted B b1, @NullRestricted B b2) {}

    static value record BBCEBCC(@NullRestricted BBC bbc, @NullRestricted E e, @NullRestricted BC bc) {}

    public static void test2() throws Throwable {
        printValueClass(BBCEBCC.class, 0);

        final B b1_def = new B((byte)0);
        final B b2_def = new B((byte)0);
        final BBC bbc_def = new BBC(b1_def, b2_def);
        final E e_def = new E();
        final B b3_def = new B((byte)0);
        final BC bc_def = new BC(b3_def);
        final BBCEBCC bbcebcc_def = new BBCEBCC(bbc_def, e_def, bc_def);

        final B b1 = new B((byte)1);
        final B b2 = new B((byte)2);
        final BBC bbc = new BBC(b1, b2);
        final E e = new E();
        final B b3 = new B((byte)3);
        final BC bc = new BC(b3);
        final BBCEBCC bbcebcc = new BBCEBCC(bbc, e, bc);

        final long bc_header_size = U.valueHeaderSize(BC.class);
        final long bc_b_payload_offset = U.objectFieldOffset(BC.class, "b") - bc_header_size;
        final int bc_b_layout = U.fieldLayout(BC.class.getDeclaredField("b"));

        final long bbc_header_size = U.valueHeaderSize(BBC.class);
        final long bbc_b1_payload_offset = U.objectFieldOffset(BBC.class, "b1") - bbc_header_size;
        final long bbc_b2_payload_offset = U.objectFieldOffset(BBC.class, "b2") - bbc_header_size;
        final int bbc_b1_layout = U.fieldLayout(BBC.class.getDeclaredField("b1"));
        final int bbc_b2_layout = U.fieldLayout(BBC.class.getDeclaredField("b2"));

        final long bbcebcc_header_size = U.valueHeaderSize(BBCEBCC.class);
        final long bbcebcc_bbc_payload_offset = U.objectFieldOffset(BBCEBCC.class, "bbc") - bbcebcc_header_size;
        final long bbcebcc_e_payload_offset = U.objectFieldOffset(BBCEBCC.class, "e") - bbcebcc_header_size;
        final long bbcebcc_bc_payload_offset = U.objectFieldOffset(BBCEBCC.class, "bc") - bbcebcc_header_size;
        final int bbcebcc_bbc_layout = U.fieldLayout(BBCEBCC.class.getDeclaredField("bbc"));
        final int bbcebcc_e_layout = U.fieldLayout(BBCEBCC.class.getDeclaredField("e"));
        final int bbcebcc_bc_layout = U.fieldLayout(BBCEBCC.class.getDeclaredField("bc"));

        final Consumer<BBCEBCC> assertEqualDef = (value) -> {
            assertEquals(value, bbcebcc_def);
            assertEquals(value.bbc, bbc_def);
            assertEquals(value.bbc.b1, b1_def);
            assertEquals(value.bbc.b1.b, (byte)0);
            assertEquals(value.bbc.b2, b2_def);
            assertEquals(value.bbc.b2.b, (byte)0);
            assertEquals(value.e, e_def);
            assertEquals(value.bc, bc_def);
            assertEquals(value.bc.b, b3_def);
            assertEquals(value.bc.b.b, (byte)0);
        };

        final Consumer<BBCEBCC> assertEqualNew = (value) -> {
            assertEquals(value, bbcebcc);
            assertEquals(value.bbc, bbc);
            assertEquals(value.bbc.b1, b1);
            assertEquals(value.bbc.b1.b, (byte)1);
            assertEquals(value.bbc.b2, b2);
            assertEquals(value.bbc.b2.b, (byte)2);
            assertEquals(value.e, e);
            assertEquals(value.bc, bc);
            assertEquals(value.bc.b, b3);
            assertEquals(value.bc.b.b, (byte)3);
        };

        final Supplier<BBCEBCC[]> getConstructionArray = () -> {
            BBCEBCC[] array = (BBCEBCC[]) ValueClass.newNullRestrictedNonAtomicArray(BBCEBCC.class, 1, bbcebcc_def);
            assertTrue(ValueClass.isFlatArray(array));
            return array;
        };

        {
            final BBCEBCC[] array = getConstructionArray.get();

            assertEqualDef.accept(array[0]);

            final long base_offset = U.arrayInstanceBaseOffset(array);
            final int array_layout = U.arrayLayout(array);
            // patch array[0]
            U.putFlatValue(array, base_offset, array_layout, BBCEBCC.class, bbcebcc);

            assertEqualNew.accept(array[0]);
        }

        {
            final BBCEBCC[] array = getConstructionArray.get();

            assertEqualDef.accept(array[0]);

            final long base_offset = U.arrayInstanceBaseOffset(array);

            // patch BBCEBCC.bbc
            final long array_bbc_offset = base_offset + bbcebcc_bbc_payload_offset;
            U.putFlatValue(array, array_bbc_offset, bbcebcc_bbc_layout, BBC.class, bbc);
            // patch BBCEBCC.e
            final long array_e_offset = base_offset + bbcebcc_e_payload_offset;
            U.putFlatValue(array, array_e_offset, bbcebcc_e_layout, E.class, e);
            // patch BBCEBCC.bc
            final long array_bc_offset = base_offset + bbcebcc_bc_payload_offset;
            U.putFlatValue(array, array_bc_offset, bbcebcc_bc_layout, BC.class, bc);

            assertEqualNew.accept(array[0]);
        }

        {
            final BBCEBCC[] array = getConstructionArray.get();

            assertEqualDef.accept(array[0]);

            final long base_offset = U.arrayInstanceBaseOffset(array);

            final long array_bbc_offset = base_offset + bbcebcc_bbc_payload_offset;
            // patch BBCEBCC.bbc.b1
            final long array_bbc_b1_offset = array_bbc_offset + bbc_b1_payload_offset;
            U.putFlatValue(array, array_bbc_b1_offset, bbc_b1_layout, B.class, b1);
            // patch BBCEBCC.bbc.b2
            final long array_bbc_b2_offset = array_bbc_offset + bbc_b2_payload_offset;
            U.putFlatValue(array, array_bbc_b2_offset, bbc_b2_layout, B.class, b2);
            // patch BBCEBCC.e
            final long array_e_offset = base_offset + bbcebcc_e_payload_offset;
            U.putFlatValue(array, array_e_offset, bbcebcc_e_layout, E.class, e);
            // patch BBCEBCC.bc.b
            final long array_bc_offset = base_offset + bbcebcc_bc_payload_offset;
            final long array_bc_b_offset = array_bc_offset + bc_b_payload_offset;
            U.putFlatValue(array, array_bc_b_offset, bc_b_layout, B.class, b3);

            assertEqualNew.accept(array[0]);
        }
    }

    static value class TestValue1  {
        short s0,s1;

        TestValue1() {
            s0 = 0;
            s1 = 0;
        }

        TestValue1(short v0, short v1) {
            s0 = v0;
            s1 = v1;
        }
    }

    static class Container1 {
        TestValue1 value;
    }

    // Testing of nullable flat field supports in Unsafe.getFlatValue()/Unsafe.putFlatValue()
    public static void testNullableFlatFields() throws Throwable {
        Container1 c = new Container1();
        Class<?> cc = Container1.class;
        Field field = cc.getDeclaredField("value");
        Class<?> fc = TestValue1.class;
        long offset = U.objectFieldOffset(field);
        int layoutKind = U.fieldLayout(field);
        if (!U.isFlatField(field)) return; // Field not flattened (due to VM flags?), test doesn't apply
        // Initial value of the field must be null
        Asserts.assertNull(U.getFlatValue(c, offset, layoutKind, fc));
        // Writing all zero value to the field, field must become non-null
        TestValue1 val0 = new TestValue1((short)0, (short)0);
        U.putFlatValue(c, offset, layoutKind, fc, val0);
        TestValue1 rval = U.getFlatValue(c, offset, layoutKind, fc);
        Asserts.assertNotNull(rval);
        Asserts.assertEQ((short)0, rval.s0);
        Asserts.assertEQ((short)0, rval.s1);
        Asserts.assertEQ((short)0, c.value.s0);
        Asserts.assertEQ((short)0, c.value.s1);
        // Writing null to the field, field must become null again
        U.putFlatValue(c, offset, layoutKind, fc, null);
        Asserts.assertNull(U.getFlatValue(c, offset, layoutKind, fc));
        Asserts.assertNull(c.value);
        // Writing non zero value to the field
        TestValue1 val1 = new TestValue1((short)-1, (short)-2);
        U.putFlatValue(c, offset, layoutKind, fc, val1);
        rval = U.getFlatValue(c, offset, layoutKind, fc);
        Asserts.assertNotNull(rval);
        Asserts.assertNotNull(c.value);
        Asserts.assertEQ((short)-1, rval.s0);
        Asserts.assertEQ((short)-2, rval.s1);
        Asserts.assertEQ((short)-1, c.value.s0);
        Asserts.assertEQ((short)-2, c.value.s1);
        // Writing a different non zero value
        TestValue1 val2 = new TestValue1((short)Short.MAX_VALUE, (short)3);
        U.putFlatValue(c, offset, layoutKind, fc, val2);
        rval = U.getFlatValue(c, offset, layoutKind, fc);
        Asserts.assertNotNull(rval);
        Asserts.assertNotNull(c.value);
        Asserts.assertEQ(Short.MAX_VALUE, c.value.s0);
        Asserts.assertEQ((short)3, rval.s1);
        Asserts.assertEQ(Short.MAX_VALUE, c.value.s0);
        Asserts.assertEQ((short)3, rval.s1);
    }

    // Testing of nullable flat arrays supports in Unsafe.getFlatValue()/Unsafe.putFlatValue()
    public static void testNullableFlatArrays() throws Throwable {
        final int ARRAY_LENGTH = 10;
        TestValue1[] array = (TestValue1[])ValueClass.newNullableAtomicArray(TestValue1.class, ARRAY_LENGTH);
        long baseOffset = U.arrayInstanceBaseOffset(array);
        int scaleIndex = U.arrayInstanceIndexScale(array);
        int layoutKind = U.arrayLayout(array);
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            Asserts.assertNull(U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class));
        }
        TestValue1 val = new TestValue1((short)0, (short)0);
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (i % 2 == 0) {
                U.putFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class, val );
            }
        }
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (i % 2 == 0) {
                Asserts.assertNotNull(U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class));
                Asserts.assertNotNull(array[i]);
            } else {
                Asserts.assertNull(U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class));
                Asserts.assertNull(array[i]);
            }
        }
        TestValue1 val2 = new TestValue1((short)Short.MAX_VALUE, (short)Short.MIN_VALUE);
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (i % 2 != 0) {
                U.putFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class, val2 );
            } else {
                U.putFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class, null );
            }
        }
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            if (i % 2 != 0) {
                TestValue1 rval = U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class);
                Asserts.assertNotNull(rval);
                Asserts.assertEQ(val2.s0, rval.s0);
                Asserts.assertEQ(val2.s1, rval.s1);
                Asserts.assertNotNull(array[i]);
                Asserts.assertEQ(val2.s0, array[i].s0);
                Asserts.assertEQ(val2.s1, array[i].s1);
            } else {
                Asserts.assertNull(U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class));
                Asserts.assertNull(array[i]);
            }
        }
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            U.putFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class, null );
        }
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            Asserts.assertNull(U.getFlatValue(array, baseOffset + i * scaleIndex, layoutKind, TestValue1.class));
            Asserts.assertNull(array[i]);
        }
    }

    public static void main(String[] args) throws Throwable {
        test0();
        test1();
        test2();
        testNullableFlatFields();
        testNullableFlatArrays();
    }

}
