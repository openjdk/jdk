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
 * @bug 8284848
 * @requires vm.compiler2.enabled
 * @summary Blackhole arguments are globally escaping, thus preventing advanced EA optimizations
 * @library /test/lib /
 * @run driver compiler.c2.irTests.blackhole.BlackholeSyncEATest
 */

package compiler.c2.irTests.blackhole;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class BlackholeSyncEATest {

    public static void main(String[] args) {
        TestFramework.runWithFlags(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:CompileCommand=blackhole,compiler.c2.irTests.blackhole.BlackholeSyncEATest::blackhole",
            "-XX:CompileCommand=dontinline,compiler.c2.irTests.blackhole.BlackholeSyncEATest::dontinline"
        );
    }

    /*
     * Negative test: check that dontinline method still allows EA to eliminate the synchronization.
     */

    @Test
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testDontline() {
        Object o = new Object();
        synchronized (o) {}
        dontinline(o);
    }

    static void dontinline(Object o) {}

    @Run(test = "testDontline")
    static void runDontinline() {
        testDontline();
    }

    /*
     * Positive test: check that blackhole keeps the synchronization in.
     */

    @Test
    @IR(counts = {IRNode.FAST_LOCK, "1"})
    @IR(counts = {IRNode.FAST_UNLOCK, "1"})
    static void testBlackholed() {
        Object o = new Object();
        synchronized (o) {}
        blackhole(o);
    }

    static void blackhole(Object o) {}

    @Run(test = "testBlackholed")
    static void runBlackholed() {
        testBlackholed();
    }

}
