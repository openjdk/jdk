/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.meta;

/**
 * Provides memory access operations for the target VM.
 */
public interface MemoryAccessProvider {

    /**
     * Reads a value of this kind using a base address and a displacement. No bounds checking or
     * type checking is performed. Returns {@code null} if the value is not available at this point.
     *
     * @param base the base address from which the value is read.
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link JavaConstant} object, or {@code null} if the
     *         value cannot be read.
     */
    JavaConstant readUnsafeConstant(JavaKind kind, JavaConstant base, long displacement);

    /**
     * Reads a primitive value using a base address and a displacement.
     *
     * @param kind the {@link JavaKind} of the returned {@link JavaConstant} object
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @param bits the number of bits to read from memory
     * @return the read value encapsulated in a {@link JavaConstant} object of {@link JavaKind} kind
     */
    JavaConstant readPrimitiveConstant(JavaKind kind, Constant base, long displacement, int bits);

    /**
     * Reads a Java {@link Object} value using a base address and a displacement.
     *
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link Constant} object
     */
    JavaConstant readObjectConstant(Constant base, long displacement);
}
