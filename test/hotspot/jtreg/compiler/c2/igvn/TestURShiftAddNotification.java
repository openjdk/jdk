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
package compiler.c2.igvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8379460
 * @key randomness
 * @summary When AddI/AddL inputs change during IGVN, URShift users must be re-added
 *          to the IGVN worklist so they can re-check the ((X << z) + Y) >>> z optimization.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestURShiftAddNotification {

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.addScenarios(new Scenario(0));
        if (Platform.isDebugBuild()) {
            framework.addScenarios(new Scenario(1, "-XX:VerifyIterativeGVN=1110"));
        }
        framework.start();
    }

    // The trick: a loop whose exit value is only known after loop optimization.
    // During initial GVN, i is a Phi, so (x << C) * i stays as MulI — URShift
    // can't see the LShiftI input through the MulI. After loop opts resolve
    // i = 1, MulI identity-folds to LShiftI (same type, no cascade), and
    // without the fix URShift is never re-notified about the new LShiftI input.

    @Run(test = {"testI", "testL"})
    public void runTests() {
        int xi = RANDOM.nextInt();
        int yi = RANDOM.nextInt();
        long xl = RANDOM.nextLong();
        long yl = RANDOM.nextLong();

        Asserts.assertEQ(((xi << 3) + yi) >>> 3, testI(xi, yi));
        Asserts.assertEQ(((xl << 9) + yl) >>> 9, testL(xl, yl));
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.MUL_I},
        counts = {IRNode.URSHIFT_I, "1", IRNode.AND_I, "1"})
    static int testI(int x, int y) {
        int i;
        for (i = -10; i < 1; i++) { }
        int c = (x << 3) * i;
        return (c + y) >>> 3;
    }

    @Test
    @IR(failOn = {IRNode.LSHIFT_L, IRNode.MUL_L},
        counts = {IRNode.URSHIFT_L, "1", IRNode.AND_L, "1"})
    static long testL(long x, long y) {
        int i;
        for (i = -10; i < 1; i++) { }
        long c = (x << 9) * i;
        return (c + y) >>> 9;
    }
}
