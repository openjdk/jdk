/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

/* @test TestWrongBarrierDisable
 * @summary Test that disabling wrong barriers fails early
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @library /test/lib
 * @run main/othervm TestWrongBarrierDisable
 */

import java.util.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestWrongBarrierDisable {

    public static void main(String[] args) throws Exception {
        String[] concurrent = {
                "ShenandoahLoadRefBarrier",
                "ShenandoahCASBarrier",
                "ShenandoahCloneBarrier",
                "ShenandoahSATBBarrier",
                "ShenandoahKeepAliveBarrier",
        };

        String[] traversal = {
                "ShenandoahLoadRefBarrier",
                "ShenandoahCASBarrier",
                "ShenandoahCloneBarrier",
        };

        shouldFailAll("adaptive",   concurrent);
        shouldFailAll("static",     concurrent);
        shouldFailAll("compact",    concurrent);
        shouldFailAll("aggressive", concurrent);
        shouldFailAll("traversal",  traversal);
        shouldPassAll("passive",    concurrent);
        shouldPassAll("passive",    traversal);
    }

    private static void shouldFailAll(String h, String[] barriers) throws Exception {
        for (String b : barriers) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahGCHeuristics=" + h,
                    "-XX:-" + b,
                    "-version"
            );
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldNotHaveExitValue(0);
            output.shouldContain("Heuristics needs ");
            output.shouldContain("to work correctly");
        }
    }

    private static void shouldPassAll(String h, String[] barriers) throws Exception {
        for (String b : barriers) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahGCHeuristics=" + h,
                    "-XX:-" + b,
                    "-version"
            );
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
        }
    }

}
