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
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8278228
 * @summary C2: Improve identical back-to-back if elimination
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestBackToBackIfs
 */

public class TestBackToBackIfs {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static private int int_field;

    @Test
    @IR(counts = { IRNode.IF, "1" })
    public static void test(int a, int b) {
        if (a == b) {
            int_field = 0x42;
        } else {
            int_field = 42;
        }
        if (a == b) {
            int_field = 0x42;
        } else {
            int_field = 42;
        }
    }

    @Run(test = "test")
    public static void test_runner() {
        test(42, 0x42);
        test(0x42, 0x42);
    }
}
