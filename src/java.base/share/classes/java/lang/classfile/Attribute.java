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
package java.lang.classfile;

import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.attribute.CharacterRangeTableAttribute;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.CompilationIDAttribute;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.DeprecatedAttribute;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.LocalVariableTypeTableAttribute;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleHashesAttribute;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.ModuleTargetAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.NestMembersAttribute;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.attribute.SourceIDAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.attribute.SyntheticAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a classfile attribute {@jvms 4.7}.  Many, though not all, subtypes of
 * {@linkplain Attribute} will implement {@link ClassElement}, {@link
 * MethodElement}, {@link FieldElement}, or {@link CodeElement}; attributes that
 * are also elements will be delivered when traversing the elements of the
 * corresponding model type. Additionally, all attributes are accessible
 * directly from the corresponding model type through {@link
 * AttributedElement#findAttribute(AttributeMapper)}.
 * @param <A> the attribute type
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
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
     * {@return the name of the attribute}
     */
    String attributeName();

    /**
     * {@return the {@link AttributeMapper} associated with this attribute}
     */
    AttributeMapper<A> attributeMapper();
}
