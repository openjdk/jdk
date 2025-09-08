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

import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models an attribute (JVMS {@jvms 4.7}) in the {@code class} file format.
 * Attributes exist on certain {@code class} file structures modeled by {@link
 * AttributedElement}, which provides basic read access to the attributes.
 * <p>
 * This sealed interface hierarchy includes attributes predefined in the JVMS
 * and JDK-specific nonstandard attributes.  Their {@linkplain #attributeMapper()
 * mappers} are available in {@link Attributes}.  Two special subtypes of {@code
 * Attribute} are {@link CustomAttribute}, which all user-defined attributes
 * should extend from, and {@link UnknownAttribute}, representing attributes
 * read from {@code class} file but are not recognized by the {@link
 * ClassFile.AttributeMapperOption}.
 * <p>
 * Attributes are read through {@link AttributedElement} or element traversal of
 * a {@link CompoundElement}; they are written through {@link ClassFileBuilder}.
 * See {@linkplain java.lang.classfile.attribute##reading Reading Attributes}
 * and {@linkplain java.lang.classfile.attribute##writing Writing Attributes}
 * for more details.
 *
 * @param <A> the attribute type
 * @see java.lang.classfile.attribute
 * @see AttributeMapper
 * @see AttributedElement
 * @see CustomAttribute
 * @see UnknownAttribute
 * @jvms 4.7 Attributes
 * @sealedGraph
 * @since 24
 */
public sealed interface Attribute<A extends Attribute<A>>
        extends ClassFileElement
        permits AnnotationDefaultAttribute, BootstrapMethodsAttribute,
                CharacterRangeTableAttribute, CodeAttribute, CompilationIDAttribute,
                ConstantValueAttribute, DeprecatedAttribute, EnclosingMethodAttribute,
                ExceptionsAttribute, InnerClassesAttribute, LineNumberTableAttribute,
                LocalVariableTableAttribute, LocalVariableTypeTableAttribute,
                MethodParametersAttribute, ModuleAttribute, ModuleHashesAttribute,
                ModuleMainClassAttribute, ModulePackagesAttribute, ModuleResolutionAttribute,
                ModuleTargetAttribute, NestHostAttribute, NestMembersAttribute,
                PermittedSubclassesAttribute,
                RecordAttribute, RuntimeInvisibleAnnotationsAttribute,
                RuntimeInvisibleParameterAnnotationsAttribute, RuntimeInvisibleTypeAnnotationsAttribute,
                RuntimeVisibleAnnotationsAttribute, RuntimeVisibleParameterAnnotationsAttribute,
                RuntimeVisibleTypeAnnotationsAttribute, SignatureAttribute,
                SourceDebugExtensionAttribute, SourceFileAttribute, SourceIDAttribute,
                StackMapTableAttribute, SyntheticAttribute,
                UnknownAttribute, BoundAttribute, UnboundAttribute, CustomAttribute {
    /**
     * {@return the name of the attribute}  The {@linkplain
     * Utf8Entry#stringValue() string value} of the name is equivalent to the
     * value of {@link AttributeMapper#name() attributeMapper().name()}.
     * <p>
     * If this attribute is read from a {@code class} file, this method returns
     * the {@link Utf8Entry} indicating the attribute name in the {@code class}
     * file.
     */
    Utf8Entry attributeName();

    /**
     * {@return the {@link AttributeMapper} associated with this attribute}
     */
    AttributeMapper<A> attributeMapper();
}
