/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.misc;

import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * Just-in-time-compiler-related queries
 */
public class JitCompiler {
    /**
     * Determine if {@code expr} can be evaluated to a constant value by the JIT
     * compiler. For example, {@code isCompileConstant(5)} will be evaluated
     * to {@code true} by the JIT compiler, while
     * {@code isCompileConstant(random.nextLong())} will likely be evaluated
     * to {@code false}.
     *
     * <p>Note that the JIT compiler is responsible to change the return value
     * of this method to {@code true}, the interpreter always returns {@code false}.
     *
     * <p>Given the nondeterministic nature of this method, the result of the
     * program must not depend on the return value of this method. It must be
     * used as a pure optimization to take advantage of the constant nature of
     * {@code expr}. E.g. for a runtime variable, looking up a hashmap may be
     * the most efficient look up method, however, if the look up table is
     * constant, it may be better to use a chain of if-else in cases where the
     * input is also a constant.
     *
     * @param expr the expression to be evaluated
     * @return {@code true} if the JIT compiler determines the {@code expr} is
     *         always evaluated to a constant value, {@code false} otherwise
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(boolean expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(byte expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(short expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(char expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(int expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(long expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(float expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(double expr) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @IntrinsicCandidate
    public static boolean isCompileConstant(Object expr) {
        return false;
    }
}
