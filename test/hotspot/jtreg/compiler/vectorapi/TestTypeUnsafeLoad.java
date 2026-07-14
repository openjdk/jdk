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
package compiler.vectorapi;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShuffle;

/*
 * @test
 * @bug 8387012
 * @summary Expansion of a VectorUnboxNode should not create a type-unsafe load.
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-TieredCompilation
 *                   -XX:+StressGCM -XX:+StressIGVN -XX:+StressCCP ${test.main.class}
 */
public class TestTypeUnsafeLoad {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }

    public static ByteVector test() {
        var v0 = ByteVector.broadcast(ByteVector.SPECIES_128, (byte) 0);
        var v2 = v0.rearrange(VectorShuffle.makeUnzip(ByteVector.SPECIES_128, 1));
        var v3 = v0.lanewise(VectorOperators.MIN, v2);
        var v5 = v3.lanewise(VectorOperators.MAX, v0);
        return v5;
    }
}
