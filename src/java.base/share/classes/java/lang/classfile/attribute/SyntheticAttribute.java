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

package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassElement;
import java.lang.classfile.FieldElement;
import java.lang.classfile.MethodElement;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@code Synthetic} attribute (JVMS {@jvms 4.7.8}), which can appear on
 * classes, methods, and fields.  Delivered as a  {@link ClassElement},
 * {@link MethodElement}, or  {@link FieldElement} when traversing the elements
 * of a corresponding model.
 * <p>
 * The attribute permits multiple instances in a given location.
 *
 * @since 24
 */
public sealed interface SyntheticAttribute
        extends Attribute<SyntheticAttribute>,
                ClassElement, MethodElement, FieldElement
        permits BoundAttribute.BoundSyntheticAttribute, UnboundAttribute.UnboundSyntheticAttribute {

    /**
     * {@return a {@code Synthetic} attribute}
     */
    static SyntheticAttribute of() {
        return new UnboundAttribute.UnboundSyntheticAttribute();
    }
}
