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
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.LocalVariable;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#localVariableTable() LocalVariableTable}
 * attribute (JVMS {@jvms 4.7.13}), which records debug information about local
 * variables.  Its entries are delivered as {@link LocalVariable}s when
 * traversing the elements of a {@link CodeModel}, which is toggled by {@link
 * ClassFile.DebugElementsOption}.
 * <p>
 * This attribute only appears on {@code Code} attributes, and permits {@linkplain
 * AttributeMapper#allowMultiple() multiple instances} in a {@code Code}
 * attribute.  It has a data dependency on {@linkplain AttributeStability#LABELS
 * labels}.
 * <p>
 * This attribute cannot be sent to a {@link CodeBuilder}; its entries can be
 * constructed with {@link LocalVariable}, resulting in at most one attribute
 * instance in the built {@code Code} attribute.
 * <p>
 * The attribute was introduced in the Java Platform version 1.0.2, major
 * version {@value ClassFile#JAVA_1_VERSION}.
 *
 * @apiNote
 * Generic local variable types and potentially annotated use of those types are
 * defined by {@link LocalVariableTypeTableAttribute} and {@link
 * RuntimeVisibleTypeAnnotationsAttribute} respectively, which requires this
 * attribute to be present.
 *
 * @see Attributes#localVariableTable()
 * @jvms 4.7.13 The {@code LocalVaribleTable} Attribute
 * @since 24
 */
public sealed interface LocalVariableTableAttribute
        extends Attribute<LocalVariableTableAttribute>
        permits BoundAttribute.BoundLocalVariableTableAttribute, UnboundAttribute.UnboundLocalVariableTableAttribute {

    /**
     * {@return debug information for the local variables in this method}
     */
    List<LocalVariableInfo> localVariables();

    /**
     * {@return a {@code LocalVariableTable} attribute}
     *
     * @apiNote
     * The created attribute cannot be written to a {@link CodeBuilder}.  Use
     * {@link CodeBuilder#localVariable CodeBuilder::localVariable} instead.
     *
     * @param locals the local variable descriptions
     */
    static LocalVariableTableAttribute of(List<LocalVariableInfo> locals) {
        return new UnboundAttribute.UnboundLocalVariableTableAttribute(locals);
    }
}
