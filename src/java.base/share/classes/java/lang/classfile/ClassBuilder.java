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
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.AccessFlagsImpl;
import jdk.internal.classfile.impl.ChainedClassBuilder;
import jdk.internal.classfile.impl.DirectClassBuilder;
import jdk.internal.classfile.impl.Util;

/**
 * A builder for a {@code class} file.  {@link ClassFile} provides different
 * {@code build} methods that accept handlers to configure such a builder;
 * {@link ClassFile#build(ClassDesc, Consumer)} suffices for basic usage, while
 * {@link ClassFile#build(ClassEntry, ConstantPoolBuilder, Consumer)} allows
 * fine-grained control over {@linkplain ClassFileBuilder#constantPool() the
 * constant pool}.
 * <p>
 * Refer to {@link ClassFileBuilder} for general guidance and caution around
 * the use of builders for structures in the {@code class} file format.
 *
 * @see ClassFile#build(ClassEntry, ConstantPoolBuilder, Consumer)
 * @see ClassModel
 * @see ClassTransform
 * @since 24
 */
public sealed interface ClassBuilder
        extends ClassFileBuilder<ClassElement, ClassBuilder>
        permits ChainedClassBuilder, DirectClassBuilder {

    /**
     * Sets the version of this class.
     *
     * @param major the major version number
     * @param minor the minor version number
     * @return this builder
     * @see ClassFileVersion
     */
    default ClassBuilder withVersion(int major, int minor) {
        return with(ClassFileVersion.of(major, minor));
    }

    /**
     * Sets the access flags of this class.
     *
     * @param flags the access flags, as a bit mask
     * @return this builder
     * @see AccessFlags
     * @see AccessFlag.Location#CLASS
     */
    default ClassBuilder withFlags(int flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.CLASS, flags));
    }

    /**
     * Sets the access flags of this class.
     *
     * @param flags the access flags, as flag enums
     * @return this builder
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#CLASS} location
     * @see AccessFlags
     * @see AccessFlag.Location#CLASS
     */
    default ClassBuilder withFlags(AccessFlag... flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.CLASS, flags));
    }

    /**
     * Sets the superclass of this class.
     *
     * @param superclassEntry the superclass
     * @return this builder
     * @see Superclass
     */
    default ClassBuilder withSuperclass(ClassEntry superclassEntry) {
        return with(Superclass.of(superclassEntry));
    }

    /**
     * Sets the superclass of this class.
     *
     * @param desc the superclass
     * @return this builder
     * @throws IllegalArgumentException if {@code desc} represents a primitive type
     * @see Superclass
     */
    default ClassBuilder withSuperclass(ClassDesc desc) {
        return withSuperclass(constantPool().classEntry(desc));
    }

    /**
     * Sets the interfaces of this class.
     *
     * @param interfaces the interfaces
     * @return this builder
     * @see Interfaces
     */
    default ClassBuilder withInterfaces(List<ClassEntry> interfaces) {
        return with(Interfaces.of(interfaces));
    }

    /**
     * Sets the interfaces of this class.
     *
     * @param interfaces the interfaces
     * @return this builder
     * @see Interfaces
     */
    default ClassBuilder withInterfaces(ClassEntry... interfaces) {
        return withInterfaces(List.of(interfaces));
    }

    /**
     * Sets the interfaces of this class.
     *
     * @param interfaces the interfaces
     * @return this builder
     * @throws IllegalArgumentException if any element of {@code interfaces} is primitive
     * @see Interfaces
     */
    default ClassBuilder withInterfaceSymbols(List<ClassDesc> interfaces) {
        return withInterfaces(Util.entryList(interfaces));
    }

    /**
     * Sets the interfaces of this class.
     *
     * @param interfaces the interfaces
     * @return this builder
     * @throws IllegalArgumentException if any element of {@code interfaces} is primitive
     * @see Interfaces
     */
    default ClassBuilder withInterfaceSymbols(ClassDesc... interfaces) {
        // list version does defensive copy
        return withInterfaceSymbols(Arrays.asList(interfaces));
    }

    /**
     * Adds a field.
     *
     * @param name the field name
     * @param descriptor the field descriptor string
     * @param handler handler to supply the contents of the field
     * @return this builder
     * @see FieldModel
     */
    ClassBuilder withField(Utf8Entry name,
                           Utf8Entry descriptor,
                           Consumer<? super FieldBuilder> handler);

    /**
     * Adds a field, with only access flags.
     *
     * @param name the field name
     * @param descriptor the field descriptor string
     * @param flags the access flags for this field, as a bit mask
     * @return this builder
     * @see FieldModel
     * @see FieldBuilder#withFlags(int)
     */
    default ClassBuilder withField(Utf8Entry name,
                                   Utf8Entry descriptor,
                                   int flags) {
        return withField(name, descriptor, Util.buildingFlags(flags));
    }

    /**
     * Adds a field.
     *
     * @param name the field name
     * @param descriptor the symbolic field descriptor
     * @param handler handler to supply the contents of the field
     * @return this builder
     * @see FieldModel
     */
    default ClassBuilder withField(String name,
                                   ClassDesc descriptor,
                                   Consumer<? super FieldBuilder> handler) {
        return withField(constantPool().utf8Entry(name),
                         constantPool().utf8Entry(descriptor),
                         handler);
    }

    /**
     * Adds a field, with only access flags.
     *
     * @param name the field name
     * @param descriptor the symbolic field descriptor
     * @param flags the access flags for this field, as a bit mask
     * @return this builder
     * @see FieldModel
     * @see FieldBuilder#withFlags(int)
     */
    default ClassBuilder withField(String name,
                                   ClassDesc descriptor,
                                   int flags) {
        return withField(constantPool().utf8Entry(name),
                         constantPool().utf8Entry(descriptor),
                         flags);
    }

    /**
     * Adds a field by transforming a field from another class.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * // @link substring=withField target="#withField(Utf8Entry, Utf8Entry, Consumer)" :
     * withField(field.fieldName(), field.fieldType(),
     *           fb -> fb.transform(field, transform)) // @link regex="transform(?=\()" target="FieldBuilder#transform"
     * }
     *
     * @param field the field to be transformed
     * @param transform the transform to apply to the field
     * @return this builder
     * @see FieldTransform
     */
    ClassBuilder transformField(FieldModel field, FieldTransform transform);

    /**
     * Adds a method.  The bit for {@link ClassFile#ACC_STATIC ACC_STATIC} flag
     * cannot be modified by the {@code handler} later, and must be set through
     * {@code methodFlags}.
     *
     * @param name the method name
     * @param descriptor the method descriptor
     * @param methodFlags the access flags as a bit mask, with the {@code
     *        ACC_STATIC} bit definitely set
     * @param handler handler to supply the contents of the method
     * @return this builder
     * @see MethodModel
     */
    ClassBuilder withMethod(Utf8Entry name,
                            Utf8Entry descriptor,
                            int methodFlags,
                            Consumer<? super MethodBuilder> handler);

    /**
     * Adds a method, with only access flags and a {@link CodeModel}.  The bit
     * for {@link ClassFile#ACC_STATIC ACC_STATIC} flag cannot be modified by
     * the {@code handler} later, and must be set through {@code methodFlags}.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * // @link substring=withMethod target="#withMethod(Utf8Entry, Utf8Entry, int, Consumer)" :
     * withMethod(name, descriptor, methodFlags, mb -> mb.withCode(handler)) // @link substring=withCode target="MethodBuilder#withCode"
     * }
     *
     * @param name the method name
     * @param descriptor the method descriptor
     * @param methodFlags the access flags as a bit mask, with the {@code
     *        ACC_STATIC} bit definitely set
     * @param handler handler to supply the contents of the method body
     * @return this builder
     * @see MethodModel
     */
    default ClassBuilder withMethodBody(Utf8Entry name,
                                        Utf8Entry descriptor,
                                        int methodFlags,
                                        Consumer<? super CodeBuilder> handler) {
        return withMethod(name, descriptor, methodFlags, Util.buildingCode(handler));
    }

    /**
     * Adds a method.  The bit for {@link ClassFile#ACC_STATIC ACC_STATIC} flag
     * cannot be modified by the {@code handler}, and must be set through
     * {@code methodFlags}.
     *
     * @param name the method name
     * @param descriptor the method descriptor
     * @param methodFlags the access flags as a bit mask, with the {@code
     *        ACC_STATIC} bit definitely set
     * @param handler handler to supply the contents of the method
     * @return this builder
     * @see MethodModel
     */
    default ClassBuilder withMethod(String name,
                                    MethodTypeDesc descriptor,
                                    int methodFlags,
                                    Consumer<? super MethodBuilder> handler) {
        return withMethod(constantPool().utf8Entry(name),
                          constantPool().utf8Entry(descriptor),
                          methodFlags,
                          handler);
    }

    /**
     * Adds a method, with only access flags and a {@link CodeModel}.  The bit
     * for {@link ClassFile#ACC_STATIC ACC_STATIC} flag cannot be modified by
     * the {@code handler}, and must be set through {@code methodFlags}.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * // @link substring=withMethod target="#withMethod(String, MethodTypeDesc, int, Consumer)" :
     * withMethod(name, descriptor, methodFlags, mb -> mb.withCode(handler)) // @link substring=withCode target="MethodBuilder#withCode"
     * }
     *
     * @param name the method name
     * @param descriptor the method descriptor
     * @param methodFlags the access flags as a bit mask, with the {@code
     *        ACC_STATIC} bit definitely set
     * @param handler handler to supply the contents of the method body
     * @return this builder
     * @see MethodModel
     */
    default ClassBuilder withMethodBody(String name,
                                        MethodTypeDesc descriptor,
                                        int methodFlags,
                                        Consumer<? super CodeBuilder> handler) {
        return withMethod(name, descriptor, methodFlags, Util.buildingCode(handler));
    }

    /**
     * Adds a method by transforming a method from another class.  The transform
     * cannot modify the {@link ClassFile#ACC_STATIC ACC_STATIC} flag of the
     * original method.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * // @link substring=withMethod target="#withMethod(Utf8Entry, Utf8Entry, int, Consumer)" :
     * withMethod(method.methodName(), method.methodType(), method.flags().flagMask(),
     *            mb -> mb.transform(method, transform)) // @link regex="transform(?=\()" target="MethodBuilder#transform"
     * }
     *
     * @param method the method to be transformed
     * @param transform the transform to apply to the method
     * @return this builder
     * @see MethodTransform
     */
    ClassBuilder transformMethod(MethodModel method, MethodTransform transform);
}
