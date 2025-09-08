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

import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.Util;

/**
 * Models a {@code CONSTANT_InvokeDynamic_info} structure, or the symbolic
 * reference to a <dfn>{@index "dynamically-computed call site"}</dfn>, in the
 * constant pool of a {@code class} file.
 * <p>
 * The use of a {@code InvokeDynamicEntry} is modeled by a {@link
 * DynamicCallSiteDesc} symbolic descriptor.  It can be obtained from {@link
 * #asSymbol() InvokeDynamicEntry::asSymbol} and converted back to a constant
 * pool entry through {@link ConstantPoolBuilder#invokeDynamicEntry(DynamicCallSiteDesc)
 * ConstantPoolBuilder::invokeDynamicEntry}.
 * <p>
 * An invoke dynamic entry is composite:
 * {@snippet lang=text :
 * // @link substring="InvokeDynamicEntry" target="ConstantPoolBuilder#invokeDynamicEntry(BootstrapMethodEntry, NameAndTypeEntry)" :
 * InvokeDynamicEntry(
 *     BootstrapMethodEntry bootstrap, // @link substring="bootstrap" target="#bootstrap()"
 *     NameAndTypeEntry nameAndType // @link substring="nameAndType" target="#nameAndType()"
 * )
 * }
 * where the {@link #type() type} in the {@code nameAndType} is a {@linkplain
 * #typeSymbol() method descriptor} string.
 *
 * @apiNote
 * A dynamically-computed call site is frequently called a <dfn>{@index "dynamic
 * call site"}</dfn>, or an <dfn>{@index "indy"}</dfn>, from the abbreviation of
 * "invoke dynamic".
 *
 * @see ConstantPoolBuilder#invokeDynamicEntry
 *      ConstantPoolBuilder::invokeDynamicEntry
 * @see DynamicCallSiteDesc
 * @see java.lang.invoke##indyinsn Dynamically-computed call sites
 * @jvms 4.4.10 The {@code CONSTANT_Dynamic_info} and {@code
 *              CONSTANT_InvokeDynamic_info} Structures
 * @since 24
 */
public sealed interface InvokeDynamicEntry
        extends DynamicConstantPoolEntry
        permits AbstractPoolEntry.InvokeDynamicEntryImpl {

    /**
     * {@return a symbolic descriptor for the {@linkplain #type() invocation
     * type} of this dynamic call site}
     */
    default MethodTypeDesc typeSymbol() {
        return Util.methodTypeSymbol(type());
    }

    /**
     * {@return a symbolic descriptor for this dynamic call site}
     *
     * @see ConstantPoolBuilder#invokeDynamicEntry(DynamicCallSiteDesc)
     *      ConstantPoolBuilder::invokeDynamicEntry(DynamicCallSiteDesc)
     */
    default DynamicCallSiteDesc asSymbol() {
        return DynamicCallSiteDesc.of(bootstrap().bootstrapMethod().asSymbol(),
                                      name().stringValue(),
                                      typeSymbol(),
                                      bootstrap().arguments().stream()
                                                 .map(LoadableConstantEntry::constantValue)
                                                 .toArray(ConstantDesc[]::new));
    }
}
