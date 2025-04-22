/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.generators;

/**
 * Defines the underlying randomness source used by the generators. This is essentially a subset of
 * {@link java.util.random.RandomGenerator} and the present methods have the same contract.
 * This interface greatly benefits testing, as it is much easier to implement than
 * {@link java.util.random.RandomGenerator}  and thus makes creating test doubles more convenient.
 */
public interface RandomnessSource {
    /** Samples the next long value uniformly at random. */
    long nextLong();
    /** Samples the next long value in the half-open interval [lo, hi) uniformly at random. */
    long nextLong(long lo, long hi);
    /** Samples the next int value uniformly at random. */
    int nextInt();
    /** Samples the next int value in the half-open interval [lo, hi) uniformly at random. */
    int nextInt(int lo, int hi);
    /** Samples the next double value in the half-open interval [lo, hi) uniformly at random. */
    double nextDouble(double lo, double hi);
    /** Samples the next float value in the half-open interval [lo, hi) uniformly at random. */
    float nextFloat(float lo, float hi);
    /** Samples the next float16 value in the half-open interval [lo, hi) uniformly at random. */
    short nextFloat16(short lo, short hi);
}
