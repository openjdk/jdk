/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Mixed results between two different generators with configurable weights.
 */
class MixedGenerator<T> extends BoundGenerator<T> {
    private final Generator<T> a;
    private final Generator<T> b;
    private final int weightA;
    private final int weightB;

    /**
     * Creates a new {@link MixedGenerator}, which samples from two generators A and B,
     * according to specified weights.
     *
     * @param weightA Weight for the distribution for a.
     * @param weightB Weight for the distribution for b.
     */
    MixedGenerator(Generators g, Generator<T> a, Generator<T> b, int weightA, int weightB) {
        super(g);
        this.a = a;
        this.b = b;
        this.weightA = weightA;
        this.weightB = weightB;
    }

    @Override
    public T next() {
        int r = g.random.nextInt(0, weightA + weightB);
        if (r < weightA) {
            return a.next();
        } else {
            return b.next();
        }
    }
}
