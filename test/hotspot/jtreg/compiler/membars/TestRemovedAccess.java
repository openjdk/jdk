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
package compiler.membars;

import compiler.lib.ir_framework.*;
import jdk.internal.misc.Unsafe;

/*
 * @test
 * @bug 8394913
 * @summary When an acquire/volatile load is removed because it is unused, or because the load can
 *          be folded, the compiler must ensure that the memory effects are properly maintained.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @run driver ${test.main.class}
 */
public class TestRemovedAccess {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static class V {
        private static final long V_OFFSET = UNSAFE.objectFieldOffset(V.class, "v");

        volatile int v;
    }

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        framework.start();
    }

    @Run(test = {"testUnusedLoadAcquireUnsafe", "testUnusedLoadVolatileUnsafe", "testUnusedLoadVolatile",
                 "testFoldedLoadVolatile", "testFoldedGVNedLoadVolatile"})
    public void run() {
        V v = new V();
        testUnusedLoadAcquireUnsafe(v);
        testUnusedLoadVolatileUnsafe(v);
        testUnusedLoadVolatile(v);
        testFoldedLoadVolatile(v);
        testFoldedGVNedLoadVolatile(v, false);
        testFoldedGVNedLoadVolatile(v, true);
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    @IR(failOn = IRNode.AARCH64_UNNECESSARY_MEMBAR_ACQUIRE, counts = {IRNode.AARCH64_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"aarch64", "true"})
    @IR(counts = {IRNode.X86_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"x64", "true"})
    private static void testUnusedLoadAcquireUnsafe(V v) {
        UNSAFE.getIntAcquire(v, V.V_OFFSET);
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    @IR(failOn = IRNode.AARCH64_UNNECESSARY_MEMBAR_ACQUIRE, counts = {IRNode.AARCH64_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"aarch64", "true"})
    @IR(counts = {IRNode.X86_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"x64", "true"})
    private static void testUnusedLoadVolatileUnsafe(V v) {
        UNSAFE.getIntVolatile(v, V.V_OFFSET);
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    @IR(failOn = IRNode.AARCH64_UNNECESSARY_MEMBAR_ACQUIRE, counts = {IRNode.AARCH64_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"aarch64", "true"})
    @IR(counts = {IRNode.X86_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"x64", "true"})
    private static void testUnusedLoadVolatile(V v) {
        int _ = v.v;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    @IR(failOn = IRNode.AARCH64_UNNECESSARY_MEMBAR_ACQUIRE, counts = {IRNode.AARCH64_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"aarch64", "true"})
    @IR(counts = {IRNode.X86_MEMBAR_ACQUIRE, "1"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"x64", "true"})
    private static int testFoldedLoadVolatile(V v) {
        UNSAFE.putInt(v, V.V_OFFSET, 0);
        return v.v;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = IRNode.LOAD_I, counts = {IRNode.MEMBAR_ACQUIRE, "2"})
    @IR(failOn = IRNode.AARCH64_UNNECESSARY_MEMBAR_ACQUIRE, counts = {IRNode.AARCH64_MEMBAR_ACQUIRE, "2"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"aarch64", "true"})
    @IR(counts = {IRNode.X86_MEMBAR_ACQUIRE, "2"},
        phase = CompilePhase.FINAL_CODE, applyIfPlatform = {"x64", "true"})
    private static int testFoldedGVNedLoadVolatile(V v, boolean b) {
        UNSAFE.putInt(v, V.V_OFFSET, 0);
        if (b) {
            return v.v + 1;
        } else {
            return v.v;
        }
    }
}
