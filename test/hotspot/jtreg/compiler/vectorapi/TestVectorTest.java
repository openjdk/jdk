/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;

/*
 * @test
 * @bug 8292289
 * @summary Test idealization of VectorTest intrinsics to eliminate
 *          the materialization of the result as an int
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*sse4.*" & (vm.opt.UseSSE == "null" | vm.opt.UseSSE > 3))
 *           | os.arch == "aarch64"
 *           | (os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*")
 * @run driver compiler.vectorapi.TestVectorTest
 */
public class TestVectorTest {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @DontInline
    public int call() { return 1; }

    @Test
    @IR(failOn = {IRNode.CMP_I, IRNode.CMOVE_I})
    @IR(counts = {IRNode.VECTOR_TEST, "1"})
    public int branch(long maskLong) {
        var mask = VectorMask.fromLong(ByteVector.SPECIES_PREFERRED, maskLong);
        return mask.allTrue() ? call() : 0;
    }

    @Test
    @IR(failOn = {IRNode.CMP_I})
    @IR(counts = {IRNode.VECTOR_TEST, "1", IRNode.CMOVE_I, "1"})
    public int cmove(long maskLong) {
        var mask = VectorMask.fromLong(ByteVector.SPECIES_PREFERRED, maskLong);
        return mask.allTrue() ? 1 : 0;
    }

    @Run(test = {"branch", "cmove"})
    public void run() {
        branch(-1);
        branch(100);
        cmove(-1);
        cmove(100);
    }
}
