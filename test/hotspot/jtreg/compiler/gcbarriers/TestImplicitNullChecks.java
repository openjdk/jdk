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
import jdk.test.lib.Asserts;


/**
 * @test
 * @summary Test that implicit null checks are generated as expected for G1
            memory accesses with barriers.
 * @library /test/lib /
 * @requires vm.gc.Z
 * @run driver compiler.gcbarriers.TestZGCImplicitNullChecks
 */

public class TestZGCImplicitNullChecks {

    static class Outer {
        Object f;
    }

    static class OuterWithVolatileField {
        volatile Object f;
    }

    static final String ANY_BARRIER = ".*";

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:+UseZGC");
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, ANY_BARRIER, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.NULL_CHECK, "1" }, phase = CompilePhase.FINAL_CODE)
    public static Object testLoad(Outer o) {
        return o.f;
    }

    @Run(test = {"testLoad"},
         mode = RunMode.STANDALONE)
    public void runLoadTests() {
        Outer o = new Outer();
        Object o1 = new Object();
        o.f = o1;
        // Trigger compilation with implicit null check.
        for (int i = 0; i < 10_000; i++) {
            Object o2 = testLoad(o);
            Asserts.assertEquals(o1, o2);
        }
        // Trigger null pointer exception.
        o = null;
        boolean nullPointerException = false;
        try {
            testLoad(o);
        } catch (NullPointerException e) { nullPointerException = true; }
        Asserts.assertTrue(nullPointerException);
    }
}
