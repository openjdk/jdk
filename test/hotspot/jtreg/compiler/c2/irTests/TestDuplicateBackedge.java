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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Objects;

/*
 * @test
 * @bug 8279888
 * @summary Local variable independently used by multiple loops can interfere with loop optimizations
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestDuplicateBackedge
 */

public class TestDuplicateBackedge {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=1");
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=1", "-XX:-DuplicateBackedge");
    }

    @Test
    @IR(applyIf = { "DuplicateBackedge", "true" }, counts = { IRNode.LOOP, "1", IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "DuplicateBackedge", "false" }, counts = { IRNode.LOOP, "1" })
    @IR(applyIf = { "DuplicateBackedge", "false" }, failOn = { IRNode.COUNTEDLOOP })
    public static float test() {
        float res = 1;
        for (int i = 1;;) {
            if (i % 10 == 0) {
                i = (i * 2) + 1;
                res /= 42;
            } else {
                i++;
                res *= 42;
            }
            if (i >= 1000) {
                break;
            }
        }
        return res;
    }

}
