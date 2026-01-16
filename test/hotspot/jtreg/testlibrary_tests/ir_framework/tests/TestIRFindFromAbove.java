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

package ir_framework.tests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8373355
 * @summary Test that IR matching happens on the whole graph, not just nodes
 *          that can be found by traversing up from the Root.
 * @library /test/lib /
 * @run main ${test.main.class}
 */

public class TestIRFindFromAbove {
    public static boolean flag = false;
    public static int fld = 0;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Warmup(0)
    // Simulate Xcomp with no warmup: ensure the flag branch is not an unstable if
    // but that we compile the infinite loop.
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.STORE_I, "1", ".*NeverBranch.*", "0"},
        phase = CompilePhase.ITER_GVN1)
    @IR(counts = {IRNode.LOAD_I, "1", IRNode.STORE_I, "1", ".*NeverBranch.*", "1"},
        phase = CompilePhase.PHASEIDEALLOOP1)
    public static void test() {
        if (flag) {
            // This loop has no exit. So it is at first not connected down to Root.
            while (true) {
                // During PHASEIDEALLOOP1, we insert a NeverBranch here, with a fake
                // exit, that connects the loop down to Root.
                fld++; // LoadI and StoreI
            }
        }
    }
}
