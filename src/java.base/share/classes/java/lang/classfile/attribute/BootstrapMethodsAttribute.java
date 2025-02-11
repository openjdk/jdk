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
import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#bootstrapMethods() BootstrapMethods} attribute
 * (JVMS {@jvms 4.7.23}), which stores symbolic information for the execution of
 * bootstrap methods, used by dynamically-computed call sites and constants.
 * It is logically a part of the constant pool of a {@code class} file and thus
 * not delivered in {@link ClassModel} traversal; its elements are accessible
 * through {@link ConstantPool}.
 * <p>
 * This attribute only appears on classes, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a class.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * This attribute cannot be constructed directly; its entries can be constructed
 * through {@link ConstantPoolBuilder#bsmEntry}, resulting in at most one
 * attribute instance in the built {@code class} file.
 * <p>
 * The attribute was introduced in the Java SE Platform version 7, major version
 * {@value ClassFile#JAVA_7_VERSION}.
 *
 * @see Attributes#bootstrapMethods()
 * @see java.lang.invoke##bsm Execution of bootstrap methods
 * @jvms 4.7.23 The {@code BootstrapMethods} Attribute
 * @since 24
 */
public sealed interface BootstrapMethodsAttribute
        extends Attribute<BootstrapMethodsAttribute>
        permits BoundAttribute.BoundBootstrapMethodsAttribute,
                UnboundAttribute.EmptyBootstrapAttribute {

    /**
     * {@return the elements of the bootstrap method table}
     */
    List<BootstrapMethodEntry> bootstrapMethods();

    /**
     * {@return the size of the bootstrap methods table}
     */
    int bootstrapMethodsSize();

    // No factories; BMA is generated as part of constant pool
}
