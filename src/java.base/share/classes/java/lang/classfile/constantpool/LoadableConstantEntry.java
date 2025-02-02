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
package java.lang.classfile.constantpool;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.constant.ConstantDesc;

/**
 * Marker interface for constant pool entries suitable for loading via the
 * {@link ConstantInstruction.LoadConstantInstruction ldc} instructions.
 * <p>
 * The use of a {@code LoadableConstantEntry} is modeled by a {@link ConstantDesc}.
 * Conversions are through {@link ConstantPoolBuilder#loadableConstantEntry(ConstantDesc)}
 * and {@link #constantValue()}.
 *
 * @see CodeBuilder#ldc(LoadableConstantEntry)
 * @jvms 4.4 The Constant Pool
 * @sealedGraph
 * @since 24
 */
public sealed interface LoadableConstantEntry extends PoolEntry
        permits ClassEntry, ConstantDynamicEntry, ConstantValueEntry, MethodHandleEntry, MethodTypeEntry {

    /**
     * {@return a symbolic descriptor of this constant}
     *
     * @see ConstantPoolBuilder#loadableConstantEntry(ConstantDesc)
     */
    ConstantDesc constantValue();

    /**
     * {@return the data type of this constant}
     * <p>
     * If the data type is of {@linkplain TypeKind#slotSize() category} 2, this
     * constant must be loaded with {@link Opcode#LDC2_W ldc2_w}; otherwise, the
     * data type is of category 1, and this constant must be loaded with {@link
     * Opcode#LDC ldc} or {@link Opcode#LDC_W ldc_w}.
     */
    default TypeKind typeKind() {
        return TypeKind.REFERENCE;
    }
}
