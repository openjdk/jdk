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

/**
 * Marker interface for structures with special capabilities in the {@code
 * class} file format. {@link AttributedElement} indicates a structure has
 * {@link Attribute}s.  {@link CompoundElement} indicates a structure can be
 * viewed as a composition of member structures, whose memberships are marked by
 * {@link ClassElement}, {@link MethodElement}, {@link FieldElement}, or {@link
 * CodeElement}.
 *
 * <h2 id="membership">Membership Elements</h2>
 * {@link ClassModel}, {@link MethodModel}, {@link FieldModel}, and {@link
 * CodeModel} each has a dedicated interface marking its member structures:
 * {@link ClassElement}, {@link MethodElement}, {@link FieldElement}, and
 * {@link CodeElement}.  They can be supplied to a {@link ClassBuilder}, a
 * {@link MethodBuilder}, a {@link FieldBuilder}, or a {@link CodeBuilder} to be
 * included as members of the built model.  Unless otherwise specified, these
 * structures are delivered during the {@linkplain CompoundElement traversal} of
 * the corresponding models.  Some of these elements may appear at most once or
 * exactly once in the traversal of the models; such elements have special
 * treatment by {@link ClassFileBuilder} and are specified in their modeling
 * interfaces.  If such elements appear multiple times during traversal, the
 * last occurrence should be used and all previous instances should be
 * discarded.
 * <p>
 * These membership element marker interfaces are sealed; future versions of the
 * Java SE Platform may define new elements to the sealed hierarchy when the
 * {@code class} file format for the Java Platform evolves.  Using an exhaustive
 * pattern matching switch over these hierarchies indicates the user only wish
 * the processing code to run on a specific version of Java Platform, and will
 * fail if unknown new elements are encountered.
 *
 * @sealedGraph
 * @since 24
 */
public sealed interface ClassFileElement
        permits AttributedElement, CompoundElement, Attribute,
                ClassElement, CodeElement, FieldElement, MethodElement {
}
