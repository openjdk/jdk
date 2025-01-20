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
 * Provides a uniform float distribution random generator, in the provided range [lo, hi).
 */
final class UniformFloatGenerator extends UniformIntersectionRestrictableGenerator<Float> {
    /**
     * Creates a new {@link UniformFloatGenerator}.
     *
     * @param lo Lower bound of the range (inclusive).
     * @param hi Higher bound of the range (exclusive).
     */
    public UniformFloatGenerator(Generators g, float lo, float hi) {
        super(g, lo, hi);
    }

    @Override
    public Float next() {
        return g.random.nextFloat(lo(), hi());
    }

    @Override
    protected RestrictableGenerator<Float> doRestrictionFromIntersection(Float lo, Float hi) {
        return new UniformFloatGenerator(g, lo, hi);
    }
}
