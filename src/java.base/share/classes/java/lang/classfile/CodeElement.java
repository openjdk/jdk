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

import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.attribute.UnknownAttribute;

/**
 * Marker interface for a member element of a {@link CodeModel}.  Such an
 * element can appear when traversing a {@link CodeModel} unless otherwise
 * specified, be supplied to a {@link CodeBuilder}, and be processed by a
 * {@link CodeTransform}.
 * <p>
 * Code elements can be categorized into {@link Instruction}, {@link
 * PseudoInstruction}, and {@link Attribute}.  Unlike in other {@link
 * CompoundElement}, the order of elements for all {@link Instruction}s and some
 * {@link PseudoInstruction}s is significant.
 *
 * @see ClassFileElement##membership Membership Elements
 * @see ClassElement
 * @see MethodElement
 * @see FieldElement
 * @sealedGraph
 * @since 24
 */
public sealed interface CodeElement extends ClassFileElement
        permits Instruction, PseudoInstruction,
                CustomAttribute, RuntimeVisibleTypeAnnotationsAttribute, RuntimeInvisibleTypeAnnotationsAttribute,
                StackMapTableAttribute, UnknownAttribute {
}
