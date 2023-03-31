/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8296545
 * @requires vm.compiler2.enabled
 * @summary Blackholes should allow load optimizations
 * @library /test/lib /
 * @run driver compiler.c2.irTests.blackhole.BlackholeLoadOptoTest
 */

package compiler.c2.irTests.blackhole;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class BlackholeLoadOptoTest {

    public static void main(String[] args) {
        TestFramework.runWithFlags(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:CompileThreshold=100",
            "-XX:-TieredCompilation",
            "-XX:CompileCommand=blackhole,compiler.c2.irTests.blackhole.BlackholeLoadOptoTest::blackhole",
            "-XX:CompileCommand=dontinline,compiler.c2.irTests.blackhole.BlackholeLoadOptoTest::dontinline"
        );
    }

    static int x, y;


    /*
     * Negative test: check that dangling expressions are eliminated
     */

    @Test
    @IR(failOn = {IRNode.LOAD_I, IRNode.MUL_I})
    static void testNothing() {
        int r1 = x * y;
        int r2 = x * y;
    }

    @Run(test = "testNothing")
    static void runNothing() {
        testNothing();
    }

    /*
     * Auxiliary test: check that dontinline method does break optimizations
     */

    @Test
    @IR(counts = {IRNode.LOAD_I, "4"})
    @IR(counts = {IRNode.MUL_I, "2"})
    static void testDontline() {
        int r1 = x * y;
        dontinline(r1);
        int r2 = x * y;
        dontinline(r2);
    }

    static void dontinline(int x) {}

    @Run(test = "testDontline")
    static void runDontinline() {
        testDontline();
    }

    /*
     * Positive test: check that blackhole does not break optimizations
     */

    @Test
    @IR(counts = {IRNode.LOAD_I, "2"})
    @IR(counts = {IRNode.MUL_I, "1"})
    static void testBlackholed() {
        int r1 = x * y;
        blackhole(r1);
        int r2 = x * y;
        blackhole(r2);
    }

    static void blackhole(int x) {}

    @Run(test = "testBlackholed")
    static void runBlackholed() {
        testBlackholed();
    }

}
