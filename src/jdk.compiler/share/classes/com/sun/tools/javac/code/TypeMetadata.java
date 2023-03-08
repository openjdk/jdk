/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;

/**
 * A type metadata is an object that can be stapled on a type. This is typically done using
 * {@link Type#addMetadata(TypeMetadata)}. Metadata associated to a type can also be removed,
 * typically using {@link Type#dropMetadata(Class)}. To drop <em>all</em> metadata from a given type,
 * the {@link Type#baseType()} method can also be used. This can be useful when comparing two
 * using reference equality (see also {@link Type#equalsIgnoreMetadata(Type)}).
 * <p>
 * There are no constraints on how a type metadata should be defined. Typically, a type
 * metadata will be defined as a small record, storing additional information (see {@link ConstantValue}).
 * In other cases, type metadata can be mutable and support complex state transitions
 * (see {@link Annotations}).
 * <p>
 * The only invariant the implementation requires is that there is only <em>one</em> metadata
 * of a given kind attached to a type, as this makes accessing and dropping metadata simpler.
 * If clients wish to store multiple metadata values that are logically related, they should
 * define a metadata type that collects such values in e.g. a list.
 */
public sealed interface TypeMetadata {

    /**
     * A type metadata object holding type annotations. This metadata needs to be mutable,
     * because type annotations are sometimes set in two steps. That is, a type can be created with
     * an empty set of annotations (e.g. during member enter). At some point later, the type
     * is then updated to contain the correct annotations. At this point we need to augment
     * the existing type, as the type has already been saved inside other symbols.
     */
    class Annotations implements TypeMetadata {
        private List<Attribute.TypeCompound> annos;

        public static final List<Attribute.TypeCompound> TO_BE_SET = List.nil();

        public Annotations() {
            this.annos = TO_BE_SET;
        }

        public Annotations(List<Attribute.TypeCompound> annos) {
            this.annos = annos;
        }

        public List<Attribute.TypeCompound> getAnnotations() {
            return annos;
        }

        public void setAnnotations(List<TypeCompound> annos) {
            Assert.check(this.annos == TO_BE_SET);
            this.annos = annos;
        }
    }

    /**
     * A type metadata holding a constant value. This can be used to describe constant types,
     * such as the type of a string literal, or that of a numeric constant.
     */
    record ConstantValue(Object value) implements TypeMetadata { }
}
