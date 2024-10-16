/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.javac.PreviewFeature;

/**
 * Models a non-standard attribute of a classfile.  Clients should extend
 * this class to provide an implementation class for non-standard attributes,
 * and provide an {@link AttributeMapper} to mediate between the classfile
 * format and the {@linkplain CustomAttribute} representation.
 * @param <T> the custom attribute type
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public abstract non-sealed class CustomAttribute<T extends CustomAttribute<T>>
        implements Attribute<T>, CodeElement, ClassElement, MethodElement, FieldElement {

    private final AttributeMapper<T> mapper;

    /**
     * Construct a {@linkplain CustomAttribute}.
     * @param mapper the attribute mapper
     */
    protected CustomAttribute(AttributeMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public final AttributeMapper<T> attributeMapper() {
        return mapper;
    }

    @Override
    public final String attributeName() {
        return mapper.name();
    }

    @Override
    public String toString() {
        return String.format("CustomAttribute[name=%s]", mapper.name());
    }
}
