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
 */
package compiler.inlining;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8382700
 * @summary verify that method inlining continues during incremental inline after it has stopped
 *          during parsing due to NodeCountInliningCutoff
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestDelayAfterInliningCutoff {
    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("-XX:+UnlockDiagnosticVMOptions");
        // Workaround the issue with incorrect call count at call sites
        framework.addFlags("-XX:MinInlineFrequencyRatio=0");
        framework.addScenarios(new Scenario(0, "-XX:+DelayAfterInliningCutoff"));
        framework.addScenarios(new Scenario(1, "-XX:-DelayAfterInliningCutoff"));
        framework.start();
    }

    @Test
    @IR(failOn = IRNode.CALL, applyIf = {"DelayAfterInliningCutoff", "true"})
    @IR(counts = {IRNode.CALL, ">= 1"}, applyIf = {"DelayAfterInliningCutoff", "false"})
    public static void test() {
        call1();
        call1();
        call1();
        call1();
    }

    private static void call1() {
        call2();
        call2();
        call2();
        call2();
    }

    private static void call2() {
        call3();
        call3();
        call3();
        call3();
    }

    private static void call3() {
        call4();
        call4();
        call4();
        call4();
    }

    private static void call4() {
        call5();
        call5();
        call5();
        call5();
    }

    private static void call5() {
        call6();
        call6();
        call6();
        call6();
    }

    private static void call6() {}
}
