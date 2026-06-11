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
package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8337791
 * @summary Test byte vector predicated ABS with UseAVX=0 and MaxVectorSize=8
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestABSMaskedMaxByteVector
 */

public class TestABSMaskedMaxByteVector {
    public static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_MAX;
    public static int idx = 0;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-ea", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:UseAVX=0", "-XX:MaxVectorSize=8");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-ea");
    }

    @Test
    @IR(failOn = {IRNode.ABS_VB}, applyIfAnd = {"MaxVectorSize", " <= 8 ", "UseAVX", "0"}, applyIfPlatform = {"x64", "true"}, applyIfCPUFeature = {"sse4.1", "true"})
    @IR(counts = {IRNode.ABS_VB, "1"}, applyIf = {"MaxVectorSize", " > 8 "}, applyIfPlatform = {"x64", "true"}, applyIfCPUFeature = {"sse4.1", "true"})
    public void test() {
        assert ByteVector.broadcast(BSP, (byte)-4)
                         .lanewise(VectorOperators.ABS, VectorMask.fromLong(BSP, 0xF))
                         .lane(idx++ & 3) == 4;
    }
}
