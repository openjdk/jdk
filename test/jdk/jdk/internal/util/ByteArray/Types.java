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

/*
 * @test
 * @bug 8299576
 * @library /test/lib
 * @modules java.base/jdk.internal.util
 * @summary Basic contracts for ByteArray types and functionalities
 * @run junit Types
 */

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import jdk.internal.util.ByteArray;
import jdk.test.lib.RandomFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

final class Types {

    interface OrderedReader<T> {
        T get(byte[] array, int index, boolean big);
    }

    interface Reader<T> {
        T get(byte[] array, int index);
    }

    record ReadCase<T>(String name, OrderedReader<T> orderedReader, Reader<T> beReader, Reader<T> leReader, int bytesCount, UnaryOperator<T> reverse, Comparator<T> comparator) {}

    static Stream<ReadCase<?>> readCases() {
        return Stream.of(
                new ReadCase<>("char", ByteArray::getCharBO, ByteArray::getCharBE, ByteArray::getCharLE, Character.BYTES, Character::reverseBytes, Comparator.naturalOrder()),
                new ReadCase<>("short", ByteArray::getShortBO, ByteArray::getShortBE, ByteArray::getShortLE, Short.BYTES, Short::reverseBytes, Comparator.naturalOrder()),
                new ReadCase<>("u2", ByteArray::getUnsignedShortBO, ByteArray::getUnsignedShortBE, ByteArray::getUnsignedShortLE, 2, u2 -> ((u2 >> Byte.SIZE) & 0xFF) | ((u2 << Byte.SIZE) & 0xFF00), Comparator.naturalOrder()),
                new ReadCase<>("int", ByteArray::getIntBO, ByteArray::getIntBE, ByteArray::getIntLE, Integer.BYTES, Integer::reverseBytes, Comparator.naturalOrder()),
                new ReadCase<>("float", ByteArray::getFloatBO, ByteArray::getFloatBE, ByteArray::getFloatLE, Float.BYTES, null, Comparator.comparing(Float::floatToRawIntBits)),
                new ReadCase<>("long", ByteArray::getLongBO, ByteArray::getLongBE, ByteArray::getLongLE, Long.BYTES, Long::reverseBytes, Comparator.naturalOrder()),
                new ReadCase<>("double", ByteArray::getDoubleBO, ByteArray::getDoubleBE, ByteArray::getDoubleLE, Double.BYTES, null, Comparator.comparing(Double::doubleToRawLongBits))
        );
    }

