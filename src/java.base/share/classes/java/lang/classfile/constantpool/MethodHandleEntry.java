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
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_MethodHandle_info} structure, or a symbolic
 * reference to a {@linkplain MethodHandle method handle}, in the constant pool
 * of a {@code class} file.  The method handle directly accesses an accessible
 * method, field, or constructor.
 * <p>
 * The use of a {@code MethodHandleEntry} is modeled by a {@link
 * DirectMethodHandleDesc}.  Conversions are through {@link
 * ConstantPoolBuilder#methodHandleEntry(DirectMethodHandleDesc)} and {@link
 * #asSymbol()}.
 * <p>
 * A method handle entry is composite:
 * {@snippet lang=text :
 * // @link substring="MethodHandleEntry" target="ConstantPoolBuilder#methodHandleEntry(int, MemberRefEntry)" :
 * MethodHandleEntry(
 *     int refKind, // @link substring="refKind" target="#kind()"
 *     MemberRefEntry reference // @link substring="reference" target="#reference()"
 * )
 * }
 * where {@code refKind} is in the range {@code [1, 9]}.
 *
 * @see ConstantPoolBuilder#methodHandleEntry
 *      ConstantPoolBuilder::methodHandleEntry
 * @jvms 4.4.8 The {@code CONSTANT_MethodHandle_info} Structure
 *
 * @since 24
 */
public sealed interface MethodHandleEntry
        extends LoadableConstantEntry
        permits AbstractPoolEntry.MethodHandleEntryImpl {

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
     * {@return the reference kind of this method handle (JVMS {@jvms 4.4.8})}
     *
     * @see MethodHandleInfo##refkinds Reference kinds
     */
    int kind();

    /**
     * {@return the constant pool entry describing the field or method,
     * according to the {@linkplain #kind() reference kind}}
     */
    MemberRefEntry reference();

    /**
     * {@return a symbolic descriptor for this method handle}
     *
     * @see ConstantPoolBuilder#methodHandleEntry(DirectMethodHandleDesc)
     *      ConstantPoolBuilder::methodHandleEntry(DirectMethodHandleDesc)
     */
    DirectMethodHandleDesc asSymbol();
}
