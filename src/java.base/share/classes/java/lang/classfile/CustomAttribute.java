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

import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.TemporaryConstantPool;

/**
 * Models a user-defined attribute in a {@code class} file.  API models for
 * user-defined attributes should extend this class.  A user-defined attribute
 * should also have an {@link AttributeMapper} defined, which will be returned
 * by {@link #attributeMapper}, and registered to the {@link
 * ClassFile.AttributeMapperOption} so the user-defined attributes can be read.
 * <p>
 * Accessor methods on user-defined attributes read from {@code class} files
 * may throw {@link IllegalArgumentException} if the attribute model is lazily
 * evaluated, and the evaluation encounters malformed {@code class} file format
 * for the attribute.
 *
 * @param <T> the custom attribute type
 * @see java.lang.classfile.attribute
 * @since 24
 */
public abstract non-sealed class CustomAttribute<T extends CustomAttribute<T>>
        implements Attribute<T>, CodeElement, ClassElement, MethodElement, FieldElement {

    private final AttributeMapper<T> mapper;

    /**
     * Constructor for subclasses to call.
     *
     * @param mapper the attribute mapper
     */
    protected CustomAttribute(AttributeMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public final AttributeMapper<T> attributeMapper() {
        return mapper;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * The default implementation returns a {@code Utf8Entry} suitable for
     * writing only, which may be {@linkplain PoolEntry##unbound unbound}.
     * Subclasses representing attributes read from {@code class} files must
     * override this method.
     *
     * @see AttributeMapper#readAttribute
     */
    @Override
    public Utf8Entry attributeName() {
        return TemporaryConstantPool.INSTANCE.utf8Entry(mapper.name());
    }

    @Override
    public String toString() {
        return String.format("CustomAttribute[name=%s]", mapper.name());
    }
}
