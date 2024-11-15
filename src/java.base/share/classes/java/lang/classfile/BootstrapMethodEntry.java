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

package java.lang.classfile;

import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.util.List;

import jdk.internal.classfile.impl.BootstrapMethodEntryImpl;

/**
 * Models an entry in the bootstrap method table.  The bootstrap method table
 * is stored in the {@link BootstrapMethodsAttribute BootstrapMethods}
 * attribute, but is modeled by the {@link ConstantPool}, since the bootstrap
 * method table is logically part of the constant pool.
 * <p>
 * Conceptually, a bootstrap method entry is a record:
 * {@snippet lang=text :
 * // @link region=1 substring="BootstrapMethodEntry" target="ConstantPoolBuilder#bsmEntry(DirectMethodHandleDesc, List)"
 * // @link substring="DirectMethodHandleDesc" target="#bootstrapMethod" :
 * BootstrapMethodEntry(DirectMethodHandleDesc, List<ConstantDesc>) // @link substring="List<ConstantDesc>" target="#arguments()"
 * // @end region=1
 * }
 * <p>
 * Physically, a bootstrap method entry is a record:
 * {@snippet lang=text :
 * // @link region=1 substring="BootstrapMethodEntry" target="ConstantPoolBuilder#bsmEntry(MethodHandleEntry, List)"
 * // @link substring="MethodHandleEntry" target="#bootstrapMethod" :
 * BootstrapMethodEntry(MethodHandleEntry, List<LoadableConstantEntry>) // @link substring="List<LoadableConstantEntry>" target="#arguments()"
 * // @end region=1
 * }
 *
 * @see ConstantPoolBuilder#bsmEntry ConstantPoolBuilder::bsmEntry
 * @since 24
 */
public sealed interface BootstrapMethodEntry
        permits BootstrapMethodEntryImpl {

    /**
     * {@return the constant pool associated with this entry}
     *
     * @apiNote
     * Given a {@link ConstantPoolBuilder} {@code builder} and a {@code
     * BootstrapMethodEntry} {@code entry}, use {@link
     * ConstantPoolBuilder#canWriteDirect
     * builder.canWriteDirect(entry.constantPool())} instead of object equality
     * of the constant pool to determine if an entry is compatible.
     */
    ConstantPool constantPool();

    /**
     * {@return the index into the bootstrap method table corresponding to this
     * entry}
     */
    int bsmIndex();

    /**
     * {@return the bootstrap method}
     *
     * @apiNote
     * A symbolic descriptor for the bootstrap method is available through
     * {@link MethodHandleEntry#asSymbol() bootstrapMethod().asSymbol()}.
     */
    MethodHandleEntry bootstrapMethod();

    /**
     * {@return the bootstrap arguments}
     *
     * @apiNote
     * A symbolic descriptor for each entry in the returned list is available
     * via {@link LoadableConstantEntry#constantValue
     * LoadableConstantEntry::constantValue}.
     */
    List<LoadableConstantEntry> arguments();
}
