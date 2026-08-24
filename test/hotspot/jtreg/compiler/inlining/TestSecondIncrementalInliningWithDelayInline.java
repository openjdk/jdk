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

/*
 * @test
 * @bug 8382605
 * @summary Verify that the second incremental inlining pass is performed
 *          when a call discovered during boxing late inlining is delayed
 *          by a delayinline directive.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.inlining;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestSecondIncrementalInliningWithDelayInline {

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:CompileCommand=delayinline,java.lang.Integer::<init>");
    }

    @Test
    @IR(failOn = {
            IRNode.STATIC_CALL_OF_METHOD,
            "java.lang.Integer::<init>"
        },
        phase = CompilePhase.BEFORE_MATCHING)
    public static Integer test(int value) {
        return Integer.valueOf(value);
    }

    @Run(test = "test")
    public static void run() {
        Asserts.assertEQ(test(Integer.MIN_VALUE), Integer.MIN_VALUE);
    }
}
