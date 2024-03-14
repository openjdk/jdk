/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.gcbarriers;

import compiler.lib.ir_framework.*;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;

/**
 * @test
 * @summary Test that the expanded size of ZGC barriers is taken into account in
 *          C2's loop unrolling heuristics so that over-unrolling is avoided.
 *          The tests use volatile memory accesses to prevent C2 from simply
 *          optimizing them away.
 * @library /test/lib /
 * @requires vm.gc.ZGenerational
 * @run driver compiler.gcbarriers.TestZGCUnrolling
 */

public class TestZGCUnrolling {

    static class Outer {
        Object f;
    }

    static final VarHandle fVarHandle;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            fVarHandle = l.findVarHandle(Outer.class, "f", Object.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseZGC", "-XX:+ZGenerational",
                                   "-XX:LoopUnrollLimit=24");
    }

    @Test
    @IR(counts = {IRNode.STORE_P, "1"})
    public static void testNoUnrolling(Outer o, Object o1) {
        for (int i = 0; i < 64; i++) {
            fVarHandle.setVolatile(o, o1);
        }
    }

    @Run(test = {"testNoUnrolling"})
    void run() {
        testNoUnrolling(new Outer(), new Object());
    }
}
