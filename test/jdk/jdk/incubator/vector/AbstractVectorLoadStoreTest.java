/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Stream;

public class AbstractVectorLoadStoreTest extends AbstractVectorTest {

    static final Collection<ByteOrder> BYTE_ORDER_VALUES = Set.of(
            ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);

    static final List<IntFunction<ByteBuffer>> BYTE_BUFFER_GENERATORS = List.of(
            withToString("HB:RW:NE", (int s) ->
                    ByteBuffer.allocate(s)
                        .order(ByteOrder.nativeOrder())),
            withToString("DB:RW:NE", (int s) ->
                    ByteBuffer.allocateDirect(s)
                        .order(ByteOrder.nativeOrder())),
            withToString("MS:RW:NE", (int s) ->
                    Arena.ofAuto().allocate(s)
                        .asByteBuffer()
                        .order(ByteOrder.nativeOrder())
            )
    );

    static final List<IntFunction<MemorySegment>> MEMORY_SEGMENT_GENERATORS = List.of(
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
            withToString("HMS:long[]", (int s) -> {
                long[] b = new long[s / Long.BYTES];
                return MemorySegment.ofArray(b);
            }),
            withToString("HMS:double[]", (int s) -> {
                double[] b = new double[s / Double.BYTES];
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
            }),
            // Slice
            withToString("HMS:long[].asSlice", (int s) -> {
                long[] b = new long[s / Long.BYTES + 1];
                return MemorySegment.ofArray(b).asSlice(Long.BYTES);
            })
    );

}
