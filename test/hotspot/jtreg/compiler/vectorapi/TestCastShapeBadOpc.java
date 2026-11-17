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

/*
 * @test id=all-flags
 * @bug 8374889
 * @summary Test case that can compile unexpected code paths in VectorAPI cast intrinsification.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:-TieredCompilation -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:StressSeed=1462975402
 *      -XX:+StressIncrementalInlining
 *      -XX:CompileCommand=compileonly,${test.main.class}::test2
 *      ${test.main.class}
 */

/*
 * @test id=no-stress-seed
 * @bug 8374889
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:-TieredCompilation -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+StressIncrementalInlining
 *      -XX:CompileCommand=compileonly,${test.main.class}::test2
 *      ${test.main.class}
 */

/*
 * @test id=vanilla
 * @bug 8374889
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import jdk.incubator.vector.*;

public class TestCastShapeBadOpc {
    public static void main(String[] args) {
        for (int i = 0; i < 100_000; ++i) {
            test1();
            test2();
        }
    }

    // This code does not trigger the bug itself, but seems to be important for profiling,
    // so that test2 fails.
    public static Object test1() {
        LongVector v0 = LongVector.broadcast(LongVector.SPECIES_512, -15L);
        var v1 = (ByteVector)v0.convertShape(VectorOperators.Conversion.ofReinterpret(long.class, byte.class), ByteVector.SPECIES_128, 0);
        var v2 = (ByteVector)v1.castShape(ByteVector.SPECIES_256, 0);
        return v2;
    }

    public static Object test2() {
        var v0 = ShortVector.broadcast(ShortVector.SPECIES_64, (short)7729);
        var v1 = (FloatVector)v0.reinterpretShape(FloatVector.SPECIES_64, 0);
        // The castShape below should take the "C" path in AbstractVector::convert0, but sometimes
        // we also compile the "Z" case because of profiling. This means we attempt to create
        // a vector cast from float -> long, but unfortunately with a UCAST (float -> long is signed).
        // This triggered an assert in VectorCastNode::opcode.
        var v2 = (LongVector)v1.castShape(LongVector.SPECIES_256, 0);
        return v2;
    }
}
