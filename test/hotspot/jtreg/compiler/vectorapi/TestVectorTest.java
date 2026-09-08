/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

    // The @IR rules below verify the VectorTest idealization that eliminates the
    // scalar materialization (CmpI/CMoveI) of the mask query result. They are only
    // valid when VectorMask.fromLong and the mask query are fully intrinsified.
    // IncrementalInlineVector is enabled by default: when the intrinsic fails to
    // apply on a given configuration (e.g. VectorMask.fromLong under -XX:UseAVX=0
    // or -XX:UseSVE=0/1 on AArch64), the fallback is inlined and materializes the
    // result with the very scalar nodes these rules forbid. We therefore guard the
    // rules to the configurations where full intrinsification is guaranteed: on x64
    // that requires UseAVX > 0 (and bmi2), on AArch64 that requires UseSVE > 1
    // (SVE2 mode, the default on SVE2-capable hardware), while on the remaining
    // supported platforms the @requires clause above already guarantees it.
    @Test
    @IR(failOn = {IRNode.CMP_I, IRNode.CMOVE_I}, counts = {IRNode.VECTOR_TEST, "1"},
        applyIfPlatform = {"x64", "true"}, applyIf = {"UseAVX", "> 0"},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.CMP_I, IRNode.CMOVE_I}, counts = {IRNode.VECTOR_TEST, "1"},
        applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "> 1"})
    @IR(failOn = {IRNode.CMP_I, IRNode.CMOVE_I}, counts = {IRNode.VECTOR_TEST, "1"},
        applyIfPlatformAnd = {"x64", "false", "aarch64", "false"})
    public int branch(long maskLong) {
        var mask = VectorMask.fromLong(ByteVector.SPECIES_PREFERRED, maskLong);
        return mask.allTrue() ? call() : 0;
    }

    @Test
    @IR(failOn = {IRNode.CMP_I}, counts = {IRNode.VECTOR_TEST, "1", IRNode.CMOVE_I, "1"},
        applyIfPlatform = {"x64", "true"}, applyIf = {"UseAVX", "> 0"},
        applyIfCPUFeature = {"bmi2", "true"})
    @IR(failOn = {IRNode.CMP_I}, counts = {IRNode.VECTOR_TEST, "1", IRNode.CMOVE_I, "1"},
        applyIfPlatform = {"aarch64", "true"}, applyIf = {"UseSVE", "> 1"})
    @IR(failOn = {IRNode.CMP_I}, counts = {IRNode.VECTOR_TEST, "1", IRNode.CMOVE_I, "1"},
        applyIfPlatformAnd = {"x64", "false", "aarch64", "false"})
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
