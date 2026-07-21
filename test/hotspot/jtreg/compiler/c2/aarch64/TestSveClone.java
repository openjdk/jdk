/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8386697
 * @summary Ensure that users of replicate nodes are clone compatible before folding
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @requires vm.cpu.features ~= ".*sve.*"
 * @run main/othervm --add-modules=jdk.incubator.vector -XX:-TieredCompilation
 *                   -XX:CompileThreshold=100 -XX:-BackgroundCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.c2.aarch64;

import jdk.incubator.vector.*;

public class TestSveClone {
    static final VectorSpecies<Byte> SB = ByteVector.SPECIES_64;

    static long test() {
        return ByteVector.broadcast(SB, (byte) -8)
            .lanewise(VectorOperators.XOR,
                ByteVector.broadcast(SB, (byte) 0),
                ByteVector.fromArray(SB, new byte[8], 0)
                    .compare(VectorOperators.EQ, (byte) 0))
            .reduceLanesToLong(VectorOperators.OR);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20000; i++) {
          test();
        }
        IO.println("done");
    }
}
