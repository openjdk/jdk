/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.VectorSpecies;
import org.testng.annotations.DataProvider;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AbstractVectorLoadStoreTest extends AbstractVectorTest {

    static final ValueLayout.OfInt SHUFFLE_ELEMENT_LAYOUT = ValueLayout.JAVA_INT.withByteAlignment(1);

    static final Collection<ByteOrder> BYTE_ORDER_VALUES = Set.of(
            ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);

    static final int SHUFFLE_BUFFER_REPS = Integer.getInteger("jdk.incubator.vector.test.buffer-vectors", 25000 / 256);

    static final List<IntFunction<int[]>> SHUFFLE_INT_GENERATORS = List.of(
            withToString("int[i * 5]", (int s) -> {
                return fill(s * SHUFFLE_BUFFER_REPS,
                        i -> (int)(i * 5));
            }),
            withToString("int[i + 1]", (int s) -> {
                return fill(s * SHUFFLE_BUFFER_REPS,
                        i -> (((int)(i + 1) == 0) ? 1 : (int)(i + 1)));
            })
    );

    static final List<IntFunction<Integer>> SHUFFLE_INDEX_GENERATORS = List.of(
            withToString("-1", (int l) -> {
                return -1;
            }),
            withToString("l", (int l) -> {
                return l;
            }),
            withToString("l - 1", (int l) -> {
                return l - 1;
            }),
            withToString("l + 1", (int l) -> {
                return l + 1;
            })
    );

    // Relative to byte[] array.length or MemorySegment.byteSize()
    static final List<IntFunction<Integer>> SHUFFLE_BYTE_INDEX_GENERATORS = List.of(
            withToString("-1", (int l) -> {
                return -1;
            }),
            withToString("l", (int l) -> {
                return l;
            }),
            withToString("l - 1", (int l) -> {
                return l - 1;
            }),
            withToString("l + 1", (int l) -> {
                return l + 1;
            })
    );

    @DataProvider
    public Object[][] shuffleIntProvider() {
        return SHUFFLE_INT_GENERATORS.stream()
                .map(f -> new Object[]{f})
                .toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shuffleIntProviderForIOOBE() {
        var f = SHUFFLE_INT_GENERATORS.get(0);
        return SHUFFLE_INDEX_GENERATORS.stream()
                .map(fi -> {
                    return new Object[] {f, fi};
                })
                .toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shuffleIntMemorySegmentProvider() {
        return SHUFFLE_INT_GENERATORS.stream().
                flatMap(fa -> SHUFFLE_MEMORY_SEGMENT_GENERATORS.stream().
                        flatMap(fb -> BYTE_ORDER_VALUES.stream().map(bo -> {
                            return new Object[]{fa, fb, bo};
                        }))).
                toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shuffleIntByteProviderForIOOBE() {
        var f = SHUFFLE_INT_GENERATORS.get(0);
        return SHUFFLE_BYTE_INDEX_GENERATORS.stream().map(fi -> {
                    return new Object[] {f, fi};
                }).toArray(Object[][]::new);
    }

    static MemorySegment toShuffleSegment(VectorSpecies<?> vsp, int[] a, IntFunction<MemorySegment> fb) {
        MemorySegment ms = fb.apply(a.length * 4);
        return ms.copyFrom(MemorySegment.ofArray(a));
    }

    private static final List<IntFunction<MemorySegment>> SHARED_MEMORY_SEGMENT_GENERATORS = List.of(
            withToString("DMS", (int s) ->
                    Arena.ofAuto().allocate(s)
            ),
            withToString("HMS:byte[]", (int s) -> {
                byte[] b = new byte[s];
                return MemorySegment.ofArray(b);
            }),
            withToString("HMS:short[]", (int s) -> {
                short[] b = new short[s / Short.BYTES];
                return MemorySegment.ofArray(b);
            }),
            withToString("HMS:int[]", (int s) -> {
                int[] b = new int[s / Integer.BYTES];
                return MemorySegment.ofArray(b);
            }),
            withToString("HMS:float[]", (int s) -> {
                float[] b = new float[s / Float.BYTES];
                return MemorySegment.ofArray(b);
            }),
            withToString("HMS:ByteBuffer.wrap", (int s) -> {
                byte[] b = new byte[s];
                ByteBuffer buff = ByteBuffer.wrap(b);
                return MemorySegment.ofBuffer(buff);
            }),
            // Just test one of the specialized buffers
            withToString("HMS:IntBuffer.wrap", (int s) -> {
                int[] b = new int[s / Integer.BYTES];
                IntBuffer buff = IntBuffer.wrap(b);
                return MemorySegment.ofBuffer(buff);
            }),
            withToString("HMS:ByteBuffer.allocate", (int s) -> {
                ByteBuffer buff = ByteBuffer.allocate(s);
                return MemorySegment.ofBuffer(buff);
            }),
            // Just test one of the specialized buffers
            withToString("HMS:IntBuffer.allocate", (int s) -> {
                IntBuffer buff = IntBuffer.allocate(s / Integer.BYTES);
                return MemorySegment.ofBuffer(buff);
            })
    );


    //These tests are adjusted to ensure we allocate enough memory for ints because we're passing
    //a memory segment size of an int array in bytes, but it's subject to integer division of a longer
    //array element.
    static final List<IntFunction<MemorySegment>> SHUFFLE_MEMORY_SEGMENT_GENERATORS = Stream.concat(
            SHARED_MEMORY_SEGMENT_GENERATORS.stream(),
            Stream.of(
                withToString("HMS:long[]:shuffle", (int s) -> {
                    long[] b = new long[(s + Long.BYTES- 1) / Long.BYTES];
                    return MemorySegment.ofArray(b);
                }),

                withToString("HMS:double[]:shuffle", (int s) -> {
                    double[] b = new double[(s  + Double.BYTES - 1)/ Double.BYTES];
                    return MemorySegment.ofArray(b);
                }),
                // Slice
                withToString("HMS:long[].asSlice:shuffle", (int s) -> {
                    long[] b = new long[(s + Long.BYTES - 1) / Long.BYTES + 1];
                    return MemorySegment.ofArray(b).asSlice(Long.BYTES);
                })
            )
    ).toList();

    static final List<IntFunction<MemorySegment>> MEMORY_SEGMENT_GENERATORS = Stream.concat(
            SHARED_MEMORY_SEGMENT_GENERATORS.stream(),
            Stream.of(
                    withToString("HMS:long[]", (int s) -> {
                        long[] b = new long[s / Long.BYTES];
                        return MemorySegment.ofArray(b);
                    }),
                    withToString("HMS:double[]", (int s) -> {
                        double[] b = new double[s / Double.BYTES];
                        return MemorySegment.ofArray(b);
                    }),
                    // Slice
                    withToString("HMS:long[].asSlice", (int s) -> {
                        long[] b = new long[s / Long.BYTES + 1];
                        return MemorySegment.ofArray(b).asSlice(Long.BYTES);
                    })
            )
    ).toList();

    private static final int[] fill(int s, IntUnaryOperator f) {
        return IntStream.range(0, s).map(f).toArray();
    }

}
