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
 * A generators whose outputs are restricted by taking the intersection of the previous interval and the new interval.
 */
abstract class UniformIntersectionRestrictableGenerator<T extends Comparable<T>> extends BoundGenerator<T> implements RestrictableGenerator<T> {
    private final T lo;
    private final T hi;

    public UniformIntersectionRestrictableGenerator(Generators g, T lo, T hi) {
        super(g);
        if (lo.compareTo(hi) > 0) throw new EmptyGeneratorException();
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Creates a new generator by further restricting the range of values. The range of values will be the
     * intersection of the previous values and the values in the provided range.
     * The probability of each element occurring in the new generator stay the same relative to each other.
     */
    @Override
    public RestrictableGenerator<T> restricted(T newLo /*as*/, T newHi /*ae*/) {
        if (lo().compareTo(newHi) > 0 || newLo.compareTo(hi()) > 0) {
            throw new EmptyGeneratorException();
        }
        return doRestrictionFromIntersection(max(newLo, lo()), min(newHi, hi()));
    }

    private T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private T min(T a, T b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Your subclass can just override this method which will receive the computed intersection between the old and
     * new interval. It is guaranteed that the interval is non-empty.
     */
    protected abstract RestrictableGenerator<T> doRestrictionFromIntersection(T lo, T hi);

    T hi() { return hi; }
    T lo() { return lo; }
}
