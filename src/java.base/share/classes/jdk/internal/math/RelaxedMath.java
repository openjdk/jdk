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

package jdk.internal.math;

import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * This class exposes float and doulbe operations with relaxed semantics that
 * allow more optimizations than their regular (strict) counterparts. For example
 * float additions need to be executed sequentially, to preserve deterministic
 * rounding errors. Such restrictions disallow some optimizations, especially
 * optimizations that reorder instructions. Relaxing these restrictions allows
 * optimizations like vectorizing float add/mul reductions.
 *
 * TODO description?
 * @author Emanuel V. Peter
 */
public class RelaxedMath {
    /**
     * Don't let anyone instantiate this class.
     */
    private RelaxedMath() {}

    /**
     * Modes, can be composed with logical OR "|" to combine bit pattern.
     */
    public static int Default = 0;
    public static int AllowReductionReordering = 1;
    public static int AllowFMA = 2;

    // TODO description?
    @IntrinsicCandidate
    public static float add(float a, float b, int optimizationMode) { return a + b; }

    @IntrinsicCandidate
    public static float mul(float a, float b, int optimizationMode) { return a * b; }

    @IntrinsicCandidate
    public static double add(double a, double b, int optimizationMode) { return a + b; }

    @IntrinsicCandidate
    public static double mul(double a, double b, int optimizationMode) { return a * b; }
}
