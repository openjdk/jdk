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
 * @bug 8337821
 * @summary Ensure reasonable inlining in Float16Vector rearrange.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -Xbatch
 *                   -esa
 *                   -XX:CompileCommand=CompileOnly,jdk.incubator.vector.Float16Vector::lambda$rearrangeTemplate$0
 *                   compiler.vectorapi.TestFloat16RearrangeInlining
 * @run main compiler.vectorapi.TestFloat16RearrangeInlining
 */

package compiler.vectorapi;

import jdk.incubator.vector.*;

public class TestFloat16RearrangeInlining {

    public static void main(String[] args) {
        Float16Vector vector512 = Float16Vector.zero(Float16Vector.SPECIES_512);
        VectorShuffle<Float16> shuffle512 =
            VectorShuffle.iota(Float16Vector.SPECIES_512, 0, 1, false);
        for (int i = 0; i < 1_500; i++) {
            vector512.rearrange(shuffle512);
        }
        Float16Vector vector256 = Float16Vector.zero(Float16Vector.SPECIES_256);
        VectorShuffle<Float16> shuffle256 =
            VectorShuffle.iota(Float16Vector.SPECIES_256, 0, 1, false);
        for (int i = 0; i < 20_000; i++) {
            vector256.rearrange(shuffle256);
        }
    }
}
