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

import jdk.incubator.vector.*;

/*
 * @test id=vanilla
 * @bug 8387411
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */

/*
 * @test id=KNL
 * @bug 8387411
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UseKNLSetting
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

public class TestMaskUnboxingTypeMismatch {

    public static Object pollute() {
        VectorMask<Integer> intMask = VectorMask.fromLong(IntVector.SPECIES_512, 1L);
        // Profile "andNot" with I512.
        return intMask.andNot(intMask);
    }

    public static Object test() {
        var v0 = ByteVector.broadcast(ByteVector.SPECIES_128, (byte)7);
        var v1 = VectorMask.fromLong(ByteVector.SPECIES_128, 1L);
        var v2 = VectorMask.fromLong(ByteVector.SPECIES_128, 2L);
        // Use "andNot" with B128.
        // We can get some boxing of B128 mask, which is later unboxed
        // as profiled I512, which is impossible. When trying to insert
        // an VectorMaskCast in VectorUnboxNode::Ideal, we hit an assert,
        // because with UseKNLSetting, B128 mask is a NVectMask, and I512
        // a PVectMask.
        var v3 = v1.andNot(v2);
        var v4 = v0.lanewise(VectorOperators.UMAX, (byte)42, v3);
        return v4;
    }

    public static void main(String[] args) {
        // Sufficient repetitions to get some profiling.
        for (int i = 0; i < 10_000; i++) {
            pollute();
        }
        // Sufficient repetitions to get compilation.
        for (int i = 0; i < 50_000; i++) {
            test();
        }
    }
}
