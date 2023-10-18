/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestMemoryDereference
 */

import java.lang.foreign.MemorySegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.lang.foreign.ValueLayout;
import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class TestMemoryDereference {

    static class Accessor<X> {

        interface SegmentGetter<X> {
            X get(MemorySegment segment);
        }

        interface SegmentSetter<X> {
            void set(MemorySegment segment, X o);
        }

        interface BufferGetter<X> {
            X get(ByteBuffer segment);
        }

        interface BufferSetter<X> {
            void set(ByteBuffer buffer, X o);
        }

        final X value;
        final SegmentGetter<X> segmentGetter;
        final SegmentSetter<X> segmentSetter;
        final BufferGetter<X> bufferGetter;
        final BufferSetter<X> bufferSetter;

        Accessor(X value,
                 SegmentGetter<X> segmentGetter, SegmentSetter<X> segmentSetter,
                 BufferGetter<X> bufferGetter, BufferSetter<X> bufferSetter) {
            this.value = value;
            this.segmentGetter = segmentGetter;
            this.segmentSetter = segmentSetter;
            this.bufferGetter = bufferGetter;
            this.bufferSetter = bufferSetter;
        }

        void test() {
            MemorySegment segment = MemorySegment.ofArray(new byte[32]);
            ByteBuffer buffer = segment.asByteBuffer();
            segmentSetter.set(segment, value);
            assertEquals(bufferGetter.get(buffer), value);
            bufferSetter.set(buffer, value);
            assertEquals(value, segmentGetter.get(segment));
        }

        <Z> Accessor<Z> of(Z value,
                           SegmentGetter<Z> segmentGetter, SegmentSetter<Z> segmentSetter,
                           BufferGetter<Z> bufferGetter, BufferSetter<Z> bufferSetter) {
            return new Accessor<>(value, segmentGetter, segmentSetter, bufferGetter, bufferSetter);
        }
    }

    @Test(dataProvider = "accessors")
    public void testMemoryAccess(String testName, Accessor<?> accessor) {
        accessor.test();
    }

    static final ByteOrder BE = ByteOrder.BIG_ENDIAN;
    static final ByteOrder LE = ByteOrder.LITTLE_ENDIAN;
    static final ByteOrder NE = ByteOrder.nativeOrder();

    @DataProvider(name = "accessors")
    static Object[][] accessors() {
        return new Object[][]{

                // byte, offset
                {"byte/offset", new Accessor<>((byte) 42,
                        s -> s.get(JAVA_BYTE, 8), (s, x) -> s.set(JAVA_BYTE, 8, x),
                        (bb) -> bb.get(8), (bb, v) -> bb.put(8, v))
                },
                // bool, offset
                {"bool", new Accessor<>(false,
                        s -> s.get(JAVA_BOOLEAN, 8), (s, x) -> s.set(JAVA_BOOLEAN, 8, x),
                        (bb) -> bb.get(8) != 0, (bb, v) -> bb.put(8, v ? (byte)1 : (byte)0))
                },
                // char, offset
                {"char/offset", new Accessor<>((char) 42,
                        s -> s.get(JAVA_CHAR_UNALIGNED, 8), (s, x) -> s.set(JAVA_CHAR_UNALIGNED, 8, x),
                        (bb) -> bb.order(NE).getChar(8), (bb, v) -> bb.order(NE).putChar(8, v))
                },
                {"char/offset/LE", new Accessor<>((char) 42,
                        s -> s.get(JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8, x),
                        (bb) -> bb.order(LE).getChar(8), (bb, v) -> bb.order(LE).putChar(8, v))
                },
                {"char/offset/BE", new Accessor<>((char) 42,
                        s -> s.get(JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, x),
                        (bb) -> bb.order(BE).getChar(8), (bb, v) -> bb.order(BE).putChar(8, v))
                },
                // short, offset
                {"short/offset", new Accessor<>((short) 42,
                        s -> s.get(JAVA_SHORT_UNALIGNED, 8), (s, x) -> s.set(JAVA_SHORT_UNALIGNED, 8, x),
                        (bb) -> bb.order(NE).getShort(8), (bb, v) -> bb.order(NE).putShort(8, v))
                },
                {"short/offset/LE", new Accessor<>((short) 42,
                        s -> s.get(JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8, x),
                        (bb) -> bb.order(LE).getShort(8), (bb, v) -> bb.order(LE).putShort(8, v))
                },
                {"short/offset/BE", new Accessor<>((short) 42,
                        s -> s.get(JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, x),
                        (bb) -> bb.order(BE).getShort(8), (bb, v) -> bb.order(BE).putShort(8, v))
                },
                // int, offset
                {"int/offset", new Accessor<>(42,
                        s -> s.get(JAVA_INT_UNALIGNED, 8), (s, x) -> s.set(JAVA_INT_UNALIGNED, 8, x),
                        (bb) -> bb.order(NE).getInt(8), (bb, v) -> bb.order(NE).putInt(8, v))
                },
                {"int/offset/LE", new Accessor<>(42,
                        s -> s.get(JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8, x),
                        (bb) -> bb.order(LE).getInt(8), (bb, v) -> bb.order(LE).putInt(8, v))
                },
                {"int/offset/BE", new Accessor<>(42,
                        s -> s.get(JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, x),
                        (bb) -> bb.order(BE).getInt(8), (bb, v) -> bb.order(BE).putInt(8, v))
                },
                // float, offset
                {"float/offset", new Accessor<>(42f,
                        s -> s.get(JAVA_FLOAT_UNALIGNED, 8), (s, x) -> s.set(JAVA_FLOAT_UNALIGNED, 8, x),
                        (bb) -> bb.order(NE).getFloat(8), (bb, v) -> bb.order(NE).putFloat(8, v))
                },
                {"float/offset/LE", new Accessor<>(42f,
                        s -> s.get(JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8, x),
                        (bb) -> bb.order(LE).getFloat(8), (bb, v) -> bb.order(LE).putFloat(8, v))
                },
                {"float/offset/BE", new Accessor<>(42f,
                        s -> s.get(JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, x),
                        (bb) -> bb.order(BE).getFloat(8), (bb, v) -> bb.order(BE).putFloat(8, v))
                },
                // double, offset
                {"double/offset", new Accessor<>(42d,
                        s -> s.get(JAVA_DOUBLE_UNALIGNED, 8), (s, x) -> s.set(JAVA_DOUBLE_UNALIGNED, 8, x),
                        (bb) -> bb.order(NE).getDouble(8), (bb, v) -> bb.order(NE).putDouble(8, v))
                },
                {"double/offset/LE", new Accessor<>(42d,
                        s -> s.get(JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 8, x),
                        (bb) -> bb.order(LE).getDouble(8), (bb, v) -> bb.order(LE).putDouble(8, v))
                },
                {"double/offset/BE", new Accessor<>(42d,
                        s -> s.get(JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8),
                        (s, x) -> s.set(JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 8, x),
                        (bb) -> bb.order(BE).getDouble(8), (bb, v) -> bb.order(BE).putDouble(8, v))
                },
                { "address/offset", new Accessor<>(MemorySegment.ofAddress(42),
                        s -> s.get(ADDRESS_UNALIGNED, 8), (s, x) -> s.set(ADDRESS_UNALIGNED, 8, x),
                        (bb) -> {
                            ByteBuffer nb = bb.order(NE);
                            long addr = ADDRESS_UNALIGNED.byteSize() == 8 ?
                                    nb.getLong(8) : nb.getInt(8);
                            return MemorySegment.ofAddress(addr);
                        },
                        (bb, v) -> {
                            ByteBuffer nb = bb.order(NE);
                            if (ADDRESS_UNALIGNED.byteSize() == 8) {
                                nb.putLong(8, v.address());
                            } else {
                                nb.putInt(8, (int)v.address());
                            }
                        })
                },
        };
    }
}
