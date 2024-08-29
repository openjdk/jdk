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

import jdk.internal.javac.PreviewFeature;

/**
 * Models an entry in the constant pool of a classfile.
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface PoolEntry
        permits AnnotationConstantValueEntry, DynamicConstantPoolEntry,
                LoadableConstantEntry, MemberRefEntry, ModuleEntry, NameAndTypeEntry,
                PackageEntry {

    /** The value of constant pool {@linkplain #tag tag} CLASS. */
    int TAG_CLASS = 7;

    /** The value of constant pool {@linkplain #tag tag} CONSTANTDYNAMIC. */
    int TAG_CONSTANTDYNAMIC = 17;

    /** The value of constant pool {@linkplain #tag tag} DOUBLE. */
    int TAG_DOUBLE = 6;

    /** The value of constant pool {@linkplain #tag tag} FIELDREF. */
    int TAG_FIELDREF = 9;

    /** The value of constant pool {@linkplain #tag tag} FLOAT. */
    int TAG_FLOAT = 4;

    /** The value of constant pool {@linkplain #tag tag} INTEGER. */
    int TAG_INTEGER = 3;

    /** The value of constant pool {@linkplain #tag tag} INTERFACEMETHODREF. */
    int TAG_INTERFACEMETHODREF = 11;

    /** The value of constant pool {@linkplain #tag tag} INVOKEDYNAMIC. */
    int TAG_INVOKEDYNAMIC = 18;

    /** The value of constant pool {@linkplain #tag tag} LONG. */
    int TAG_LONG = 5;

    /** The value of constant pool {@linkplain #tag tag} METHODHANDLE. */
    int TAG_METHODHANDLE = 15;

    /** The value of constant pool {@linkplain #tag tag} METHODREF. */
    int TAG_METHODREF = 10;

    /** The value of constant pool {@linkplain #tag tag} METHODTYPE. */
    int TAG_METHODTYPE = 16;

    /** The value of constant pool {@linkplain #tag tag} MODULE. */
    int TAG_MODULE = 19;

    /** The value of constant pool {@linkplain #tag tag} NAMEANDTYPE. */
    int TAG_NAMEANDTYPE = 12;

    /** The value of constant pool {@linkplain #tag tag} PACKAGE. */
    int TAG_PACKAGE = 20;

    /** The value of constant pool {@linkplain #tag tag} STRING. */
    int TAG_STRING = 8;

    /** The value of constant pool {@linkplain #tag tag} UNICODE. */
    int TAG_UNICODE = 2;

    /** The value of constant pool {@linkplain #tag tag} UTF8. */
    int TAG_UTF8 = 1;

    /**
     * {@return the constant pool this entry is from}
     */
    ConstantPool constantPool();

    /**
     * {@return the constant pool tag that describes the type of this entry}
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
