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
package compiler.c2.gvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8360192
 * @summary Tests that count bits nodes are handled correctly.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestFindStore {
    static class P {
        int v;
    }

    static class C1 extends P {}
    static class C2 extends P {}

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"testLoad", "testStore"})
    public void run() {
        C1 c1 = new C1();
        C2 c2 = new C2();

        Asserts.assertEQ(1, testLoad(c1, c2, 1, 0));
        Asserts.assertEQ(0, testStore(c2, 0).v);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    static int testLoad(C1 c1, C2 c2, int v1, int v2) {
        c1.v = v1;
        c2.v = v2;
        return c1.v;
    }

    @Test
    @IR(counts = {IRNode.STORE, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    static C1 testStore(C2 c2, int v2) {
        C1 c1 = new C1();
        c2.v = v2;
        c1.v = 0;
        return c1;
    }
}
