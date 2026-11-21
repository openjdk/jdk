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
 *
 */

/*
 * @test
 * @bug 8379513
 * @summary A SafePoint in a non-counted loop must not be removed because of a
 *          boxing call that is still a macro node and could be eliminated later.
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestBoxingCallLoopSafepoint::test
 *                   -XX:CompileCommand=dontinline,java.lang.Double::valueOf
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+SafepointTimeout -XX:SafepointTimeoutDelay=3000
 *                   -XX:+AbortVMOnSafepointTimeout
 *                   -XX:-CreateCoredumpOnCrash
 *                   compiler.loopopts.TestBoxingCallLoopSafepoint
 */

package compiler.loopopts;

public class TestBoxingCallLoopSafepoint {
    static double limit;
    static long lFld;
    static double dFld;
    static volatile boolean inTest;

    public static void main(String[] args) throws Exception {
        limit = 1;
        Double.valueOf(3.4); // Load Double class.
        test(); // Compile with C2 (-Xcomp).
        inTest = false; // Reset: the warm-up call also set it.

        limit = Double.POSITIVE_INFINITY; // Make the loop run indefinitely.
        Thread thread = new Thread(TestBoxingCallLoopSafepoint::test);
        thread.setDaemon(true);
        thread.start();
        while (!inTest) {
            Thread.onSpinWait();
        }
        // There is no safepoint poll between the store to inTest and the loop,
        // so the thread can only reach this safepoint from inside the loop.
        System.gc(); // Trigger safepoint
    }

    static double test() {
        Double dBox = null;
        double d = 0;
        inTest = true; // Signal main thread. No safepoint poll until the loop.
        for (double i = 0; i < limit; i++) { // Non-counted loop.
            // Non-inlined boxing call whose only use is the loop SafePoint.
            // Before the fix:
            // - SafePointNode::Identity() treated the boxing call as a guaranteed
            //   safepoint and removed the loop SafePoint during post loop opts IGVN.
            // - The boxing call then had no more uses and was removed during
            //   macro expansion, leaving the loop without any safepoint.
            dBox = Double.valueOf(dFld);
            // MoveL2D:
            // 1) Not optimized until post loop opts
            // 2) Post loop opts IGVN:
            //    - Optimize MoveL2D -> LoadD
            //    - Enqueue SafePoint use to IGVN
            //    - SafePointNode::Identity: Sees that guaranteed_safepoint() for valueOf returns true
            //                               -> removes seemingly useless SafePoint because we already have
            //                                  a guaranteed safepoint from valueOf
            d = Double.longBitsToDouble(lFld);
            // SafePoint before exit check.
        }

        Double anotherDBox = dBox; // Keep dBox alive in the loop SafePoint.
        return d; // keep alive
    }
}
