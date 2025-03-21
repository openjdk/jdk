/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;

/**
 * Provides a uniform float16 distribution random generator, in the provided range [lo, hi).
 */
final class UniformFloat16Generator extends UniformIntersectionRestrictableGenerator<Float16> {
    /**
     * Creates a new {@link UniformFloat16Generator}.
     *
     * @param lo Lower bound of the range (inclusive).
     * @param hi Higher bound of the range (exclusive).
     */
    public UniformFloat16Generator(Generators g, Float16 lo, Float16 hi) {
        super(g, lo, hi);
    }

    @Override
    public Float16 next() {
        return valueOf(g.random.nextFloat(lo().floatValue(), hi().floatValue()));
    }

    @Override
    protected RestrictableGenerator<Float16> doRestrictionFromIntersection(Float16 lo, Float16 hi) {
        return new UniformFloat16Generator(g, lo, hi);
    }
}
