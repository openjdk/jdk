/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.types;

import jdk.internal.org.objectweb.asm.MethodVisitor;

/**
 * Array operations, not supported by all ops
 */
interface BytecodeArrayOps {

    /**
     * Load an array element given that the array and its index are already on
     * the stack
     *
     * @param method method visitor
     * @return the array element type
     *
     */
    Type aload(MethodVisitor method);

    /**
     * Store an array element given that the array and its index and the element
     * are on the stack
     *
     * @param method method visitor
     */
    void astore(MethodVisitor method);

    /**
     * Generate an array length operation
     *
     * @param method method method visitor
     * @return length of the array
     */
    Type arraylength(MethodVisitor method);

    /**
     * Create a new array of this array type and length on stack
     *
     * @param method method visitor
     * @return the type of the array
     */
    Type newarray(MethodVisitor method);

    /**
     * Create a new multi array of this array type and allocate the number of
     * dimensions given
     *
     * @param method method visitor
     * @param dims   number of dimensions
     * @return the type of the new array
     */
    Type newarray(MethodVisitor method, int dims);
}
