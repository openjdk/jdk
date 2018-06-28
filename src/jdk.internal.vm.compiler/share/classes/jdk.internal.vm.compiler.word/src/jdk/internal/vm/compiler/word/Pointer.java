/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.vm.compiler.word;

/**
 * Lowest-level memory access of native C memory.
 * <p>
 * Do not use these methods to access Java objects. These methods access the raw memory without any
 * null checks, read- or write barriers. Even when the VM uses compressed pointers, then readObject
 * and writeObject methods access uncompressed pointers.
 *
 * @since 1.0
 */
public interface Pointer extends UnsignedWord, PointerBase {

    /**
     * Unsafe conversion of this Pointer to a Java language object. No correctness checks or type
     * checks are performed. The caller must ensure that the Pointer contains a valid Java object
     * that can i.e., processed by the garbage collector.
     *
     * @return this Pointer cast to Object.
     *
     * @since 1.0
     */
    Object toObject();

    /**
     * Unsafe conversion of this Pointer to a Java language object. No correctness checks or type
     * checks are performed. The caller must ensure that the Pointer contains a valid Java object
     * that can i.e., processed by the garbage collector and the Pointer does not contain 0.
     *
     * @return this Pointer cast to non-null Object.
     *
     * @since 1.0
     */
    Object toObjectNonNull();

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    byte readByte(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    char readChar(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    short readShort(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    int readInt(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    long readLong(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    float readFloat(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    double readDouble(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    Object readObject(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    byte readByte(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    char readChar(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    short readShort(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    int readInt(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    long readLong(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    float readFloat(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    double readDouble(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 1.0
     */
    Object readObject(int offset, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeChar(WordBase offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeShort(WordBase offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeInt(WordBase offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Initializes the memory at address {@code (this + offset)}. Both the base address and offset
     * are in bytes. The memory must be uninitialized or zero prior to this operation.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeByte(int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeChar(int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeShort(int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeInt(int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeLong(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeFloat(int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeDouble(int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeWord(int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Initializes the memory at address {@code (this + offset)}. Both the base address and offset
     * are in bytes. The memory must be uninitialized or zero prior to this operation.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void initializeLong(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeObject(int offset, Object val, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    byte readByte(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    char readChar(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    short readShort(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    int readInt(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    long readLong(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    float readFloat(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    double readDouble(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    <T extends WordBase> T readWord(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    Object readObject(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    byte readByte(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    char readChar(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    short readShort(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    int readInt(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    long readLong(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    float readFloat(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    double readDouble(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    <T extends WordBase> T readWord(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 1.0
     */
    Object readObject(int offset);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeByte(WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeChar(WordBase offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeShort(WordBase offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeInt(WordBase offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeLong(WordBase offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeFloat(WordBase offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeDouble(WordBase offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeWord(WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeObject(WordBase offset, Object val);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeByte(int offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeChar(int offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeShort(int offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeInt(int offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeLong(int offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeFloat(int offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeDouble(int offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeWord(int offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 1.0
     */
    void writeObject(int offset, Object val);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value after the atomic exchange
     *
     * @since 1.0
     */
    Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    /**
     * Atomically exchanges memory at address {@code (this + offset)}. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected value of the atomic exchange
     * @param newValue the new value of the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 1.0
     */
    boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    // Math functions that are defined in Unsigned, but known to preserve the
    // pointer-characteristics.
    // It is therefore safe that they return a static type of Pointer instead of Unsigned.

    /**
     * Returns a Pointer whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Pointer.
     * @return {@code this + val}
     *
     * @since 1.0
     */
    @Override
    Pointer add(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Pointer.
     * @return {@code this + val}
     *
     * @since 1.0
     */
    @Override
    Pointer add(int val);

    /**
     * Returns a Pointer whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Pointer.
     * @return {@code this - val}
     *
     * @since 1.0
     */
    @Override
    Pointer subtract(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Pointer.
     * @return {@code this - val}
     *
     * @since 1.0
     */
    @Override
    Pointer subtract(int val);

    /**
     * Returns a Pointer whose value is {@code (this & val)}.
     *
     * @param val value to be AND'ed with this Pointer.
     * @return {@code this & val}
     *
     * @since 1.0
     */
    @Override
    Pointer and(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this & val)}.
     *
     * @param val value to be AND'ed with this Pointer.
     * @return {@code this & val}
     *
     * @since 1.0
     */
    @Override
    Pointer and(int val);

    /**
     * Returns a Pointer whose value is {@code (this | val)}.
     *
     * @param val value to be OR'ed with this Pointer.
     * @return {@code this | val}
     *
     * @since 1.0
     */
    @Override
    Pointer or(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this | val)}.
     *
     * @param val value to be OR'ed with this Pointer.
     * @return {@code this | val}
     *
     * @since 1.0
     */
    @Override
    Pointer or(int val);
}
