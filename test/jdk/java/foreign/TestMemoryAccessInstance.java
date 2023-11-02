/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestMemoryAccessInstance
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_SEGMENT_FORCE_EXACT=true --enable-native-access=ALL-UNNAMED TestMemoryAccessInstance
 */

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.testng.annotations.*;
import org.testng.SkipException;
import static org.testng.Assert.*;

public class TestMemoryAccessInstance {

    static class Accessor<X, L extends ValueLayout> {

        interface SegmentGetter<X, L> {
            X get(MemorySegment segment, L layout, long offset);
        }

        interface SegmentSetter<X, L> {
            void set(MemorySegment segment, L layout, long offset, X o);
        }

        interface BufferGetter<X> {
            X get(ByteBuffer segment, int offset);
        }

        interface BufferSetter<X> {
            void set(ByteBuffer buffer, int offset, X o);
        }

        final X value;
        final L layout;
        final SegmentGetter<X, L> segmentGetter;
        final SegmentSetter<X, L> segmentSetter;
        final BufferGetter<X> bufferGetter;
        final BufferSetter<X> bufferSetter;

        Accessor(L layout, X value,
                 SegmentGetter<X, L> segmentGetter, SegmentSetter<X, L> segmentSetter,
                 BufferGetter<X> bufferGetter, BufferSetter<X> bufferSetter) {
            this.layout = layout;
            this.value = value;
            this.segmentGetter = segmentGetter;
            this.segmentSetter = segmentSetter;
            this.bufferGetter = bufferGetter;
            this.bufferSetter = bufferSetter;
        }

