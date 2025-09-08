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

import java.lang.constant.ModuleDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_Module_info} structure, denoting a module, in the
 * constant pool of a {@code class} file.
 * <p>
 * The use of a {@code ModuleEntry} is modeled by a {@link ModuleDesc}.
 * Conversions are through {@link ConstantPoolBuilder#moduleEntry(ModuleDesc)}
 * and {@link #asSymbol()}.
 * <p>
 * A module entry is composite:
 * {@snippet lang=text :
 * // @link substring="ModuleEntry" target="ConstantPoolBuilder#moduleEntry(Utf8Entry)" :
 * ModuleEntry(Utf8Entry name) // @link substring="name" target="#name()"
 * }
 * where {@code name} is a {@linkplain #asSymbol() module name}.
 *
 * @jvms 4.4.11 The {@code CONSTANT_Module_info} Structure
 * @since 24
 */
public sealed interface ModuleEntry extends PoolEntry
        permits AbstractPoolEntry.ModuleEntryImpl {
    /**
     * {@return the name of the {@linkplain #asSymbol() module}}
     */
    Utf8Entry name();

    /**
     * {@return a symbolic descriptor for the {@linkplain #name() module name}}
     *
     * @apiNote
     * If only symbol equivalence is desired, {@link #matches(ModuleDesc)
     * matches} should be used.  It requires reduced parsing and can
     * improve {@code class} file reading performance.
     */
    ModuleDesc asSymbol();

    /**
     * {@return whether this entry describes the given module}
     *
     * @param desc the module descriptor
     * @since 25
     */
    boolean matches(ModuleDesc desc);
}
