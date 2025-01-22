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
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#stackMapTable() StackMapTable} attribute (JVMS
 * {@jvms 4.7.4}), which is used for verification by type checking ({@jvms
 * 4.10.1}).
 * <p>
 * This attribute is not delivered in the traversal of a {@link CodeAttribute},
 * but instead automatically generated upon {@code class} file writing.
 * Advanced users can supply their own stack maps according to the {@link
 * ClassFile.StackMapsOption}.
 * <p>
 * This attribute only appears on {@code Code} attributes, and does not permit
 * {@linkplain AttributeMapper#allowMultiple multiple instances} in a {@code
 * Code} attribute.  It has a data dependency on {@linkplain
 * AttributeStability#LABELS labels} in the {@code code} array.
 * <p>
 * This attribute was introduced in the Java SE Platform version 6, major
 * version {@value ClassFile#JAVA_6_VERSION}.
 *
 * @see Attributes#stackMapTable()
 * @see DiscontinuedInstruction.JsrInstruction
 * @see DiscontinuedInstruction.RetInstruction
 * @jvms 4.7.4 The {@code StackMapTable} Attribute
 * @jvms 4.10.1 Verification by Type Checking
 * @since 24
 */
public sealed interface StackMapTableAttribute
        extends Attribute<StackMapTableAttribute>, CodeElement
        permits BoundAttribute.BoundStackMapTableAttribute, UnboundAttribute.UnboundStackMapTableAttribute {

    /**
     * {@return the stack map frames}
     */
    List<StackMapFrameInfo> entries();

    /**
     * {@return a stack map table attribute}
     *
     * @param entries the stack map frames
     */
    public static StackMapTableAttribute of(List<StackMapFrameInfo> entries) {
        return new UnboundAttribute.UnboundStackMapTableAttribute(entries);
    }
}
