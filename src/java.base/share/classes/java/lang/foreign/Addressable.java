/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.javac.PreviewFeature;

/**
 * An object that may be projected down to a {@linkplain #address() memory address}.
 * Examples of addressable types are {@link MemorySegment}, {@link MemoryAddress} and {@link VaList}.
 * <p>
 * The {@link Addressable} type is used by a {@linkplain Linker linker} to model the types of
 * {@linkplain Linker#downcallHandle(FunctionDescriptor) downcall handle} parameters that must be passed <em>by reference</em>
 * (e.g. memory addresses, variable argument lists and upcall stubs).
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface Addressable permits MemorySegment, MemoryAddress, VaList {

    /**
     * {@return the {@linkplain MemoryAddress memory address} associated with this addressable}
     */
    MemoryAddress address();
}
