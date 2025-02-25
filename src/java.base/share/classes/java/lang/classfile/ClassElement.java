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

/**
 * Marker interface for a member element of a {@link ClassModel}.  Such an
 * element can appear when traversing a {@link ClassModel} unless otherwise
 * specified, be supplied to a {@link ClassBuilder}, and be processed by a
 * {@link ClassTransform}.
 * <p>
 * {@link AccessFlags}, and {@link ClassFileVersion} are member elements of a
 * class that appear exactly once during the traversal of a {@link ClassModel}.
 * {@link Superclass} and {@link Interfaces} may be absent or appear at most
 * once.  A {@link ClassBuilder} may provide an alternative superclass if it is
 * not defined but required.
 *
 * @see ClassFileElement##membership Membership Elements
 * @see MethodElement
 * @see FieldElement
 * @see CodeElement
 * @sealedGraph
 * @since 24
 */
public sealed interface ClassElement extends ClassFileElement
        permits AccessFlags, Superclass, Interfaces, ClassFileVersion,
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
