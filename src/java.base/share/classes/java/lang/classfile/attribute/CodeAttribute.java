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

/**
 * Models the {@link Attributes#code() Code} attribute (JVMS {@jvms 4.7.3}),
 * which contains the bytecode of this method.
 * <p>
 * This attribute only appears on methods, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a method.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * This attribute was introduced in the Java Platform version 1.0.2, major
 * version {@value ClassFile#JAVA_1_VERSION}.
 *
 * @apiNote
 * {@code CodeAttribute} models properties of a {@code Code} attribute read
 * from {@code class} files.  General {@code class} file transformation should
 * process and traverse a {@link CodeModel} in the traversal of a {@link
 * MethodModel}, to support transformation of {@code Code} attributes currently
 * being built.
 *
 * @see Attributes#code()
 * @jvms 4.7.3 The {@code Code} Attribute
 * @since 24
 */
public sealed interface CodeAttribute extends Attribute<CodeAttribute>, CodeModel
        permits BoundAttribute.BoundCodeAttribute {

    /**
     * {@return the maximum size of the local variable table}
     */
    int maxLocals();

    /**
     * {@return the maximum size of the operand stack}
     */
    int maxStack();

    /**
     * {@return The length of the code array in bytes}
     */
    int codeLength();

    /**
     * {@return the bytes (bytecode) of the code array}
     */
    byte[] codeArray();

    /**
     * {@return the position of the {@code label} in the {@link #codeArray codeArray}}
     * The label represents a cursor pointing at immediately before the returned
     * index into the {@code code} array.
     *
     * @param label a marker for a position within this {@code CodeAttribute}
     * @throws IllegalArgumentException if the {@code label} is not from this attribute
     */
    int labelToBci(Label label);
}
