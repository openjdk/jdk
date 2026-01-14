/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8246774
 * @summary Checks that the appropriate value is given to the canonical ctr
 * @library /test/lib
 * @run junit DifferentStreamFieldsTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import jdk.test.lib.serial.SerialObjectBuilder;
import static java.lang.System.out;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks that the appropriate value is given to the canonical ctr.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DifferentStreamFieldsTest {

    record R01(boolean x) implements Serializable {}

    record R02(byte x) implements Serializable {}

    record R03(short x) implements Serializable {}

    record R04(char x) implements Serializable {}

    record R05(int x) implements Serializable {}

    record R06(long x) implements Serializable {}

    record R07(float x) implements Serializable {}

    record R08(double x) implements Serializable {}

    record R09(Object x) implements Serializable {}

    record R10(String x) implements Serializable {}

    record R11(int[]x) implements Serializable {}

    record R12(Object[]x) implements Serializable {}

    record R13(R12 x) implements Serializable {}

    record R14(R13[]x) implements Serializable {}

    public Object[][] recordTypeAndExpectedValue() {
        return new Object[][]{
            new Object[]{R01.class, false},
            new Object[]{R02.class, (byte) 0},
            new Object[]{R03.class, (short) 0},
            new Object[]{R04.class, '\u0000'},
            new Object[]{R05.class, 0},
            new Object[]{R06.class, 0L},
            new Object[]{R07.class, 0.0f},
            new Object[]{R08.class, 0.0d},
            new Object[]{R09.class, null},
            new Object[]{R10.class, null},
            new Object[]{R11.class, null},
            new Object[]{R12.class, null},
            new Object[]{R13.class, null},
            new Object[]{R14.class, null}
        };
    }

    @ParameterizedTest
    @MethodSource("recordTypeAndExpectedValue")
    public void testWithDifferentTypes(Class<?> clazz, Object expectedXValue)
    throws Exception {
        out.println("\n---");
        assert clazz.isRecord();
        byte[] bytes = SerialObjectBuilder
            .newBuilder(clazz.getName())
            .build();

        Object obj = deserialize(bytes);
        out.println("deserialized: " + obj);
        Object actualXValue = clazz.getDeclaredMethod("x").invoke(obj);
        assertEquals(expectedXValue, actualXValue);

        bytes = SerialObjectBuilder
            .newBuilder(clazz.getName())
            .addPrimitiveField("y", int.class, 5)  // stream junk
            .build();

        obj = deserialize(bytes);
        out.println("deserialized: " + obj);
        actualXValue = clazz.getDeclaredMethod("x").invoke(obj);
        assertEquals(expectedXValue, actualXValue);
    }

    // --- all together

    @Test
    public void testWithAllTogether() throws Exception {
        out.println("\n---");
        record R15(boolean a, byte b, short c, char d, int e, long f, float g,
                   double h, Object i, String j, long[]k, Object[]l)
            implements Serializable {}

        byte[] bytes = SerialObjectBuilder
            .newBuilder(R15.class.getName())
            .addPrimitiveField("x", int.class, 5)  // stream junk
            .build();

        R15 obj = deserialize(bytes);
        out.println("deserialized: " + obj);
        assertEquals(false, obj.a);
        assertEquals(0, obj.b);
        assertEquals(0, obj.c);
        assertEquals('\u0000', obj.d);
        assertEquals(0, obj.e);
        assertEquals(0l, obj.f);
        assertEquals(0f, obj.g);
        assertEquals(0d, obj.h);
        assertEquals(null, obj.i);
        assertEquals(null, obj.j);
        assertEquals(null, obj.k);
        assertEquals(null, obj.l);
    }

    @Test
    public void testInt() throws Exception {
        out.println("\n---");
        {
            record R(int x) implements Serializable {}

            var r = new R(5);
            byte[] OOSBytes = serialize(r);

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())
                .addPrimitiveField("x", int.class, 5)
                .build();

            var deser1 = deserialize(OOSBytes);
            assertEquals(r, deser1);
            var deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);
        }
        {
            record R(int x, int y) implements Serializable {}

            var r = new R(7, 8);
            byte[] OOSBytes = serialize(r);
            var deser1 = deserialize(OOSBytes);
            assertEquals(r, deser1);

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())
                .addPrimitiveField("x", int.class, 7)
                .addPrimitiveField("y", int.class, 8)
                .build();

            var deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);

            builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())
                .addPrimitiveField("y", int.class, 8)  // reverse order
                .addPrimitiveField("x", int.class, 7)
                .build();
            deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);

            builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())
                .addPrimitiveField("w", int.class, 6) // additional fields
                .addPrimitiveField("x", int.class, 7)
                .addPrimitiveField("y", int.class, 8)
                .addPrimitiveField("z", int.class, 9) // additional fields
                .build();
            deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);

            r = new R(0, 0);
            OOSBytes = serialize(r);
            deser1 = deserialize(OOSBytes);
            assertEquals(r, deser1);

            builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())
                .addPrimitiveField("y", int.class, 0)
                .addPrimitiveField("x", int.class, 0)
                .build();
            deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);

            builderBytes = SerialObjectBuilder
                .newBuilder(R.class.getName())  // no field values
                .build();
            deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);
        }
    }

    @Test
    public void testString() throws Exception {
        out.println("\n---");

        record Str(String part1, String part2) implements Serializable {}

        var r = new Str("Hello", "World!");
        var deser1 = deserialize(serialize(r));
        assertEquals(r, deser1);

        byte[] builderBytes = SerialObjectBuilder
            .newBuilder(Str.class.getName())
            .addField("part1", String.class, "Hello")
            .addField("part2", String.class, "World!")
            .build();

        var deser2 = deserialize(builderBytes);
        assertEquals(deser1, deser2);

        builderBytes = SerialObjectBuilder
            .newBuilder(Str.class.getName())
            .addField("cruft", String.class, "gg")
            .addField("part1", String.class, "Hello")
            .addField("part2", String.class, "World!")
            .addPrimitiveField("x", int.class, 13)
            .build();

        var deser3 = deserialize(builderBytes);
        assertEquals(deser1, deser3);
    }

    @Test
    public void testArrays() throws Exception {
        out.println("\n---");
        {
            record IntArray(int[]ints, long[]longs) implements Serializable {}
            IntArray r = new IntArray(new int[]{5, 4, 3, 2, 1}, new long[]{9L});
            IntArray deser1 = deserialize(serialize(r));
            Assertions.assertArrayEquals(r.ints(), deser1.ints());
            Assertions.assertArrayEquals(r.longs(), deser1.longs());

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(IntArray.class.getName())
                .addField("ints", int[].class, new int[]{5, 4, 3, 2, 1})
                .addField("longs", long[].class, new long[]{9L})
                .build();

            IntArray deser2 = deserialize(builderBytes);
            Assertions.assertArrayEquals(deser1.ints(), deser2.ints());
            Assertions.assertArrayEquals(deser1.longs(), deser2.longs());
        }
        {
            record StrArray(String[]stringArray) implements Serializable {}
            StrArray r = new StrArray(new String[]{"foo", "bar"});
            StrArray deser1 = deserialize(serialize(r));
            Assertions.assertArrayEquals(r.stringArray(), deser1.stringArray());

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(StrArray.class.getName())
                .addField("stringArray", String[].class, new String[]{"foo", "bar"})
                .build();

            StrArray deser2 = deserialize(builderBytes);
            Assertions.assertArrayEquals(deser1.stringArray(), deser2.stringArray());
        }
    }

    @Test
    public void testCompatibleFieldTypeChange() throws Exception {
        out.println("\n---");

        {
            record NumberHolder(Number n) implements Serializable {}

            var r = new NumberHolder(123);
            var deser1 = deserialize(serialize(r));
            assertEquals(r, deser1);

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(NumberHolder.class.getName())
                .addField("n", Integer.class, 123)
                .build();

            var deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);
        }

        {
            record IntegerHolder(Integer i) implements Serializable {}

            var r = new IntegerHolder(123);
            var deser1 = deserialize(serialize(r));
            assertEquals(r, deser1);

            byte[] builderBytes = SerialObjectBuilder
                .newBuilder(IntegerHolder.class.getName())
                .addField("i", Number.class, 123)
                .build();

            var deser2 = deserialize(builderBytes);
            assertEquals(deser1, deser2);
        }
    }

    @Test
    public void testIncompatibleRefFieldTypeChange() throws Exception {
        out.println("\n---");

        record StringHolder(String s) implements Serializable {}

        var r = new StringHolder("123");
        var deser1 = deserialize(serialize(r));
        assertEquals(r, deser1);

        byte[] builderBytes = SerialObjectBuilder
            .newBuilder(StringHolder.class.getName())
            .addField("s", Integer.class, 123)
            .build();

        try {
            var deser2 = deserialize(builderBytes);
            throw new AssertionError(
                "Unexpected success of deserialization. Deserialized value: " + deser2);
        } catch (InvalidObjectException e) {
            // expected
        }
    }

    @Test
    public void testIncompatiblePrimitiveFieldTypeChange() throws Exception {
        out.println("\n---");

        record IntHolder(int i) implements Serializable {}

        var r = new IntHolder(123);
        var deser1 = deserialize(serialize(r));
        assertEquals(r, deser1);

        byte[] builderBytes = SerialObjectBuilder
            .newBuilder(IntHolder.class.getName())
            .addPrimitiveField("i", long.class, 123L)
            .build();

        try {
            var deser2 = deserialize(builderBytes);
            throw new AssertionError(
                "Unexpected success of deserialization. Deserialized value: " + deser2);
        } catch (InvalidClassException e) {
            // expected
        }
    }

    <T> byte[] serialize(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] streamBytes)
    throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }
}
