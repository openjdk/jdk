/*
 * Copyright (c) 2025, Red Hat and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8344251
 * @summary Test that blackhole without control input are removed
 * @library /test/lib /
 * @run driver compiler.blackhole.DeadBhElimination
 */

package compiler.blackhole;

import compiler.lib.ir_framework.*;

public class DeadBhElimination {
    public static void main(String[] args) {
        TestFramework.runWithFlags(
                "-XX:-TieredCompilation",
                "-XX:+UnlockExperimentalVMOptions",
                // Prevent the dead branches to be compiled into an uncommon trap
                // instead of generating a BlackholeNode.
                "-XX:PerMethodTrapLimit=0",
                "-XX:PerMethodSpecTrapLimit=0",
                "-XX:CompileCommand=blackhole,compiler.blackhole.DeadBhElimination::iAmABlackhole"
        );
    }

    static public void iAmABlackhole(int x) {}

    @Test
    @IR(counts = {IRNode.BLACKHOLE, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.BLACKHOLE}, phase = CompilePhase.FINAL_CODE)
    static void removalAfterLoopOpt() {
        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);
        // b == 77, known after first loop opts round
        // loop is detected as empty loop

        if (b == 78) { // dead
            iAmABlackhole(a);
        }
    }


}
