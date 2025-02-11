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

package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#sourceId() SourceID} attribute, which records
 * the last modified time of the source file from which this {@code class} file
 * was compiled.
 * <p>
 * This attribute only appears on classes, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a class.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * This attribute is not predefined in the Java SE Platform.  This is a
 * JDK-specific nonstandard attribute produced by the reference implementation
 * of the system Java compiler, defined by the {@code jdk.compiler} module.
 *
 * @see Attributes#sourceId()
 * @see CompilationIDAttribute
 * @see CharacterRangeTableAttribute
 * @since 24
 */
public sealed interface SourceIDAttribute
        extends Attribute<SourceIDAttribute>, ClassElement
        permits BoundAttribute.BoundSourceIDAttribute, UnboundAttribute.UnboundSourceIDAttribute {

    /**
     * {@return the source id}  The source id is the last modified time of the
     * source file (as reported by the file system, in milliseconds) when this
     * {@code class} file is compiled.
     */
    Utf8Entry sourceId();

    /**
     * {@return a {@code SourceID} attribute}
     *
     * @param sourceId the source id
     */
    static SourceIDAttribute of(Utf8Entry sourceId) {
        return new UnboundAttribute.UnboundSourceIDAttribute(sourceId);
    }

    /**
     * {@return a {@code SourceID} attribute}
     *
     * @param sourceId the source id
     */
    static SourceIDAttribute of(String sourceId) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(sourceId));
    }
}
