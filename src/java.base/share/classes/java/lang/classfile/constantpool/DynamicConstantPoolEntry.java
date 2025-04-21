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

import java.lang.classfile.BootstrapMethodEntry;

/**
 * Superinterface modeling dynamically-computed constant pool entries, which
 * include {@link ConstantDynamicEntry} and {@link InvokeDynamicEntry}, in the
 * constant pool of a {@code class} file.
 * <p>
 * Different types of dynamically-computed constant pool entries bear structural
 * similarities, but they appear in distinct locations.  As a result, their uses
 * are represented by different symbolic descriptors, specific to each subtype.
 * <p>
 * A dynamic constant entry is composite:
 * {@snippet lang=text :
 * DynamicConstantPoolEntry(
 *     BootstrapMethodEntry bootstrap, // @link substring="bootstrap" target="#bootstrap()"
 *     NameAndTypeEntry nameAndType // @link substring="nameAndType" target="#nameAndType()"
 * )
 * }
 *
 * @see java.lang.invoke##jvm_mods Dynamic resolution of call sites and
 *      constants
 * @jvms 4.4.10 The {@code CONSTANT_Dynamic_info} and {@code
 *              CONSTANT_InvokeDynamic_info} Structures
 * @jvms 5.4.3.6 Dynamically-Computed Constant and Call Site Resolution
 * @sealedGraph
 * @since 24
 */
public sealed interface DynamicConstantPoolEntry extends PoolEntry
        permits ConstantDynamicEntry, InvokeDynamicEntry {

    /**
     * {@return the entry in the bootstrap method table for this constant}
     *
     * @see java.lang.invoke##bsm Execution of bootstrap methods
     * @see ConstantPoolBuilder#constantDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     *      ConstantPoolBuilder::constantDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     * @see ConstantPoolBuilder#invokeDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     *      ConstantPoolBuilder::invokeDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     */
    BootstrapMethodEntry bootstrap();

    /**
     * {@return index of the entry in the bootstrap method table for this
     * constant}  The return value is equivalent to {@code
     * bootstrap().bsmIndex()}.
     */
    int bootstrapMethodIndex();

    /**
     * {@return the name and the descriptor string indicated by this symbolic
     * reference}
     *
     * @see java.lang.invoke##bsm Execution of bootstrap methods
     * @see ConstantPoolBuilder#constantDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     *      ConstantPoolBuilder::constantDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     * @see ConstantPoolBuilder#invokeDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     *      ConstantPoolBuilder::invokeDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)
     */
    NameAndTypeEntry nameAndType();

    /**
     * {@return the name indicated by this symbolic reference}
     */
    default Utf8Entry name() {
        return nameAndType().name();
    }

    /**
     * {@return the descriptor string indicated by this symbolic reference}
     * This is a field descriptor string if this entry is a {@link
     * ConstantDynamicEntry}, or a method descriptor string if this entry is a
     * {@link InvokeDynamicEntry}.
     *
     * @apiNote
     * Each subinterface has its specific accessor named {@code typeSymbol} for
     * the symbolic descriptor derived from this descriptor string.
     */
    default Utf8Entry type() {
        return nameAndType().type();
    }
}