    @ParameterizedTest
    @MethodSource("readCases")
    <T> void testReadType(ReadCase<T> in) {
        OrderedReader<T> orderedReader = in.orderedReader;
        Reader<T> beReader = in.beReader;
        Reader<T> leReader = in.leReader;
        int size = in.bytesCount;
        UnaryOperator<T> reverse = in.reverse;
        Comparator<T> comparator = in.comparator;
        int arrayLen = 128;
        byte[] arr = new byte[arrayLen];

        assertThrows(NullPointerException.class, () -> orderedReader.get(null, 0, true));
        assertThrows(NullPointerException.class, () -> beReader.get(null, 0));
        assertThrows(NullPointerException.class, () -> leReader.get(null, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> orderedReader.get(arr, -1, false));
        assertThrows(IndexOutOfBoundsException.class, () -> beReader.get(arr, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> leReader.get(arr, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> orderedReader.get(arr, arrayLen - size + 1, false));
        assertThrows(IndexOutOfBoundsException.class, () -> beReader.get(arr, arrayLen - size + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> leReader.get(arr, arrayLen - size + 1));

        Random random = RandomFactory.getRandom();
        random.nextBytes(arr);
        for (int index = 0; index + size <= arrayLen; index += random.nextInt(1, 33)) {
            T be = beReader.get(arr, index);
            T le = leReader.get(arr, index);
            T beO = orderedReader.get(arr, index, true);
            T leO = orderedReader.get(arr, index, false);
            assertEquals(0, comparator.compare(be, beO));
            assertEquals(0, comparator.compare(le, leO));
            if (reverse != null) {
                assertEquals(0, comparator.compare(reverse.apply(be), le));
            }
        }
    }

    interface OrderedWriter<T> {
        void set(byte[] array, int index, boolean big, T value);
    }

    interface Writer<T> {
        void set(byte[] array, int index, T value);
    }

    record WriteCase<T>(String name, OrderedWriter<T> orderedWriter, Writer<T> beWriter, Writer<T> leWriter, int bytesCount, List<T> equalValues) {}

    static Stream<WriteCase<?>> writeCases() {
        return Stream.of(
                new WriteCase<>("char", ByteArray::setCharBO, ByteArray::setCharBE, ByteArray::setCharLE, Character.BYTES, List.of('e')),
                new WriteCase<>("short", ByteArray::setShortBO, ByteArray::setShortBE, ByteArray::setShortLE, Short.BYTES, List.of((short) 56)),
                new WriteCase<>("u2", ByteArray::setUnsignedShortBO, ByteArray::setUnsignedShortBE, ByteArray::setUnsignedShortLE, 2, List.of(32768, -32768)),
                new WriteCase<>("int", ByteArray::setIntBO, ByteArray::setIntBE, ByteArray::setIntLE, Integer.BYTES, List.of(42)),
                new WriteCase<>("float", ByteArray::setFloatBO, ByteArray::setFloatBE, ByteArray::setFloatLE, Float.BYTES, List.of(Float.NaN, Float.intBitsToFloat(0x7FF23847))),
                new WriteCase<>("float raw", ByteArray::setFloatRawBO, ByteArray::setFloatRawBE, ByteArray::setFloatRawLE, Float.BYTES, List.of(1.0F)),
                new WriteCase<>("long", ByteArray::setLongBO, ByteArray::setLongBE, ByteArray::setLongLE, Long.BYTES, List.of(233748579238L)),
                new WriteCase<>("double", ByteArray::setDoubleBO, ByteArray::setDoubleBE, ByteArray::setDoubleLE, Double.BYTES, List.of(Double.NaN, Double.longBitsToDouble(0x7FFF_FFFF_0000_FFFFL))),
                new WriteCase<>("double raw", ByteArray::setDoubleRawBO, ByteArray::setDoubleRawBE, ByteArray::setDoubleRawLE, Double.BYTES, List.of(1.1D + 2.3D))
        );
    }

    @ParameterizedTest
    @MethodSource("writeCases")
    <T> void testWriteType(WriteCase<T> in) {
        OrderedWriter<T> orderedWriter = in.orderedWriter;
        Writer<T> beWriter = in.beWriter;
        Writer<T> leWriter = in.leWriter;
        int size = in.bytesCount;
        T value = in.equalValues.getFirst();
        int arrayLen = 25;
        byte[] arr = new byte[arrayLen];

        assertThrows(NullPointerException.class, () -> orderedWriter.set(null, 0, true, value));
        assertThrows(NullPointerException.class, () -> beWriter.set(null, 0, value));
        assertThrows(NullPointerException.class, () -> leWriter.set(null, 0, value));
        assertThrows(IndexOutOfBoundsException.class, () -> orderedWriter.set(arr, -1, false, value));
        assertThrows(IndexOutOfBoundsException.class, () -> beWriter.set(arr, -1, value));
        assertThrows(IndexOutOfBoundsException.class, () -> leWriter.set(arr, -1, value));
        assertThrows(IndexOutOfBoundsException.class, () -> orderedWriter.set(arr, arrayLen - size + 1, false, value));
        assertThrows(IndexOutOfBoundsException.class, () -> beWriter.set(arr, arrayLen - size + 1, value));
        assertThrows(IndexOutOfBoundsException.class, () -> leWriter.set(arr, arrayLen - size + 1, value));

        int index = 0;
        var arrBe = arr.clone();
        var arrLe = arr.clone();
        var arrBeO = arr.clone();
        var arrLeO = arr.clone();
        beWriter.set(arrBe, index, value);
        leWriter.set(arrLe, index, value);
        orderedWriter.set(arrBeO, index, true, value);
        orderedWriter.set(arrLeO, index, false, value);

        assertArrayEquals(arrBe, arrBeO);
        assertArrayEquals(arrLe, arrLeO);
        var arrBeR = arrBe.clone();
        reverseRegion(arrBeR, index, size);
        assertArrayEquals(arrLe, arrBeR);

        for (int i = 1; i < in.equalValues.size(); i++) {
            T v1 = in.equalValues.get(i);

            var arrBe1 = arr.clone();
            beWriter.set(arrBe1, index, v1);
            assertArrayEquals(arrBe, arrBe1);
        }
    }

    private static void reverseRegion(byte[] arr, int start, int size) {
        for (int i = start, j = start + size - 1; i < j; i++, j--) {
            byte t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
        }
    }
}
