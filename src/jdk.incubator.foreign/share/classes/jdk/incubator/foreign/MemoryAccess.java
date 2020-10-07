/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.incubator.foreign;

import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * This class defines ready-made static accessors which can be used to dereference memory segments in many ways.
 * <p>
 * The most primitive accessors (see {@link #getIntAtOffset(MemorySegment, long)}) take a segment and an offset (expressed in bytes).
 * The final address at which the dereference will occur will be computed by offsetting the base address by
 * the specified offset, as if by calling {@link MemoryAddress#addOffset(long)} on the specified base address.
 * <p>
 * In cases where no offset is required, overloads are provided (see {@link #getInt(MemorySegment)}) so that
 * clients can omit the offset coordinate.
 * <p>
 * To help dereferencing in array-like use cases (e.g. where the layout of a given memory segment is a sequence
 * layout of given size an element count), higher-level overloads are also provided (see {@link #getIntAtIndex(MemorySegment, long)}),
 * which take a segment and a <em>logical</em> element index. The formula to obtain the byte offset {@code O} from an
 * index {@code I} is given by {@code O = I * S} where {@code S} is the size (expressed in bytes) of the element to
 * be dereferenced.
 */
public final class MemoryAccess {

    private MemoryAccess() {
        // just the one
    }

    private static final VarHandle byte_LE_handle = indexedHandle(MemoryLayouts.BITS_8_LE, byte.class);
    private static final VarHandle char_LE_handle = indexedHandle(MemoryLayouts.BITS_16_LE, char.class);
    private static final VarHandle short_LE_handle = indexedHandle(MemoryLayouts.BITS_16_LE, short.class);
    private static final VarHandle int_LE_handle = indexedHandle(MemoryLayouts.BITS_32_LE, int.class);
    private static final VarHandle float_LE_handle = indexedHandle(MemoryLayouts.BITS_32_LE, float.class);
    private static final VarHandle long_LE_handle = indexedHandle(MemoryLayouts.BITS_64_LE, long.class);
    private static final VarHandle double_LE_handle = indexedHandle(MemoryLayouts.BITS_64_LE, double.class);
    private static final VarHandle byte_BE_handle = indexedHandle(MemoryLayouts.BITS_8_BE, byte.class);
    private static final VarHandle char_BE_handle = indexedHandle(MemoryLayouts.BITS_16_BE, char.class);
    private static final VarHandle short_BE_handle = indexedHandle(MemoryLayouts.BITS_16_BE, short.class);
    private static final VarHandle int_BE_handle = indexedHandle(MemoryLayouts.BITS_32_BE, int.class);
    private static final VarHandle float_BE_handle = indexedHandle(MemoryLayouts.BITS_32_BE, float.class);
    private static final VarHandle long_BE_handle = indexedHandle(MemoryLayouts.BITS_64_BE, long.class);
    private static final VarHandle double_BE_handle = indexedHandle(MemoryLayouts.BITS_64_BE, double.class);
    private static final VarHandle address_handle;

    static {
        Class<?> carrier = switch ((int) MemoryLayouts.ADDRESS.byteSize()) {
            case 4 -> int.class;
            case 8 -> long.class;
            default -> throw new ExceptionInInitializerError("Unsupported pointer size: " + MemoryLayouts.ADDRESS.byteSize());
        };
        address_handle = MemoryHandles.asAddressVarHandle(indexedHandle(MemoryLayouts.ADDRESS, carrier));
    }

    /**
     * Read a byte from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_8_LE.withBitAlignment(8).varHandle(byte.class), 1L);
    byte value = (byte)handle.get(segment, offset);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByteAtOffset_LE(MemorySegment segment, long offset) {
        return (byte)byte_LE_handle.get(segment, offset);
    }

    /**
     * Writes a byte at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_8_LE.withBitAlignment(8).varHandle(byte.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtOffset_LE(MemorySegment segment, long offset, byte value) {
        byte_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a char from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_LE.withBitAlignment(8).varHandle(char.class), 1L);
    char value = (char)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a char value read from {@code segment}.
     */
    public static char getCharAtOffset_LE(MemorySegment segment, long offset) {
        return (char)char_LE_handle.get(segment, offset);
    }

    /**
     * Writes a char at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_LE.withBitAlignment(8).varHandle(char.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the char value to be written.
     */
    public static void setCharAtOffset_LE(MemorySegment segment, long offset, char value) {
        char_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a short from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_LE.withBitAlignment(8).varHandle(short.class), 1L);
    short value = (short)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a short value read from {@code segment}.
     */
    public static short getShortAtOffset_LE(MemorySegment segment, long offset) {
        return (short)short_LE_handle.get(segment, offset);
    }

    /**
     * Writes a short at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_LE.withBitAlignment(8).varHandle(short.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the short value to be written.
     */
    public static void setShortAtOffset_LE(MemorySegment segment, long offset, short value) {
        short_LE_handle.set(segment, offset, value);
    }

    /**
     * Read an int from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_LE.withBitAlignment(8).varHandle(int.class), 1L);
    int value = (int)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return an int value read from {@code segment}.
     */
    public static int getIntAtOffset_LE(MemorySegment segment, long offset) {
        return (int)int_LE_handle.get(segment, offset);
    }

    /**
     * Writes an int at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_LE.withBitAlignment(8).varHandle(int.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the int value to be written.
     */
    public static void setIntAtOffset_LE(MemorySegment segment, long offset, int value) {
        int_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a float from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_LE.withBitAlignment(8).varHandle(float.class), 1L);
    float value = (float)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a float value read from {@code segment}.
     */
    public static float getFloatAtOffset_LE(MemorySegment segment, long offset) {
        return (float)float_LE_handle.get(segment, offset);
    }

    /**
     * Writes a float at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_LE.withBitAlignment(8).varHandle(float.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtOffset_LE(MemorySegment segment, long offset, float value) {
        float_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a long from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_LE.withBitAlignment(8).varHandle(long.class), 1L);
    long value = (long)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a long value read from {@code segment}.
     */
    public static long getLongAtOffset_LE(MemorySegment segment, long offset) {
        return (long)long_LE_handle.get(segment, offset);
    }

    /**
     * Writes a long at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_LE.withBitAlignment(8).varHandle(long.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the long value to be written.
     */
    public static void setLongAtOffset_LE(MemorySegment segment, long offset, long value) {
        long_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a double from given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_LE.withBitAlignment(8).varHandle(double.class), 1L);
    double value = (double)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a double value read from {@code segment}.
     */
    public static double getDoubleAtOffset_LE(MemorySegment segment, long offset) {
        return (double)double_LE_handle.get(segment, offset);
    }

    /**
     * Writes a double at given segment and offset, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_LE.withBitAlignment(8).varHandle(double.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtOffset_LE(MemorySegment segment, long offset, double value) {
        double_LE_handle.set(segment, offset, value);
    }

    /**
     * Read a byte from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_8_BE.withBitAlignment(8).varHandle(byte.class), 1L);
    byte value = (byte)handle.get(segment, offset);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByteAtOffset_BE(MemorySegment segment, long offset) {
        return (byte)byte_BE_handle.get(segment, offset);
    }

    /**
     * Writes a byte at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_8_BE.withBitAlignment(8).varHandle(byte.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtOffset_BE(MemorySegment segment, long offset, byte value) {
        byte_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a char from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_BE.withBitAlignment(8).varHandle(char.class), 1L);
    char value = (char)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a char value read from {@code segment}.
     */
    public static char getCharAtOffset_BE(MemorySegment segment, long offset) {
        return (char)char_BE_handle.get(segment, offset);
    }

    /**
     * Writes a char at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_BE.withBitAlignment(8).varHandle(char.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the char value to be written.
     */
    public static void setCharAtOffset_BE(MemorySegment segment, long offset, char value) {
        char_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a short from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_BE.withBitAlignment(8).varHandle(short.class), 1L);
    short value = (short)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a short value read from {@code segment}.
     */
    public static short getShortAtOffset_BE(MemorySegment segment, long offset) {
        return (short)short_BE_handle.get(segment, offset);
    }

    /**
     * Writes a short at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_16_BE.withBitAlignment(8).varHandle(short.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the short value to be written.
     */
    public static void setShortAtOffset_BE(MemorySegment segment, long offset, short value) {
        short_BE_handle.set(segment, offset, value);
    }

    /**
     * Read an int from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_BE.withBitAlignment(8).varHandle(int.class), 1L);
    int value = (int)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return an int value read from {@code segment}.
     */
    public static int getIntAtOffset_BE(MemorySegment segment, long offset) {
        return (int)int_BE_handle.get(segment, offset);
    }

    /**
     * Writes an int at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_BE.withBitAlignment(8).varHandle(int.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the int value to be written.
     */
    public static void setIntAtOffset_BE(MemorySegment segment, long offset, int value) {
        int_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a float from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_BE.withBitAlignment(8).varHandle(float.class), 1L);
    float value = (float)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a float value read from {@code segment}.
     */
    public static float getFloatAtOffset_BE(MemorySegment segment, long offset) {
        return (float)float_BE_handle.get(segment, offset);
    }

    /**
     * Writes a float at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_32_BE.withBitAlignment(8).varHandle(float.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtOffset_BE(MemorySegment segment, long offset, float value) {
        float_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a long from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_BE.withBitAlignment(8).varHandle(long.class), 1L);
    long value = (long)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a long value read from {@code segment}.
     */
    public static long getLongAtOffset_BE(MemorySegment segment, long offset) {
        return (long)long_BE_handle.get(segment, offset);
    }

    /**
     * Writes a long at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_BE.withBitAlignment(8).varHandle(long.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the long value to be written.
     */
    public static void setLongAtOffset_BE(MemorySegment segment, long offset, long value) {
        long_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a double from given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_BE.withBitAlignment(8).varHandle(double.class), 1L);
    double value = (double)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a double value read from {@code segment}.
     */
    public static double getDoubleAtOffset_BE(MemorySegment segment, long offset) {
        return (double)double_BE_handle.get(segment, offset);
    }

    /**
     * Writes a double at given segment and offset, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(BITS_64_BE.withBitAlignment(8).varHandle(double.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtOffset_BE(MemorySegment segment, long offset, double value) {
        double_BE_handle.set(segment, offset, value);
    }

    /**
     * Read a byte from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_BYTE.withBitAlignment(8).varHandle(byte.class), 1L);
    byte value = (byte)handle.get(segment, offset);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByteAtOffset(MemorySegment segment, long offset) {
        return (byte)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? byte_BE_handle : byte_LE_handle).get(segment, offset);
    }

    /**
     * Writes a byte at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_BYTE.withBitAlignment(8).varHandle(byte.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtOffset(MemorySegment segment, long offset, byte value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? byte_BE_handle : byte_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a char from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_CHAR.withBitAlignment(8).varHandle(char.class), 1L);
    char value = (char)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a char value read from {@code segment}.
     */
    public static char getCharAtOffset(MemorySegment segment, long offset) {
        return (char)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? char_BE_handle : char_LE_handle).get(segment, offset);
    }

    /**
     * Writes a char at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_CHAR.withBitAlignment(8).varHandle(char.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the char value to be written.
     */
    public static void setCharAtOffset(MemorySegment segment, long offset, char value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? char_BE_handle : char_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a short from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_SHORT.withBitAlignment(8).varHandle(short.class), 1L);
    short value = (short)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a short value read from {@code segment}.
     */
    public static short getShortAtOffset(MemorySegment segment, long offset) {
        return (short)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? short_BE_handle : short_LE_handle).get(segment, offset);
    }

    /**
     * Writes a short at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_SHORT.withBitAlignment(8).varHandle(short.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the short value to be written.
     */
    public static void setShortAtOffset(MemorySegment segment, long offset, short value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? short_BE_handle : short_LE_handle).set(segment, offset, value);
    }

    /**
     * Read an int from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_INT.withBitAlignment(8).varHandle(int.class), 1L);
    int value = (int)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return an int value read from {@code segment}.
     */
    public static int getIntAtOffset(MemorySegment segment, long offset) {
        return (int)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? int_BE_handle : int_LE_handle).get(segment, offset);
    }

    /**
     * Writes an int at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_INT.withBitAlignment(8).varHandle(int.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the int value to be written.
     */
    public static void setIntAtOffset(MemorySegment segment, long offset, int value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? int_BE_handle : int_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a float from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_FLOAT.withBitAlignment(8).varHandle(float.class), 1L);
    float value = (float)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a float value read from {@code segment}.
     */
    public static float getFloatAtOffset(MemorySegment segment, long offset) {
        return (float)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? float_BE_handle : float_LE_handle).get(segment, offset);
    }

    /**
     * Writes a float at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_FLOAT.withBitAlignment(8).varHandle(float.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtOffset(MemorySegment segment, long offset, float value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? float_BE_handle : float_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a long from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_LONG.withBitAlignment(8).varHandle(long.class), 1L);
    long value = (long)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a long value read from {@code segment}.
     */
    public static long getLongAtOffset(MemorySegment segment, long offset) {
        return (long)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? long_BE_handle : long_LE_handle).get(segment, offset);
    }

    /**
     * Writes a long at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_LONG.withBitAlignment(8).varHandle(long.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the long value to be written.
     */
    public static void setLongAtOffset(MemorySegment segment, long offset, long value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? long_BE_handle : long_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a double from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_DOUBLE.withBitAlignment(8).varHandle(double.class), 1L);
    double value = (double)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a double value read from {@code segment}.
     */
    public static double getDoubleAtOffset(MemorySegment segment, long offset) {
        return (double)((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? double_BE_handle : double_LE_handle).get(segment, offset);
    }

    /**
     * Writes a double at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.withStride(JAVA_DOUBLE.withBitAlignment(8).varHandle(double.class), 1L);
    handle.set(segment, offset, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtOffset(MemorySegment segment, long offset, double value) {
        ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? double_BE_handle : double_LE_handle).set(segment, offset, value);
    }

    /**
     * Read a memory address from given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.asAddressHandle(MemoryHandles.withStride(JAVA_LONG.withBitAlignment(8).varHandle(long.class), 1L));
    MemoryAddress value = (MemoryAddress)handle.get(segment, offset);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @return a memory address read from {@code segment}.
     */
    public static MemoryAddress getAddressAtOffset(MemorySegment segment, long offset) {
        return (MemoryAddress)address_handle.get(segment, offset);
    }

    /**
     * Writes a memory address at given segment and offset, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    VarHandle handle = MemoryHandles.asAddressHandle(MemoryHandles.withStride(JAVA_LONG.withBitAlignment(8).varHandle(long.class), 1L));
    handle.set(segment, offset, value.address());
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param offset offset (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(offset)}.
     * @param value the memory address to be written (expressed as an {@link Addressable} instance).
     */
    public static void setAddressAtOffset(MemorySegment segment, long offset, Addressable value) {
        address_handle.set(segment, offset, value.address());
    }

    private static VarHandle indexedHandle(ValueLayout elementLayout, Class<?> carrier) {
        return MemoryHandles.varHandle(carrier, 1, elementLayout.order());
    }

    /**
     * Read a byte from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByte_LE(MemorySegment segment) {
        return getByteAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a byte at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the byte value to be written.
     */
    public static void setByte_LE(MemorySegment segment, byte value) {
        setByteAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a char from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a char value read from {@code segment}.
     */
    public static char getChar_LE(MemorySegment segment) {
        return getCharAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a char at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the char value to be written.
     */
    public static void setChar_LE(MemorySegment segment, char value) {
        setCharAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a short from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a short value read from {@code segment}.
     */
    public static short getShort_LE(MemorySegment segment) {
        return getShortAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a short at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the short value to be written.
     */
    public static void setShort_LE(MemorySegment segment, short value) {
        setShortAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read an int from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return an int value read from {@code segment}.
     */
    public static int getInt_LE(MemorySegment segment) {
        return getIntAtOffset_LE(segment, 0L);
    }

    /**
     * Writes an int at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the int value to be written.
     */
    public static void setInt_LE(MemorySegment segment, int value) {
        setIntAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a float from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a float value read from {@code segment}.
     */
    public static float getFloat_LE(MemorySegment segment) {
        return getFloatAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a float at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the float value to be written.
     */
    public static void setFloat_LE(MemorySegment segment, float value) {
        setFloatAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a long from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    long value = getLongAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a long value read from {@code segment}.
     */
    public static long getLong_LE(MemorySegment segment) {
        return getLongAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a long at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the long value to be written.
     */
    public static void setLong_LE(MemorySegment segment, long value) {
        setLongAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a double from given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    double value = getDoubleAtOffset_LE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a double value read from {@code segment}.
     */
    public static double getDouble_LE(MemorySegment segment) {
        return getDoubleAtOffset_LE(segment, 0L);
    }

    /**
     * Writes a double at given segment, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset_LE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the double value to be written.
     */
    public static void setDouble_LE(MemorySegment segment, double value) {
        setDoubleAtOffset_LE(segment, 0L, value);
    }

    /**
     * Read a byte from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByte_BE(MemorySegment segment) {
        return getByteAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a byte at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the byte value to be written.
     */
    public static void setByte_BE(MemorySegment segment, byte value) {
        setByteAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a char from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a char value read from {@code segment}.
     */
    public static char getChar_BE(MemorySegment segment) {
        return getCharAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a char at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the char value to be written.
     */
    public static void setChar_BE(MemorySegment segment, char value) {
        setCharAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a short from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a short value read from {@code segment}.
     */
    public static short getShort_BE(MemorySegment segment) {
        return getShortAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a short at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the short value to be written.
     */
    public static void setShort_BE(MemorySegment segment, short value) {
        setShortAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read an int from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return an int value read from {@code segment}.
     */
    public static int getInt_BE(MemorySegment segment) {
        return getIntAtOffset_BE(segment, 0L);
    }

    /**
     * Writes an int at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the int value to be written.
     */
    public static void setInt_BE(MemorySegment segment, int value) {
        setIntAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a float from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a float value read from {@code segment}.
     */
    public static float getFloat_BE(MemorySegment segment) {
        return getFloatAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a float at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the float value to be written.
     */
    public static void setFloat_BE(MemorySegment segment, float value) {
        setFloatAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a long from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    long value = getLongAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a long value read from {@code segment}.
     */
    public static long getLong_BE(MemorySegment segment) {
        return getLongAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a long at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the long value to be written.
     */
    public static void setLong_BE(MemorySegment segment, long value) {
        setLongAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a double from given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    double value = getDoubleAtOffset_BE(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a double value read from {@code segment}.
     */
    public static double getDouble_BE(MemorySegment segment) {
        return getDoubleAtOffset_BE(segment, 0L);
    }

    /**
     * Writes a double at given segment, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset_BE(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the double value to be written.
     */
    public static void setDouble_BE(MemorySegment segment, double value) {
        setDoubleAtOffset_BE(segment, 0L, value);
    }

    /**
     * Read a byte from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset(segment, 0L);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @return a byte value read from {@code segment}.
     */
    public static byte getByte(MemorySegment segment) {
        return getByteAtOffset(segment, 0L);
    }

    /**
     * Writes a byte at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the byte value to be written.
     */
    public static void setByte(MemorySegment segment, byte value) {
        setByteAtOffset(segment, 0L, value);
    }

    /**
     * Read a char from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a char value read from {@code segment}.
     */
    public static char getChar(MemorySegment segment) {
        return getCharAtOffset(segment, 0L);
    }

    /**
     * Writes a char at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the char value to be written.
     */
    public static void setChar(MemorySegment segment, char value) {
        setCharAtOffset(segment, 0L, value);
    }

    /**
     * Read a short from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a short value read from {@code segment}.
     */
    public static short getShort(MemorySegment segment) {
        return getShortAtOffset(segment, 0L);
    }

    /**
     * Writes a short at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the short value to be written.
     */
    public static void setShort(MemorySegment segment, short value) {
        setShortAtOffset(segment, 0L, value);
    }

    /**
     * Read an int from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return an int value read from {@code segment}.
     */
    public static int getInt(MemorySegment segment) {
        return getIntAtOffset(segment, 0L);
    }

    /**
     * Writes an int at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the int value to be written.
     */
    public static void setInt(MemorySegment segment, int value) {
        setIntAtOffset(segment, 0L, value);
    }

    /**
     * Read a float from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a float value read from {@code segment}.
     */
    public static float getFloat(MemorySegment segment) {
        return getFloatAtOffset(segment, 0L);
    }

    /**
     * Writes a float at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the float value to be written.
     */
    public static void setFloat(MemorySegment segment, float value) {
        setFloatAtOffset(segment, 0L, value);
    }

    /**
     * Read a long from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    long value = getLongAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a long value read from {@code segment}.
     */
    public static long getLong(MemorySegment segment) {
        return getLongAtOffset(segment, 0L);
    }

    /**
     * Writes a long at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the long value to be written.
     */
    public static void setLong(MemorySegment segment, long value) {
        setLongAtOffset(segment, 0L, value);
    }

    /**
     * Read a double from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    double value = getDoubleAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a double value read from {@code segment}.
     */
    public static double getDouble(MemorySegment segment) {
        return getDoubleAtOffset(segment, 0L);
    }

    /**
     * Writes a double at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the double value to be written.
     */
    public static void setDouble(MemorySegment segment, double value) {
        setDoubleAtOffset(segment, 0L, value);
    }

    /**
     * Read a memory address from given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryAddress value = getAddressAtOffset(segment, 0L);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @return a memory address read from {@code segment}.
     */
    public static MemoryAddress getAddress(MemorySegment segment) {
        return getAddressAtOffset(segment, 0L);
    }

    /**
     * Writes a memory address at given segment, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setAddressAtOffset(segment, 0L, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param value the memory address to be written (expressed as an {@link Addressable} instance).
     */
    public static void setAddress(MemorySegment segment, Addressable value) {
        setAddressAtOffset(segment, 0L, value);
    }

    /**
     * Read a byte from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset_LE(segment, index);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @return a byte value read from {@code segment} at the element index specified by {@code index}.
     */
    public static byte getByteAtIndex_LE(MemorySegment segment, long index) {
        return getByteAtOffset_LE(segment, index);
    }

    /**
     * Writes a byte at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset_LE(segment, index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtIndex_LE(MemorySegment segment, long index, byte value) {
        setByteAtOffset_LE(segment, index, value);
    }

    /**
     * Read a char from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset_LE(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a char value read from {@code segment} at the element index specified by {@code index}.
     */
    public static char getCharAtIndex_LE(MemorySegment segment, long index) {
        return getCharAtOffset_LE(segment, scale(segment, index, 2));
    }

    /**
     * Writes a char at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset_LE(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the char value to be written.
     */
    public static void setCharAtIndex_LE(MemorySegment segment, long index, char value) {
        setCharAtOffset_LE(segment, scale(segment, index, 2), value);
    }

    /**
     * Read a short from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset_LE(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a short value read from {@code segment} at the element index specified by {@code index}.
     */
    public static short getShortAtIndex_LE(MemorySegment segment, long index) {
        return getShortAtOffset_LE(segment, scale(segment, index, 2));
    }

    /**
     * Writes a short at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset_LE(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the short value to be written.
     */
    public static void setShortAtIndex_LE(MemorySegment segment, long index, short value) {
        setShortAtOffset_LE(segment, scale(segment, index, 2), value);
    }

    /**
     * Read an int from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset_LE(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return an int value read from {@code segment} at the element index specified by {@code index}.
     */
    public static int getIntAtIndex_LE(MemorySegment segment, long index) {
        return getIntAtOffset_LE(segment, scale(segment, index, 4));
    }

    /**
     * Writes an int at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset_LE(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the int value to be written.
     */
    public static void setIntAtIndex_LE(MemorySegment segment, long index, int value) {
        setIntAtOffset_LE(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a float from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset_LE(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return a float value read from {@code segment} at the element index specified by {@code index}.
     */
    public static float getFloatAtIndex_LE(MemorySegment segment, long index) {
        return getFloatAtOffset_LE(segment, scale(segment, index, 4));
    }

    /**
     * Writes a float at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset_LE(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtIndex_LE(MemorySegment segment, long index, float value) {
        setFloatAtOffset_LE(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a long from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getLongAtOffset_LE(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a long value read from {@code segment} at the element index specified by {@code index}.
     */
    public static long getLongAtIndex_LE(MemorySegment segment, long index) {
        return getLongAtOffset_LE(segment, scale(segment, index, 8));
    }

    /**
     * Writes a long at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset_LE(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the long value to be written.
     */
    public static void setLongAtIndex_LE(MemorySegment segment, long index, long value) {
        setLongAtOffset_LE(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a double from given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getDoubleAtOffset_LE(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a double value read from {@code segment} at the element index specified by {@code index}.
     */
    public static double getDoubleAtIndex_LE(MemorySegment segment, long index) {
        return getDoubleAtOffset_LE(segment, scale(segment, index, 8));
    }

    /**
     * Writes a double at given segment and element index, with byte order set to {@link ByteOrder#LITTLE_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset_LE(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtIndex_LE(MemorySegment segment, long index, double value) {
        setDoubleAtOffset_LE(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a byte from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset_BE(segment, index);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @return a byte value read from {@code segment} at the element index specified by {@code index}.
     */
    public static byte getByteAtIndex_BE(MemorySegment segment, long index) {
        return getByteAtOffset_BE(segment, index);
    }

    /**
     * Writes a byte at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset_BE(segment, index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtIndex_BE(MemorySegment segment, long index, byte value) {
        setByteAtOffset_BE(segment, index, value);
    }

    /**
     * Read a char from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset_BE(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a char value read from {@code segment} at the element index specified by {@code index}.
     */
    public static char getCharAtIndex_BE(MemorySegment segment, long index) {
        return getCharAtOffset_BE(segment, scale(segment, index, 2));
    }

    /**
     * Writes a char at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset_BE(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the char value to be written.
     */
    public static void setCharAtIndex_BE(MemorySegment segment, long index, char value) {
        setCharAtOffset_BE(segment, scale(segment, index, 2), value);
    }

    /**
     * Read a short from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset_BE(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a short value read from {@code segment} at the element index specified by {@code index}.
     */
    public static short getShortAtIndex_BE(MemorySegment segment, long index) {
        return getShortAtOffset_BE(segment, scale(segment, index, 2));
    }

    /**
     * Writes a short at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset_BE(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the short value to be written.
     */
    public static void setShortAtIndex_BE(MemorySegment segment, long index, short value) {
        setShortAtOffset_BE(segment, scale(segment, index, 2), value);
    }

    /**
     * Read an int from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset_BE(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return an int value read from {@code segment} at the element index specified by {@code index}.
     */
    public static int getIntAtIndex_BE(MemorySegment segment, long index) {
        return getIntAtOffset_BE(segment, scale(segment, index, 4));
    }

    /**
     * Writes an int at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset_BE(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the int value to be written.
     */
    public static void setIntAtIndex_BE(MemorySegment segment, long index, int value) {
        setIntAtOffset_BE(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a float from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset_BE(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return a float value read from {@code segment} at the element index specified by {@code index}.
     */
    public static float getFloatAtIndex_BE(MemorySegment segment, long index) {
        return getFloatAtOffset_BE(segment, scale(segment, index, 4));
    }

    /**
     * Writes a float at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset_BE(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtIndex_BE(MemorySegment segment, long index, float value) {
        setFloatAtOffset_BE(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a long from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getLongAtOffset_BE(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a long value read from {@code segment} at the element index specified by {@code index}.
     */
    public static long getLongAtIndex_BE(MemorySegment segment, long index) {
        return getLongAtOffset_BE(segment, scale(segment, index, 8));
    }

    /**
     * Writes a long at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset_BE(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the long value to be written.
     */
    public static void setLongAtIndex_BE(MemorySegment segment, long index, long value) {
        setLongAtOffset_BE(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a double from given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getDoubleAtOffset_BE(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a double value read from {@code segment} at the element index specified by {@code index}.
     */
    public static double getDoubleAtIndex_BE(MemorySegment segment, long index) {
        return getDoubleAtOffset_BE(segment, scale(segment, index, 8));
    }

    /**
     * Writes a double at given segment and element index, with byte order set to {@link ByteOrder#BIG_ENDIAN}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset_BE(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtIndex_BE(MemorySegment segment, long index, double value) {
        setDoubleAtOffset_BE(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a byte from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    byte value = getByteAtOffset(segment, index);
     * }</pre></blockquote>
     *
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @return a byte value read from {@code segment} at the element index specified by {@code index}.
     */
    public static byte getByteAtIndex(MemorySegment segment, long index) {
        return getByteAtOffset(segment, index);
    }

    /**
     * Writes a byte at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setByteAtOffset(segment, index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index)}.
     * @param value the byte value to be written.
     */
    public static void setByteAtIndex(MemorySegment segment, long index, byte value) {
        setByteAtOffset(segment, index, value);
    }

    /**
     * Read a char from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    char value = getCharAtOffset(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a char value read from {@code segment} at the element index specified by {@code index}.
     */
    public static char getCharAtIndex(MemorySegment segment, long index) {
        return getCharAtOffset(segment, scale(segment, index, 2));
    }

    /**
     * Writes a char at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setCharAtOffset(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the char value to be written.
     */
    public static void setCharAtIndex(MemorySegment segment, long index, char value) {
        setCharAtOffset(segment, scale(segment, index, 2), value);
    }

    /**
     * Read a short from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    short value = getShortAtOffset(segment, 2 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @return a short value read from {@code segment} at the element index specified by {@code index}.
     */
    public static short getShortAtIndex(MemorySegment segment, long index) {
        return getShortAtOffset(segment, scale(segment, index, 2));
    }

    /**
     * Writes a short at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setShortAtOffset(segment, 2 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 2)}.
     * @param value the short value to be written.
     */
    public static void setShortAtIndex(MemorySegment segment, long index, short value) {
        setShortAtOffset(segment, scale(segment, index, 2), value);
    }

    /**
     * Read an int from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    int value = getIntAtOffset(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return an int value read from {@code segment} at the element index specified by {@code index}.
     */
    public static int getIntAtIndex(MemorySegment segment, long index) {
        return getIntAtOffset(segment, scale(segment, index, 4));
    }

    /**
     * Writes an int at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setIntAtOffset(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the int value to be written.
     */
    public static void setIntAtIndex(MemorySegment segment, long index, int value) {
        setIntAtOffset(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a float from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    float value = getFloatAtOffset(segment, 4 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @return a float value read from {@code segment} at the element index specified by {@code index}.
     */
    public static float getFloatAtIndex(MemorySegment segment, long index) {
        return getFloatAtOffset(segment, scale(segment, index, 4));
    }

    /**
     * Writes a float at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setFloatAtOffset(segment, 4 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 4)}.
     * @param value the float value to be written.
     */
    public static void setFloatAtIndex(MemorySegment segment, long index, float value) {
        setFloatAtOffset(segment, scale(segment, index, 4), value);
    }

    /**
     * Read a long from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getLongAtOffset(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a long value read from {@code segment} at the element index specified by {@code index}.
     */
    public static long getLongAtIndex(MemorySegment segment, long index) {
        return getLongAtOffset(segment, scale(segment, index, 8));
    }

    /**
     * Writes a long at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setLongAtOffset(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the long value to be written.
     */
    public static void setLongAtIndex(MemorySegment segment, long index, long value) {
        setLongAtOffset(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a double from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getDoubleAtOffset(segment, 8 * index);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a double value read from {@code segment} at the element index specified by {@code index}.
     */
    public static double getDoubleAtIndex(MemorySegment segment, long index) {
        return getDoubleAtOffset(segment, scale(segment, index, 8));
    }

    /**
     * Writes a double at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setDoubleAtOffset(segment, 8 * index, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the double value to be written.
     */
    public static void setDoubleAtIndex(MemorySegment segment, long index, double value) {
        setDoubleAtOffset(segment, scale(segment, index, 8), value);
    }

    /**
     * Read a memory address from given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    return getAddressAtOffset(segment, index * 8);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @return a memory address read from {@code segment} at the element index specified by {@code index}.
     */
    public static MemoryAddress getAddressAtIndex(MemorySegment segment, long index) {
        return getAddressAtOffset(segment, scale(segment, index, 8));
    }

    /**
     * Writes a memory address at given segment and element index, with byte order set to {@link ByteOrder#nativeOrder()}.
     * <p>
     * This is equivalent to the following code:
     * <blockquote><pre>{@code
    setAddressAtOffset(segment, index * 8, value);
     * }</pre></blockquote>
     * @param segment the segment to be dereferenced.
     * @param index element index (relative to {@code segment}). The final address of this read operation can be expressed as {@code segment.address().addOffset(index * 8)}.
     * @param value the memory address to be written (expressed as an {@link Addressable} instance).
     */
    public static void setAddressAtIndex(MemorySegment segment, long index, Addressable value) {
        setAddressAtOffset(segment, scale(segment, index, 8), value);
    }

    @ForceInline
    private static long scale(MemorySegment address, long index, int size) {
        return MemorySegmentProxy.multiplyOffsets(index, size, (MemorySegmentProxy)address);
    }
}
