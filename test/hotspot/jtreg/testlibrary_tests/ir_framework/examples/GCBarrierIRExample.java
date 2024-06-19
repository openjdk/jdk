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

package ir_framework.examples;

import compiler.lib.ir_framework.*;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;

/**
 * @test
 * @bug 8330153
 * @summary Example test that illustrates the use of the IR test framework for
 *          verification of late-expanded GC barriers.
 * @library /test/lib /
 * @requires vm.gc.ZGenerational
 * @run driver ir_framework.examples.GCBarrierIRExample
 */

public class GCBarrierIRExample {

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
    static Outer o = new Outer();
    static Object oldVal = new Object();
    static Object newVal = new Object();

    public static void main(String[] args) {
        // These rules apply only to collectors that expand barriers at code
        // emission, such as ZGC. Because the collector selection flags are not
        // whitelisted (see IR framework's README.md file), the user (as opposed
        // to jtreg) needs to set these flags here.
        TestFramework.runWithFlags("-XX:+UseZGC", "-XX:+ZGenerational");
    }

    @Test
    // IR rules can be used to verify collector-specific barrier info (in this
    // case, that a ZGC barrier corresponds to a strong OOP reference). Barrier
    // info can only be verified after matching, e.g. at the FINAL_CODE phase.
    @IR(counts = {IRNode.Z_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, "strong", "1"},
        phase  = CompilePhase.FINAL_CODE)
    static boolean testBarrierOfCompareAndSwap() {
        return fVarHandle.compareAndSet(o, oldVal, newVal);
    }
}
