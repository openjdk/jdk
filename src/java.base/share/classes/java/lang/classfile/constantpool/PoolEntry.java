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
package java.lang.classfile.constantpool;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFileBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;

/**
 * Models an entry in the constant pool of a {@code class} file.  Entries are
 * read from {@code class} files, and can be created with a {@link
 * ConstantPoolBuilder} to write to {@code class} files.
 *
 * @implNote
 * <h2 id="unbound">Unbound Constant Pool Entries</h2>
 * Implementations may create unbound constant pool entries not belonging to
 * an actual constant pool.  They conveniently represent constant pool entries
 * referred by unbound {@linkplain Attribute attributes} not read from a {@code
 * class} file.  Their {@link #index() index()} return a non-positive invalid
 * value, and behaviors of their {@link #constantPool() constantPool()} are
 * unspecified.  They are considered alien to any {@linkplain
 * ClassFileBuilder#constantPool() contextual constant pool} and will be
 * converted when they are written to {@code class} files.
 *
 * @see ConstantPoolBuilder##alien Alien Constant Pool Entries
 * @sealedGraph
 * @since 24
 */
public sealed interface PoolEntry
        permits AnnotationConstantValueEntry, DynamicConstantPoolEntry,
                LoadableConstantEntry, MemberRefEntry, ModuleEntry, NameAndTypeEntry,
                PackageEntry {

    /** The {@linkplain #tag tag} for {@link ClassEntry CONSTANT_Class} constant kind. */
    int TAG_CLASS = 7;

    /** The {@linkplain #tag tag} for {@link DoubleEntry CONSTANT_Double} constant kind. */
    int TAG_DOUBLE = 6;

    /** The {@linkplain #tag tag} for {@link ConstantDynamicEntry CONSTANT_Dynamic} constant kind. */
    int TAG_DYNAMIC = 17;

    /** The {@linkplain #tag tag} for {@link FieldRefEntry CONSTANT_Fieldref} constant kind. */
    int TAG_FIELDREF = 9;

    /** The {@linkplain #tag tag} for {@link FloatEntry CONSTANT_Float} constant kind. */
    int TAG_FLOAT = 4;

    /** The {@linkplain #tag tag} for {@link IntegerEntry CONSTANT_Integer} constant kind. */
    int TAG_INTEGER = 3;

    /** The {@linkplain #tag tag} for {@link InterfaceMethodRefEntry CONSTANT_InterfaceMethodref} constant kind. */
    int TAG_INTERFACE_METHODREF = 11;

    /** The {@linkplain #tag tag} for {@link InvokeDynamicEntry CONSTANT_InvokeDynamic} constant kind. */
    int TAG_INVOKE_DYNAMIC = 18;

    /** The {@linkplain #tag tag} for {@link LongEntry CONSTANT_Long} constant kind. */
    int TAG_LONG = 5;

    /** The {@linkplain #tag tag} for {@link MethodHandleEntry CONSTANT_MethodHandle} constant kind. */
    int TAG_METHOD_HANDLE = 15;

    /** The {@linkplain #tag tag} for {@link MethodRefEntry CONSTANT_Methodref} constant kind. */
    int TAG_METHODREF = 10;

    /** The {@linkplain #tag tag} for {@link MethodTypeEntry CONSTANT_MethodType} constant kind. */
    int TAG_METHOD_TYPE = 16;

    /** The {@linkplain #tag tag} for {@link ModuleEntry CONSTANT_Module} constant kind. */
    int TAG_MODULE = 19;

    /** The {@linkplain #tag tag} for {@link NameAndTypeEntry CONSTANT_NameAndType} constant kind. */
    int TAG_NAME_AND_TYPE = 12;

    /** The {@linkplain #tag tag} for {@link PackageEntry CONSTANT_Package} constant kind. */
    int TAG_PACKAGE = 20;

    /** The {@linkplain #tag tag} for {@link StringEntry CONSTANT_String} constant kind. */
    int TAG_STRING = 8;

    /** The {@linkplain #tag tag} for {@link Utf8Entry CONSTANT_Utf8} constant kind. */
    int TAG_UTF8 = 1;

    /**
     * {@return the constant pool this entry is from}
     *
     * @apiNote
     * Given a {@link ConstantPoolBuilder} {@code builder} and a {@code
     * PoolEntry entry}, use {@link ConstantPoolBuilder#canWriteDirect
     * builder.canWriteDirect(entry.constantPool())} instead of object equality
     * of the constant pool to determine if an entry belongs to the builder.
     *
     * @see ##unbound Unbound Constant Pool Entries
     */
    ConstantPool constantPool();

    /**
     * {@return the constant pool tag that describes the type of this entry}
     *
     * @apiNote
     * {@code TAG_}-prefixed constants in this class, such as {@link #TAG_UTF8},
     * describe the possible return values of this method.
     */
    int tag();

    /**
     * {@return the index within the constant pool corresponding to this entry}
     * A valid index is always positive; if the index is non-positive, this
     * entry is {@linkplain ##unbound unbound}.
     *
     * @see ##unbound Unbound Constant Pool Entries
     */
    int index();

    /**
     * {@return the number of constant pool slots this entry consumes}
     * <p>
     * All pool entries except {@link LongEntry CONSTANT_Long} and {@link
     * DoubleEntry CONSTANT_Double} have width {@code 1}. These two exceptions
     * have width {@code 2}, and their subsequent indices at {@link #index()
     * index() + 1} are considered unusable.
     *
     * @apiNote
     * If this entry is {@linkplain LoadableConstantEntry loadable}, the width
     * of this entry does not decide if this entry should be loaded with {@link
     * Opcode#LDC ldc} or {@link Opcode#LDC2_W ldc2_w}.  For example, {@link
     * ConstantDynamicEntry} always has width {@code 1}, but it must be loaded
     * with {@code ldc2_w} if its {@linkplain ConstantDynamicEntry#typeKind()
     * type} is {@link TypeKind#LONG long} or {@link TypeKind#DOUBLE double}.
     * Use {@link LoadableConstantEntry#typeKind() typeKind().slotSize()} to
     * determine the loading instruction instead.
     *
     * @see ConstantPool##index Index in the Constant Pool
     */
    int width();
}
