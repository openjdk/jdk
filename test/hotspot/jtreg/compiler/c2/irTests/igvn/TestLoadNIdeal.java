/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests.igvn;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8310524
 * @requires vm.bits == 64
 * @summary Test that IGVN optimizes away one of two identical LoadNs.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.igvn.TestLoadNIdeal
 */

public class TestLoadNIdeal {

    public static void main(String[] args) {
        // Ensure that we run with compressed oops
        TestFramework.runWithFlags("-XX:+UseCompressedOops");
    }

    static class A { int x; }

    @DontInline
    void dummy(A p[]) { }

    @Test
    @IR(applyIf = { "UseCompressedOops", "true" }, counts = { IRNode.LOAD_N, "1" })
    int test() {
        A p[] = new A[1];
        p[0] = new A();

        // The dummy method is not inlined => Escape analysis
        // cannot ensure that p[0] is unmodified after the call.
        dummy(p);

        // We should only need to load p[0] once here. Storing A within an
        // array adds range checks for the first load and ensures the second
        // load is not optimized already at bytecode parsing.
        return p[0].x + p[0].x;
    }
}
