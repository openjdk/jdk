/*
 * Copyright (c) 2022, 2023, Red Hat, Inc. All rights reserved.
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
 * bug 8290529
 * @summary verify that x <u 1 is transformed to x == 0
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="riscv64"
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.CmpUWithZero
 */

public class CmpUWithZero {
    static volatile boolean field;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = { IRNode.CMP_I, "1" })
    @IR(failOn = { IRNode.CMP_U})
    public static void test(int x) {
        if (Integer.compareUnsigned(x, 1) < 0) {
            field = true;
        } else {
            field = false;
        }
    }

    @Run(test = "test")
    private void testRunner() {
        test(0);
        test(42);
    }

}
