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
 * @bug 8374582
 * @summary Tests the creation and removal of opaque nodes at range checks points in string intrinsics.
 * @requires vm.flagless
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.intrinsics.string;

import compiler.lib.ir_framework.*;

public class TestOpaqueConstantBoolNodes {

    static byte[] bytes = new byte[42];

    public static void main(String[] args) {
        TestFramework.runWithFlags(
            "-XX:CompileCommand=inline,java.lang.String::*",
            "-XX:CompileCommand=inline,java.lang.StringCoding::*",
            "-XX:CompileCommand=exclude,jdk.internal.util.Preconditions::checkFromIndexSize");
    }

    @Setup
    private static Object[] setup() {
        return new Object[] {bytes, 2, 23};
    }

    @Test
    @IR(counts = {IRNode.OPAQUE_CONSTANT_BOOL, "3"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.OPAQUE_CONSTANT_BOOL}, phase = CompilePhase.AFTER_MACRO_EXPANSION)
    @Arguments(setup = "setup")
    private static String test(byte[] bytes, int i, int l) {
        return new String(bytes, i , l);
    }
}

