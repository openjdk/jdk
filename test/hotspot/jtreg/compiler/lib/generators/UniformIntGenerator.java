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
 * Provides a uniform int distribution random generator.
 */
final class UniformIntGenerator extends UniformIntersectionRestrictableGenerator<Integer> {
    public UniformIntGenerator(Generators g, int lo, int hi) {
        super(g, lo, hi);
    }

    @Override
    public Integer next() {
        if (hi() == Integer.MAX_VALUE) {
            if (lo() == Integer.MIN_VALUE) {
                return g.random.nextInt();
            }
            return g.random.nextInt(lo() - 1, hi()) + 1;
        }
        return g.random.nextInt(lo(), hi() + 1);
    }

    @Override
    protected RestrictableGenerator<Integer> doRestrictionFromIntersection(Integer lo, Integer hi) {
        return new UniformIntGenerator(g, lo, hi);
    }
}
