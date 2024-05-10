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
package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

import java.util.List;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the {@code LocalVariableTable} attribute {@jvms 4.7.13}, which can appear
 * on a {@code Code} attribute, and records debug information about local
 * variables.
 * Delivered as a {@link java.lang.classfile.instruction.LocalVariable} when traversing the
 * elements of a {@link java.lang.classfile.CodeModel}, according to the setting of the
 * {@link java.lang.classfile.ClassFile.DebugElementsOption} option.
 * <p>
 * The attribute permits multiple instances in a given location.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface LocalVariableTableAttribute
        extends Attribute<LocalVariableTableAttribute>
        permits BoundAttribute.BoundLocalVariableTableAttribute, UnboundAttribute.UnboundLocalVariableTableAttribute {

    /**
     * {@return debug information for the local variables in this method}
     */
    List<LocalVariableInfo> localVariables();

    /**
     * {@return a {@code LocalVariableTable} attribute}
     * @param locals the local variable descriptions
     */
    static LocalVariableTableAttribute of(List<LocalVariableInfo> locals) {
        return new UnboundAttribute.UnboundLocalVariableTableAttribute(locals);
    }
}
