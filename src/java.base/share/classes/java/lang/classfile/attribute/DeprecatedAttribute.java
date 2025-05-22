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

import java.lang.classfile.*;
import java.lang.classfile.AttributeMapper.AttributeStability;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#deprecated() Deprecated} attribute (JVMS {@jvms
 * 4.7.15}), which indicates this structure has been superseded.
 * <p>
 * This attribute can appear on classes, methods, and fields, and permits
 * {@linkplain AttributeMapper#allowMultiple multiple instances} in a structure.
 * It has {@linkplain AttributeStability#STATELESS no data dependency}.
 * <p>
 * This attribute was introduced in the Java SE Platform version 1.1, major
 * version {@value ClassFile#JAVA_1_VERSION}.
 *
 * @apiNote
 * When this attribute is present, the {@link Deprecated} annotation should
 * also be present in the {@link RuntimeVisibleAnnotationsAttribute
 * RuntimeVisibleAnnotations} attribute to provide more obvious alerts.
 * The reference implementation of the system Java compiler emits this attribute
 * without the annotation when a {@code @deprecated} tag is present in the
 * documentation comments without the annotation.
 *
 * @see Attributes#deprecated()
 * @see Deprecated
 * @jvms 4.7.15 The {@code Deprecated} Attribute
 * @since 24
 */
public sealed interface DeprecatedAttribute
        extends Attribute<DeprecatedAttribute>,
                ClassElement, MethodElement, FieldElement
        permits BoundAttribute.BoundDeprecatedAttribute,
                UnboundAttribute.UnboundDeprecatedAttribute {

    /**
     * {@return a {@code Deprecated} attribute}
     */
    static DeprecatedAttribute of() {
        return new UnboundAttribute.UnboundDeprecatedAttribute();
    }
}
