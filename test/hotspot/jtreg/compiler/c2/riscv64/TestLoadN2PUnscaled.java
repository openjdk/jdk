/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8389895
 * @summary LoadN followed by DecodeN is matched into a single lwu when
 *          compressed oops are unscaled.
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @run driver ${test.main.class}
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;

public class TestLoadN2PUnscaled {

    static class Link {
        Link next;
    }

    Link link = new Link();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
            // A small heap is placed below 4GB, so compressed oops are
            // unscaled and decoding is a no-op zero-extension.
            new Scenario(0, "-XX:+UseCompressedOops", "-Xmx128m"),
            // A heap that ends above 4GB needs a shift to decode, so the
            // load and the decode must not be fused.
            new Scenario(1, "-XX:+UseCompressedOops", "-Xmx5g"));
        framework.start();
    }

    @Test
    @IR(counts = {IRNode.RISCV_LOAD_N2P_UNSCALED, "1"},
        applyIf = {"MaxHeapSize", "< 1073741824"})
    @IR(failOn = IRNode.RISCV_LOAD_N2P_UNSCALED,
        applyIf = {"MaxHeapSize", "> 4294967296"})
    public Link test() {
        return link;
    }
}
