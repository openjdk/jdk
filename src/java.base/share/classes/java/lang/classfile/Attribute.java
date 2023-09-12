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

import jdk.internal.classfile.attribute.AnnotationDefaultAttribute;
import jdk.internal.classfile.attribute.BootstrapMethodsAttribute;
import jdk.internal.classfile.attribute.CharacterRangeTableAttribute;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.attribute.CompilationIDAttribute;
import jdk.internal.classfile.attribute.ConstantValueAttribute;
import jdk.internal.classfile.attribute.DeprecatedAttribute;
import jdk.internal.classfile.attribute.EnclosingMethodAttribute;
import jdk.internal.classfile.attribute.ExceptionsAttribute;
import jdk.internal.classfile.attribute.InnerClassesAttribute;
import jdk.internal.classfile.attribute.LineNumberTableAttribute;
import jdk.internal.classfile.attribute.LocalVariableTableAttribute;
import jdk.internal.classfile.attribute.LocalVariableTypeTableAttribute;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.ModuleHashesAttribute;
import jdk.internal.classfile.attribute.ModuleMainClassAttribute;
import jdk.internal.classfile.attribute.ModulePackagesAttribute;
import jdk.internal.classfile.attribute.ModuleResolutionAttribute;
import jdk.internal.classfile.attribute.ModuleTargetAttribute;
import jdk.internal.classfile.attribute.NestHostAttribute;
import jdk.internal.classfile.attribute.NestMembersAttribute;
import jdk.internal.classfile.attribute.PermittedSubclassesAttribute;
import jdk.internal.classfile.attribute.RecordAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.SignatureAttribute;
import jdk.internal.classfile.attribute.SourceDebugExtensionAttribute;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import jdk.internal.classfile.attribute.SourceIDAttribute;
import jdk.internal.classfile.attribute.StackMapTableAttribute;
import jdk.internal.classfile.attribute.SyntheticAttribute;
import jdk.internal.classfile.attribute.UnknownAttribute;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models a classfile attribute {@jvms 4.7}.  Many, though not all, subtypes of
 * {@linkplain Attribute} will implement {@link ClassElement}, {@link
 * MethodElement}, {@link FieldElement}, or {@link CodeElement}; attributes that
 * are also elements will be delivered when traversing the elements of the
 * corresponding model type. Additionally, all attributes are accessible
 * directly from the corresponding model type through {@link
 * AttributedElement#findAttribute(AttributeMapper)}.
 * @param <A> the attribute type
 */
public sealed interface Attribute<A extends Attribute<A>>
        extends WritableElement<A>
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
                UnknownAttribute, BoundAttribute, UnboundAttribute {
    /**
     * {@return the name of the attribute}
     */
    String attributeName();

    /**
     * {@return the {@link AttributeMapper} associated with this attribute}
     */
    AttributeMapper<A> attributeMapper();
}
