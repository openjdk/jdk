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

/*
 * @test
 * @bug 8367483
 * @summary Missed worklist notification during IGVN for CastX2P(SubX(x, y)) pattern,
 *          leads to missed optimization.
 * @run main/othervm
 *           -XX:CompileCommand=compileonly,compiler.igvn.MissedOptimizationWithCastX2PSubX::test
 *           -XX:-TieredCompilation
 *           -XX:+IgnoreUnrecognizedVMOptions
 *           -XX:VerifyIterativeGVN=1110
 *           compiler.igvn.MissedOptimizationWithCastX2PSubX
 * @run main compiler.igvn.MissedOptimizationWithCastX2PSubX
 */

package compiler.igvn;

import java.lang.foreign.*;

public class MissedOptimizationWithCastX2PSubX {
    public static void main(String[] args) {
        MemorySegment a  = Arena.ofAuto().allocate(74660);
        MemorySegment b  = Arena.ofAuto().allocate(74660);

        for (int i = 0; i < 1_000; i++) {
            test(a, 69830/2 - 100, b, -10_000, 0, 1_000);
        }
    }

    public static void test(MemorySegment a, long invarA, MemorySegment b, long invarB, long ivLo, long ivHi) {
        for (long i = ivHi-1; i >= ivLo; i-=1) {
            a.set(ValueLayout.JAVA_CHAR_UNALIGNED, 69830L + 2L * i + -2L * invarA, (char)42);
            b.set(ValueLayout.JAVA_FLOAT_UNALIGNED, 9071L + -4L * i + -4L * invarB, 42);
        }
    }
}
