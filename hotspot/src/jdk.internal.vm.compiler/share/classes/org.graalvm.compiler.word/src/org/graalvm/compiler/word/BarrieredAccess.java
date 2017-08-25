/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

import org.graalvm.compiler.word.Word.Opcode;
import org.graalvm.compiler.word.Word.Operation;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

/**
 * Medium-level memory access for Objects. Similarly to the readXxx and writeXxx methods defined for
 * {@link Pointer} and {@link ObjectAccess}, these methods access the memory without any null
 * checks. However, these methods use read- or write barriers. When the VM uses compressed pointers,
 * then readObject and writeObject methods access compressed pointers.
 */
public final class BarrieredAccess {

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native char readChar(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native short readShort(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native int readInt(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native long readLong(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native char readChar(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native short readShort(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native int readInt(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native long readLong(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, WordBase offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, WordBase offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, WordBase offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, WordBase offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, WordBase offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, WordBase offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, WordBase offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, int offset, Object val, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native char readChar(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native short readShort(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native int readInt(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native long readLong(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native char readChar(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native short readShort(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native int readInt(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native long readLong(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, int offset);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, WordBase offset, char val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, WordBase offset, short val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, WordBase offset, int val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, WordBase offset, long val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, WordBase offset, float val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, WordBase offset, double val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, WordBase offset, Object val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, int offset, byte val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, int offset, char val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, int offset, short val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, int offset, int val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, int offset, long val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, int offset, float val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, int offset, double val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, int offset, WordBase val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, int offset, Object val);
}
