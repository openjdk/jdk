/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.calc;

//JaCoCo Exclude

/**
 * Utilities for unsigned comparisons. All methods have correct, but slow, standard Java
 * implementations so that they can be used with compilers not supporting the intrinsics.
 */
public class UnsignedMath {

    /**
     * Unsigned comparison aboveThan for two numbers.
     */
    public static boolean aboveThan(int a, int b) {
        return Integer.compareUnsigned(a, b) > 0;
    }

    /**
     * Unsigned comparison aboveOrEqual for two numbers.
     */
    public static boolean aboveOrEqual(int a, int b) {
        return Integer.compareUnsigned(a, b) >= 0;
    }

    /**
     * Unsigned comparison belowThan for two numbers.
     */
    public static boolean belowThan(int a, int b) {
        return Integer.compareUnsigned(a, b) < 0;
    }

    /**
     * Unsigned comparison belowOrEqual for two numbers.
     */
    public static boolean belowOrEqual(int a, int b) {
        return Integer.compareUnsigned(a, b) <= 0;
    }

    /**
     * Unsigned comparison aboveThan for two numbers.
     */
    public static boolean aboveThan(long a, long b) {
        return Long.compareUnsigned(a, b) > 0;
    }

    /**
     * Unsigned comparison aboveOrEqual for two numbers.
     */
    public static boolean aboveOrEqual(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0;
    }

    /**
     * Unsigned comparison belowThan for two numbers.
     */
    public static boolean belowThan(long a, long b) {
        return Long.compareUnsigned(a, b) < 0;
    }

    /**
     * Unsigned comparison belowOrEqual for two numbers.
     */
    public static boolean belowOrEqual(long a, long b) {
        return Long.compareUnsigned(a, b) <= 0;
    }
}
