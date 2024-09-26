/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Models an entry in the constant pool of a classfile.
 *
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
     */
    ConstantPool constantPool();

    /**
     * {@return the constant pool tag that describes the type of this entry}
     *
     * @apiNote
     * {@code TAG_}-prefixed constants in this class, such as {@link #TAG_UTF8},
     * describe the possible return values of this method.
     */
    byte tag();

    /**
     * {@return the index within the constant pool corresponding to this entry}
     */
    int index();

    /**
     * {@return the number of constant pool slots this entry consumes}
     */
    int width();
}
