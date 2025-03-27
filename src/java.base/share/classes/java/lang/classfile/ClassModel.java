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

import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.ClassImpl;

/**
 * Models a {@code class} file.  A {@code class} file can be viewed as a
 * {@linkplain CompoundElement composition} of {@link ClassElement}s, or by
 * random access via accessor methods if only specific parts of the {@code
 * class} file is needed.
 * <p>
 * Use {@link ClassFile#parse(byte[])}, which parses the binary data of a {@code
 * class} file into a model, to obtain a {@code ClassModel}.
 * <p>
 * To construct a {@code class} file, use {@link ClassFile#build(ClassDesc,
 * Consumer)}.  {@link ClassFile#transformClass(ClassModel, ClassTransform)}
 * allows creating a new class by selectively processing the original class
 * elements and directing the results to a class builder.
 * <p>
 * A class holds attributes, most of which are accessible as member elements.
 * {@link BootstrapMethodsAttribute} can only be accessed via {@linkplain
 * AttributedElement explicit attribute reading}, as it is modeled as part of
 * the {@linkplain #constantPool() constant pool}.
 *
 * @see ClassFile#parse(byte[])
 * @see ClassTransform
 * @jvms 4.1 The {@code ClassFile} Structure
 * @since 24
 */
public sealed interface ClassModel
        extends CompoundElement<ClassElement>, AttributedElement
        permits ClassImpl {

    /**
     * {@return the constant pool for this class}
     *
     * @see ConstantPoolBuilder#of(ClassModel)
     */
    ConstantPool constantPool();

    /**
     * {@return the access flags}
     *
     * @see AccessFlag.Location#CLASS
     */
    AccessFlags flags();

    /** {@return the constant pool entry describing the name of this class} */
    ClassEntry thisClass();

    /**
     * {@return the major version of this class}  It is in the range of unsigned
     * short, {@code [0, 65535]}.
     *
     * @see ClassFileVersion
     */
    int majorVersion();

    /**
     * {@return the minor version of this class}  It is in the range of unsigned
     * short, {@code [0, 65535]}.
     *
     * @see ClassFileVersion
     */
    int minorVersion();

    /** {@return the fields of this class} */
    List<FieldModel> fields();

    /** {@return the methods of this class} */
    List<MethodModel> methods();

    /**
     * {@return the superclass of this class, if there is one}
     * This {@code class} file may have no superclass if this represents a
     * {@linkplain #isModuleInfo() module descriptor} or the {@link Object}
     * class; otherwise, it must have a superclass.  If this is an interface,
     * the superclass must be {@link Object}.
     *
     * @see Superclass
     */
    Optional<ClassEntry> superclass();

    /**
     * {@return the interfaces implemented by this class}
     *
     * @see Interfaces
     */
    List<ClassEntry> interfaces();

    /**
     * {@return whether this {@code class} file is a module descriptor}
     *
     * @see ClassFile#buildModule(ModuleAttribute, Consumer)
     */
    boolean isModuleInfo();
}