        void test() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(128, 1);
                ByteBuffer buffer = segment.asByteBuffer();
                segmentSetter.set(segment, layout, 8, value);
                assertEquals(bufferGetter.get(buffer, 8), value);
                bufferSetter.set(buffer, 8, value);
                assertEquals(value, segmentGetter.get(segment, layout, 8));
            }
        }

        @SuppressWarnings("unchecked")
        void testHyperAligned() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(64, 1);
                L alignedLayout = (L)layout.withByteAlignment(layout.byteSize() * 2);
                try {
                    segmentSetter.set(segment, alignedLayout, 0, value);
                    fail();
                } catch (IllegalArgumentException exception) {
                    assertTrue(exception.getMessage().contains("greater"));
                }
                try {
                    segmentGetter.get(segment, alignedLayout, 0);
                    fail();
                } catch (IllegalArgumentException exception) {
                    assertTrue(exception.getMessage().contains("greater"));
                }
            }
        }

        X get(MemorySegment segment, long offset) {
            return segmentGetter.get(segment, layout, offset);
        }

        void set(MemorySegment segment, long offset, X value) {
            segmentSetter.set(segment, layout, offset, value);
        }

        static <L extends ValueLayout, X> Accessor<X, L> of(L layout, X value,
                                                            SegmentGetter<X, L> segmentGetter, SegmentSetter<X, L> segmentSetter,
                                                            BufferGetter<X> bufferGetter, BufferSetter<X> bufferSetter) {
            return new Accessor<>(layout, value, segmentGetter, segmentSetter, bufferGetter, bufferSetter);
        }
    }

    @Test(dataProvider = "segmentAccessors")
    public void testSegmentAccess(String testName, Accessor<?, ?> accessor) {
        accessor.test();
    }

    @Test(dataProvider = "segmentAccessors")
    public void testSegmentAccessHyper(String testName, Accessor<?, ?> accessor) {
        if (testName.contains("index")) {
            accessor.testHyperAligned();
        } else {
            throw new SkipException("Skipping");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Heap segment not allowed.*")
    public void badHeapSegmentSet() {
        long byteSize = ValueLayout.ADDRESS.byteSize();
        Arena scope = Arena.ofAuto();
        MemorySegment targetSegment = scope.allocate(byteSize, 1);
        MemorySegment segment = MemorySegment.ofArray(new byte[]{ 0, 1, 2 });
        targetSegment.set(ValueLayout.ADDRESS, 0, segment); // should throw
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*Heap segment not allowed.*")
    public void badHeapSegmentSetAtIndex() {
        long byteSize = ValueLayout.ADDRESS.byteSize();
        Arena scope = Arena.ofAuto();
        MemorySegment targetSegment = scope.allocate(byteSize, 1);
        MemorySegment segment = MemorySegment.ofArray(new byte[]{ 0, 1, 2 });
        targetSegment.setAtIndex(ValueLayout.ADDRESS, 0, segment); // should throw
    }

    @Test(dataProvider = "segmentAccessors")
    public <X, L extends ValueLayout> void badAccessOverflowInIndexedAccess(String testName, Accessor<X, L> accessor) {
        MemorySegment segment = MemorySegment.ofArray(new byte[100]);
        if (testName.contains("/index") && accessor.layout.byteSize() > 1) {
            assertThrows(IndexOutOfBoundsException.class, () -> accessor.get(segment, Long.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> accessor.set(segment, Long.MAX_VALUE, accessor.value));
        }
    }

    static final ByteOrder NE = ByteOrder.nativeOrder();

    @DataProvider(name = "segmentAccessors")
    static Object[][] segmentAccessors() {
        return new Object[][]{

                {"byte", Accessor.of(ValueLayout.JAVA_BYTE, (byte) 42,
                        MemorySegment::get, MemorySegment::set,
                        ByteBuffer::get, ByteBuffer::put)
                },
                {"boolean", Accessor.of(ValueLayout.JAVA_BOOLEAN, false,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.get(pos) != 0, (bb, pos, v) -> bb.put(pos, v ? (byte)1 : (byte)0))
                },
                {"char", Accessor.of(ValueLayout.JAVA_CHAR, (char) 42,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getChar(pos), (bb, pos, v) -> bb.order(NE).putChar(pos, v))
                },
                {"short", Accessor.of(ValueLayout.JAVA_SHORT, (short) 42,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getShort(pos), (bb, pos, v) -> bb.order(NE).putShort(pos, v))
                },
                {"int", Accessor.of(ValueLayout.JAVA_INT, 42,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getInt(pos), (bb, pos, v) -> bb.order(NE).putInt(pos, v))
                },
                {"float", Accessor.of(ValueLayout.JAVA_FLOAT, 42f,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getFloat(pos), (bb, pos, v) -> bb.order(NE).putFloat(pos, v))
                },
                {"long", Accessor.of(ValueLayout.JAVA_LONG, 42L,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getLong(pos), (bb, pos, v) -> bb.order(NE).putLong(pos, v))
                },
                {"double", Accessor.of(ValueLayout.JAVA_DOUBLE, 42d,
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> bb.order(NE).getDouble(pos), (bb, pos, v) -> bb.order(NE).putDouble(pos, v))
                },
                { "address", Accessor.of(ValueLayout.ADDRESS, MemorySegment.ofAddress(42),
                        MemorySegment::get, MemorySegment::set,
                        (bb, pos) -> {
                            ByteBuffer nb = bb.order(NE);
                            long addr = ValueLayout.ADDRESS.byteSize() == 8 ?
                                    nb.getLong(pos) : nb.getInt(pos);
                            return MemorySegment.ofAddress(addr);
                        },
                        (bb, pos, v) -> {
                            ByteBuffer nb = bb.order(NE);
                            if (ValueLayout.ADDRESS.byteSize() == 8) {
                                nb.putLong(pos, v.address());
                            } else {
                                nb.putInt(pos, (int)v.address());
                            }
                        })
                },

                {"byte/index", Accessor.of(ValueLayout.JAVA_BYTE, (byte) 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).get(pos), (bb, pos, v) -> bb.order(NE).put(pos, v))
                },
                {"boolean/index", Accessor.of(ValueLayout.JAVA_BOOLEAN, true,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).get(pos) != 0, (bb, pos, v) -> bb.order(NE).put(pos, (byte) (v ? 1 : 0)))
                },
                {"char/index", Accessor.of(ValueLayout.JAVA_CHAR, (char) 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getChar(pos * 2), (bb, pos, v) -> bb.order(NE).putChar(pos * 2, v))
                },
                {"short/index", Accessor.of(ValueLayout.JAVA_SHORT, (short) 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getShort(pos * 2), (bb, pos, v) -> bb.order(NE).putShort(pos * 2, v))
                },
                {"int/index", Accessor.of(ValueLayout.JAVA_INT, 42,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getInt(pos * 4), (bb, pos, v) -> bb.order(NE).putInt(pos * 4, v))
                },
                {"float/index", Accessor.of(ValueLayout.JAVA_FLOAT, 42f,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getFloat(pos * 4), (bb, pos, v) -> bb.order(NE).putFloat(pos * 4, v))
                },
                {"long/index", Accessor.of(ValueLayout.JAVA_LONG, 42L,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getLong(pos * 8), (bb, pos, v) -> bb.order(NE).putLong(pos * 8, v))
                },
                {"double/index", Accessor.of(ValueLayout.JAVA_DOUBLE, 42d,
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> bb.order(NE).getDouble(pos * 8), (bb, pos, v) -> bb.order(NE).putDouble(pos * 8, v))
                },
                { "address/index", Accessor.of(ValueLayout.ADDRESS, MemorySegment.ofAddress(42),
                        MemorySegment::getAtIndex, MemorySegment::setAtIndex,
                        (bb, pos) -> {
                            ByteBuffer nb = bb.order(NE);
                            long addr = ValueLayout.ADDRESS.byteSize() == 8 ?
                                    nb.getLong(pos * 8) : nb.getInt(pos * 4);
                            return MemorySegment.ofAddress(addr);
                        },
                        (bb, pos, v) -> {
                            ByteBuffer nb = bb.order(NE);
                            if (ValueLayout.ADDRESS.byteSize() == 8) {
                                nb.putLong(pos * 8, v.address());
                            } else {
                                nb.putInt(pos * 4, (int)v.address());
                            }
                        })
                },
        };
    }
}
