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

/*
 * @test
 * @bug 8259609 8276116
 * @summary C2: optimize long range checks in long counted loops
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCountedLoopPhiValue
 */

public class TestCountedLoopPhiValue {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=0");
    }

    @Test
    @IR(failOn = { IRNode.IF })
    public static float test1() {
        int i = 0;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i++;
        } while (i < 10);
        if (j < 10) {
            v *= 2;
        }
        return v;
    }

    @Test
    @IR(failOn = { IRNode.IF })
    public static float test2() {
        int i = 0;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i += 2;
        } while (i < 10);
        if (j < 9) {
            v *= 2;
        }
        return v;
    }

    @Test
    @IR(failOn = { IRNode.IF })
    public static float test3() {
        int i = 10;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i--;
        } while (i > 0);
        if (j > 0) {
            v *= 2;
        }
        return v;
    }

    @Test
    @IR(failOn = { IRNode.IF })
    public static float test4() {
        int i = 10;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i -= 2;
        } while (i > 0);
        if (j > 1) {
            v *= 2;
        }
        return v;
    }
}
