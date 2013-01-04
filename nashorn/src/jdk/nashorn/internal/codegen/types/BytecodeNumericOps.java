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
 * Numeric operations, not supported by all types
 */
interface BytecodeNumericOps {

    /**
     * Pop and negate the value on top of the stack and push the result
     *
     * @param method method visitor
     *
     * @return result type
     */
    Type neg(MethodVisitor method);

    /**
     * Pop two values on top of the stack and subtract the first from the
     * second, pushing the result on the stack
     *
     * @param method method visitor
     *
     * @return result type
     */
    Type sub(MethodVisitor method);

    /**
     * Pop and multiply the two values on top of the stack and push the result
     * on the stack
     *
     * @param method method visitor
     *
     * @return result type
     */
    Type mul(MethodVisitor method);

    /**
     * Pop two values on top of the stack and divide the first with the second,
     * pushing the result on the stack
     *
     * @param method method visitor
     *
     * @return result type
     */
    Type div(MethodVisitor method);

    /**
     * Pop two values on top of the stack and compute the modulo of the first
     * with the second, pushing the result on the stack
     *
     * @param method method visitor
     *
     * @return result type
     */
    Type rem(MethodVisitor method);

    /**
     * Comparison with int return value, e.g. LCMP, DCMP.
     *
     * @param method the method visitor
     * @param isCmpG is this a double style cmpg
     *
     * @return int return value
     */
    Type cmp(MethodVisitor method, boolean isCmpG);
}
