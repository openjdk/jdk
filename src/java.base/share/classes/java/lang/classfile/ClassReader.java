/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolException;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Optional;
import java.util.function.Function;

import jdk.internal.classfile.impl.ClassReaderImpl;

/**
 * Advanced {@code class} file reading support for {@link AttributeMapper}s.
 * Supports reading arbitrary offsets within a {@code class} file and reading
 * data of various numeric types (e.g., {@code u2}, {@code u4}) in addition to
 * constant pool access.
 * <p>
 * All numeric values in the {@code class} file format are {@linkplain
 * java.nio.ByteOrder#BIG_ENDIAN big endian}.
 * <p>
 * Unless otherwise specified, all out-of-bounds access result in an {@link
 * IllegalArgumentException} to indicate the {@code class} file data is
 * malformed.  Since the {@code class} file data is arbitrary, users should
 * sanity-check the structural integrity of the data before attempting to
 * interpret the potentially malformed data.
 *
 * @see AttributeMapper#readAttribute(AttributedElement, ClassReader, int)
 * @since 24
 */
public sealed interface ClassReader extends ConstantPool
        permits ClassReaderImpl {

    // Processing context

    /**
     * {@return the table of custom attribute mappers}  This is derived from
     * the processing option {@link ClassFile.AttributeMapperOption}.
     */
    Function<Utf8Entry, AttributeMapper<?>> customAttributes();

    // Class context

    /**
     * {@return the access flags for the class, as a bit mask}
     *
     * @see ClassModel#flags()
     */
    int flags();

    /**
     * {@return the constant pool entry describing the name of class}
     *
     * @see ClassModel#thisClass()
     */
    ClassEntry thisClassEntry();

    /**
     * {@return the constant pool entry describing the name of the superclass, if any}
     *
     * @see ClassModel#superclass()
     */
    Optional<ClassEntry> superclassEntry();

    /** {@return the length of the {@code class} file, in number of bytes} */
    int classfileLength();

    // Constant pool

    /**
     * {@return the constant pool entry whose index is given at the specified
     * offset within the {@code class} file}
     *
     * @apiNote
     * If only a particular type of entry is expected, use {@link #readEntry(
     * int, Class) readEntry(int, Class)}.
     *
     * @param offset the offset of the index within the {@code class} file
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero
     */
    PoolEntry readEntry(int offset);

    /**
     * {@return the constant pool entry of a given type whose index is given
     * at the specified offset within the {@code class} file}
     * @param <T> the entry type
     * @param offset the offset of the index within the {@code class} file
     * @param cls the entry type
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the entry is not of the given type
     */
    <T extends PoolEntry> T readEntry(int offset, Class<T> cls);

    /**
     * {@return the constant pool entry whose index is given at the specified
     * offset within the {@code class} file, or {@code null} if the index at the
     * specified offset is zero}
     *
     * @apiNote
     * If only a particular type of entry is expected, use {@link #readEntryOrNull(
     * int, Class) readEntryOrNull(int, Class)}.
     *
     * @param offset the offset of the index within the {@code class} file
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size
     */
    PoolEntry readEntryOrNull(int offset);

    /**
     * {@return the constant pool entry of a given type whose index is given
     * at the specified offset within the {@code class} file, or {@code null} if
     * the index at the specified offset is zero}
     *
     * @param <T> the entry type
     * @param offset the offset of the index within the {@code class} file
     * @param cls the entry type
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the entry is not of the given type
     */
    <T extends PoolEntry> T readEntryOrNull(int offset, Class<T> cls);

    /**
     * {@return the unsigned byte at the specified offset within the {@code
     * class} file}  Reads a byte and zero-extends it to an {@code int}.
     *
     * @param offset the offset within the {@code class} file
     */
    int readU1(int offset);

    /**
     * {@return the unsigned short at the specified offset within the {@code
     * class} file}  Reads a 2-byte value and zero-extends it to an {@code int}.
     *
     * @param offset the offset within the {@code class} file
     */
    int readU2(int offset);

    /**
     * {@return the signed byte at the specified offset within the {@code class}
     * file}  Reads a byte and sign-extends it to an {@code int}.
     *
     * @param offset the offset within the {@code class} file
     */
    int readS1(int offset);

    /**
     * {@return the signed byte at the specified offset within the {@code class}
     * file}  Reads a 2-byte value and sign-extends it to an {@code int}.
     *
     * @param offset the offset within the {@code class} file
     */
    int readS2(int offset);

    /**
     * {@return the signed int at the specified offset within the {@code class}
     * file}  Reads 4 bytes of value.
     *
     * @param offset the offset within the {@code class} file
     */
    int readInt(int offset);

    /**
     * {@return the signed long at the specified offset within the {@code class}
     * file}  Reads 8 bytes of value.
     *
     * @param offset the offset within the {@code class} file
     */
    long readLong(int offset);

    /**
     * {@return the float value at the specified offset within the {@code class}
     * file}  Reads 4 bytes of value.
     * <p>
     * In the conversions, all NaN values of the {@code float} may or may not be
     * collapsed into a single {@linkplain Float#NaN "canonical" NaN value}.
     *
     * @param offset the offset within the {@code class} file
     */
    float readFloat(int offset);

    /**
     * {@return the double value at the specified offset within the {@code
     * class} file}  Reads 8 bytes of value.
     * <p>
     * In the conversions, all NaN values of the {@code double} may or may not
     * be collapsed into a single {@linkplain Double#NaN "canonical" NaN value}.
     *
     * @param offset the offset within the {@code class} file
     */
    double readDouble(int offset);

    /**
     * {@return a copy of the bytes at the specified range in the {@code class}
     * file}
     *
     * @param offset the offset within the {@code class} file
     * @param len the length of the range
     */
    byte[] readBytes(int offset, int len);

    /**
     * Copy a range of bytes from the {@code class} file to a {@link BufWriter}.
     *
     * @param buf the {@linkplain BufWriter}
     * @param offset the offset within the {@code class} file
     * @param len the length of the range
     */
    void copyBytesTo(BufWriter buf, int offset, int len);
}
