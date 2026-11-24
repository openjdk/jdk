/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8398034
 * @summary Assertion failure due to wrong type in check_vbox when the mask class is not initialized
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation ${test.main.class}
 */

package compiler.vectorapi;

import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

public class TestRearrangeUninitializedMaskClass {
    static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;
    static final VectorSpecies<Short> S256 = ShortVector.SPECIES_256;
    static final short[] a = new short[S256.length()];
    static final int[] idx = new int[S256.length()];
    static final boolean[] m = new boolean[S256.length()];

    static void unmasked256() {
        ShortVector.fromArray(S256, a, 0).rearrange(VectorShuffle.fromArray(S256, idx, 0)).intoArray(a, 0);
    }

    static void masked128() {
        ShortVector.fromArray(S128, a, 0).rearrange(VectorShuffle.fromArray(S128, idx, 0),
                                                     VectorMask.fromArray(S128, m, 0)).intoArray(a, 0);
    }

    static void masked256() {
        ShortVector.fromArray(S256, a, 0).rearrange(VectorShuffle.fromArray(S256, idx, 0),
                                                     VectorMask.fromArray(S256, m, 0)).intoArray(a, 0);
    }

    public static void main(String[] args) {
        unmasked256();
        masked128();
        masked256();
    }
}
