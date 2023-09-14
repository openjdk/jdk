/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile;

import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.constantpool.ConstantPoolException;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import jdk.internal.classfile.constantpool.PackageEntry;
import jdk.internal.classfile.constantpool.PoolEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.ClassReaderImpl;

import java.util.Optional;
import java.util.function.Function;

/**
 * Supports reading from a classfile.  Methods are provided to read data of
 * various numeric types (e.g., {@code u2}, {@code u4}) at a given offset within
 * the classfile, copying raw bytes, and reading constant pool entries.
 * Encapsulates additional reading context such as mappers for custom attributes
 * and processing options.
 */
public sealed interface ClassReader extends ConstantPool
        permits ClassReaderImpl {

    // Processing context

    /**
     * {@return the table of custom attribute mappers}  This is derived from
     * the processing option {@link Classfile.AttributeMapperOption}.
     */
    Function<Utf8Entry, AttributeMapper<?>> customAttributes();

    // Class context

    /** {@return the access flags for the class, as a bit mask } */
    int flags();

    /** {@return the constant pool entry describing the name of class} */
    ClassEntry thisClassEntry();

    /** {@return the constant pool entry describing the name of the superclass, if any} */
    Optional<ClassEntry> superclassEntry();

    /** {@return the offset into the classfile of the {@code this_class} field} */
    int thisClassPos();

    /** {@return the length of the classfile, in bytes} */
    int classfileLength();

    // Buffer related

    /**
     * {@return the offset following the block of attributes starting at the
     * specified position}
     * @param offset the offset into the classfile at which the attribute block
     *               starts
     */
    int skipAttributeHolder(int offset);

    // Constant pool

    /**
     * {@return the UTF8 constant pool entry at the given index of the constant
     * pool}  The given index must correspond to a valid constant pool index
     * whose slot holds a UTF8 constant.
     * @param index the index into the constant pool
     */
    Utf8Entry utf8EntryByIndex(int index);

    /**
     * {@return the constant pool entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero
     */
    PoolEntry readEntry(int offset);

    /**
     * {@return the constant pool entry of a given type whose index is given
     * at the specified offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @param cls the entry type
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the entry is not of the given type
     */
    <T extends PoolEntry> T readEntry(int offset, Class<T> cls);

    /**
     * {@return the constant pool entry whose index is given at the specified
     * offset within the classfile, or null if the index at the specified
     * offset is zero}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size
     */
    PoolEntry readEntryOrNull(int offset);

    /**
     * {@return the UTF8 entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a UTF8 entry
     */
    Utf8Entry readUtf8Entry(int offset);

    /**
     * {@return the UTF8 entry whose index is given at the specified
     * offset within the classfile, or null if the index at the specified
     * offset is zero}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or the index does not correspond to
     *         a UTF8 entry
     */
    Utf8Entry readUtf8EntryOrNull(int offset);

    /**
     * {@return the module entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a module entry
     */
    ModuleEntry readModuleEntry(int offset);

    /**
     * {@return the package entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a package entry
     */
    PackageEntry readPackageEntry(int offset);

    /**
     * {@return the class entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a class entry
     */
    ClassEntry readClassEntry(int offset);

    /**
     * {@return the name-and-type entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a name-and-type entry
     */
    NameAndTypeEntry readNameAndTypeEntry(int offset);

    /**
     * {@return the method handle entry whose index is given at the specified
     * offset within the classfile}
     * @param offset the offset of the index within the classfile
     * @throws ConstantPoolException if the index is out of range of the
     *         constant pool size, or zero, or the index does not correspond to
     *         a method handle entry
     */
    MethodHandleEntry readMethodHandleEntry(int offset);

    /**
     * {@return the unsigned byte at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    int readU1(int offset);

    /**
     * {@return the unsigned short at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    int readU2(int offset);

    /**
     * {@return the signed byte at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    int readS1(int offset);

    /**
     * {@return the signed byte at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    int readS2(int offset);

    /**
     * {@return the signed int at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    int readInt(int offset);

    /**
     * {@return the signed long at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    long readLong(int offset);

    /**
     * {@return the float value at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    float readFloat(int offset);

    /**
     * {@return the double value at the specified offset within the classfile}
     * @param offset the offset within the classfile
     */
    double readDouble(int offset);

    /**
     * {@return a copy of the bytes at the specified range in the classfile}
     * @param offset the offset within the classfile
     * @param len the length of the range
     */
    byte[] readBytes(int offset, int len);

    /**
     * Copy a range of bytes from the classfile to a {@link BufWriter}
     *
     * @param buf the {@linkplain BufWriter}
     * @param offset the offset within the classfile
     * @param len the length of the range
     */
    void copyBytesTo(BufWriter buf, int offset, int len);

    /**
     * Compare a range of bytes from the classfile to a range of bytes within
     * a {@link BufWriter}.
     *
     * @param bufWriter the {@linkplain BufWriter}
     * @param bufWriterOffset the offset within the {@linkplain BufWriter}
     * @param classReaderOffset the offset within the classfile
     * @param length the length of the range
     * @return whether the two ranges were identical
     */
    boolean compare(BufWriter bufWriter,
                    int bufWriterOffset,
                    int classReaderOffset,
                    int length);
}
