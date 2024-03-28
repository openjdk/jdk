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
package jdk.internal.vm;

import jdk.internal.vm.annotation.Hidden;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * This class defines methods to test if a value has been evaluated to a
 * compile-time constant by the HotSpot VM.
 */
public class ConstantSupport {
    /**
     * Determine if {@code val} can be proved to be a constant by the JIT
     * compiler. For example, {@code isCompileConstant(5)} will be evaluated
     * to {@code true}, while {@code isCompileConstant(random.nextLong())}
     * will likely be evaluated to {@code false}.
     *
     * <p>Note that the JIT compiler is responsible for changing the return
     * value of this method to {@code true}, the interpreter always returns
     * {@code false}.
     *
     * <p>Given the nondeterministic nature of this method, the result of the
     * program must not depend on the return value of this method. It must be
     * used as a pure optimization to take advantage of the constant nature of
     * {@code val}.
     *
     * <p>Since we will not have profile information in the branch that this
     * method returns {@code true}, the compiler may have a harder time
     * optimizing it. As a result, apart from simple cases, this technique
     * should be used with care.
     *
     * <p>Usage example:
     *
     * {@snippet lang=java:
     * void checkIndex(int index, int length) {
     *     // Normally, we check length >= 0 && index u< length because
     *     // length >= 0 can often be hoisted out of loops, however, if
     *     // we know that index >= 0, just signed compare index and length
     *     boolean indexPos = index >= 0;
     *     if (isCompileConstant(indexPos) && indexPos) {
     *         if (index >= length) {
     *             throw new IndexOutOfBoundsException();
     *         }
     *         return;
     *     }
     *
     *     if (length < 0 || Integer.compareUnsigned(index, length) >= 0) {
     *         throw new IndexOutOfBoundsException();
     *     }
     * }
     * }
     *
     * @param val the tested value
     * @return {@code true} if the JIT compiler determines that {@code val} is
     *         a constant, {@code false} otherwise
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(boolean val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(byte val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(short val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(char val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(int val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(long val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(float val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(double val) {
        return false;
    }

    /**
     * @see #isCompileConstant(boolean)
     */
    @Hidden
    @IntrinsicCandidate
    public static boolean isCompileConstant(Object val) {
        return false;
    }
}
