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
package jdk.classfile;

import jdk.classfile.attribute.CompilationIDAttribute;
import jdk.classfile.attribute.DeprecatedAttribute;
import jdk.classfile.attribute.EnclosingMethodAttribute;
import jdk.classfile.attribute.InnerClassesAttribute;
import jdk.classfile.attribute.ModuleAttribute;
import jdk.classfile.attribute.ModuleHashesAttribute;
import jdk.classfile.attribute.ModuleMainClassAttribute;
import jdk.classfile.attribute.ModulePackagesAttribute;
import jdk.classfile.attribute.ModuleResolutionAttribute;
import jdk.classfile.attribute.ModuleTargetAttribute;
import jdk.classfile.attribute.NestHostAttribute;
import jdk.classfile.attribute.NestMembersAttribute;
import jdk.classfile.attribute.PermittedSubclassesAttribute;
import jdk.classfile.attribute.RecordAttribute;
import jdk.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.SignatureAttribute;
import jdk.classfile.attribute.SourceDebugExtensionAttribute;
import jdk.classfile.attribute.SourceFileAttribute;
import jdk.classfile.attribute.SourceIDAttribute;
import jdk.classfile.attribute.SyntheticAttribute;
import jdk.classfile.attribute.UnknownAttribute;

/**
 * A {@link ClassfileElement} that can appear when traversing the elements
 * of a {@link ClassModel} or be presented to a {@link ClassBuilder}.
 */
public sealed interface ClassElement extends ClassfileElement
        permits AccessFlags, Superclass, Interfaces, ClassfileVersion,
                FieldModel, MethodModel,
                CustomAttribute, CompilationIDAttribute, DeprecatedAttribute,
                EnclosingMethodAttribute, InnerClassesAttribute,
                ModuleAttribute, ModuleHashesAttribute, ModuleMainClassAttribute,
                ModulePackagesAttribute, ModuleResolutionAttribute, ModuleTargetAttribute,
                NestHostAttribute, NestMembersAttribute, PermittedSubclassesAttribute,
                RecordAttribute,
                RuntimeInvisibleAnnotationsAttribute, RuntimeInvisibleTypeAnnotationsAttribute,
                RuntimeVisibleAnnotationsAttribute, RuntimeVisibleTypeAnnotationsAttribute,
                SignatureAttribute, SourceDebugExtensionAttribute,
                SourceFileAttribute, SourceIDAttribute, SyntheticAttribute, UnknownAttribute {
}
