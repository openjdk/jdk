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
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_MethodType_info} structure, or a symbolic reference
 * to a method type, in the constant pool of a {@code class} file.
 * <p>
 * The use of a {@code MethodTypeEntry} is modeled by a {@link MethodTypeDesc}.
 * Conversions are through {@link ConstantPoolBuilder#methodTypeEntry(MethodTypeDesc)}
 * and {@link #asSymbol()}.
 * <p>
 * A method type entry is composite:
 * {@snippet lang=text :
 * // @link substring="MethodTypeEntry" target="ConstantPoolBuilder#methodTypeEntry(Utf8Entry)" :
 * MethodTypeEntry(Utf8Entry descriptor) // @link substring="descriptor" target="#descriptor()"
 * }
 * where {@code descriptor} is a {@linkplain #asSymbol() method descriptor}
 * string.
 *
 * @jvms 4.4.9 The {@code CONSTANT_MethodType_info} Structure
 * @since 24
 */
public sealed interface MethodTypeEntry
        extends LoadableConstantEntry
        permits AbstractPoolEntry.MethodTypeEntryImpl {

    /**
     * {@inheritDoc}
     * <p>
     * This is equivalent to {@link #asSymbol() asSymbol()}.
     */
    @Override
    default ConstantDesc constantValue() {
        return asSymbol();
    }

    /**
     * {@return the {@linkplain #asSymbol() method descriptor} string}
     */
    Utf8Entry descriptor();

    /**
     * {@return a symbolic descriptor for the {@linkplain #descriptor() method
     * type}}
     *
     * @apiNote
     * If only symbol equivalence is desired, {@link #matches(MethodTypeDesc)
     * matches} should be used.  It requires reduced parsing and can
     * improve {@code class} file reading performance.
     */
    MethodTypeDesc asSymbol();

    /**
     * {@return whether this entry describes the given method type}
     *
     * @param desc the method type descriptor
     * @since 25
     */
    boolean matches(MethodTypeDesc desc);
}
