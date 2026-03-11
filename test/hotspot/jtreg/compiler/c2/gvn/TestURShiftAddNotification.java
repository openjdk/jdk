/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import jdk.test.lib.Platform;

/*
 * @test
 * @bug 8379460
 * @summary When AddI/AddL inputs change during IGVN (e.g. due to loop peeling),
 *          URShift users must be re-added to the IGVN worklist.
 * @library /test/lib /
 * @run driver compiler.c2.gvn.TestURShiftAddNotification
 */
public class TestURShiftAddNotification {

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.addScenarios(new Scenario(0));
        if (Platform.isDebugBuild()) {
            framework.addScenarios(new Scenario(1, "-Xcomp", "-XX:+StressLoopPeeling", "-XX:VerifyIterativeGVN=1110",
                "-XX:CompileCommand=compileonly,compiler.c2.gvn.TestURShiftAddNotification::*"));
        }
        framework.start();
    }

    long lArr[][];

    @Run(test = "test")
    public void runTest() {
        lArr = new long[10][10];
        test();
    }

    @Test
    void test() {
        int i, j, x = 4;
        for (i = 6; i < 8; i++) {
            for (j = 1; j < 10; j++) {
                x += j * x;
                switch (x >>> 1) {
                    case 6:
                        lArr[1] = lArr[i];
                }
            }
        }
    }
}
