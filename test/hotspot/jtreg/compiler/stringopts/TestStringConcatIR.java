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
 * @bug 8362117
 * @summary Basic IR checks to verify that merge validation does not break concat optimizations.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;

public class TestStringConcatIR {

    public static void main(String[] args) {
        TestFramework.runWithFlags();
    }

    @Run(test = {"stackedConcat", "stackedConcatNullCheck"})
    public void runMethodA() {
        stackedConcat();
        stackedConcatNullCheck();
    }

    @Test
    @IR(counts = {IRNode.CALL,    ">= 9"}, phase = {CompilePhase.BEFORE_STRINGOPTS}) // at least init, append, tostring x 3
    @IR(counts = {IRNode.ALLOC,   "= 3"},  phase = {CompilePhase.BEFORE_STRINGOPTS})
    @IR(counts = {IRNode.CALL,    "= 0"},  phase = {CompilePhase.ITER_GVN1})
    @IR(counts = {IRNode.ALLOC,   "= 1"},  phase = {CompilePhase.ITER_GVN1})
    @IR(counts = {IRNode.STORE_B, "= 16"}, phase = {CompilePhase.ITER_GVN1})
    static String stackedConcat() {
        String s = "ab";
        s = new StringBuilder(s).append(s).toString();
        s = new StringBuilder(s).append(s).toString();
        s = new StringBuilder(s).append(s).toString();
        return s;
    }

    @Test
    @IR(applyIf = {"TieredCompilation", "true"},
        counts = {IRNode.CALL, ">= 9", IRNode.ALLOC, "= 3"},
        phase = {CompilePhase.BEFORE_STRINGOPTS})
    @IR(applyIf = {"TieredCompilation", "true"},
        counts = {IRNode.CALL, "= 0", IRNode.ALLOC, "= 1", IRNode.STORE_B, "= 24"},
        phase = {CompilePhase.ITER_GVN1})
    static String stackedConcatNullCheck() {
        String s = "abc";
        s = new StringBuilder(String.valueOf(s)).append(s).toString();
        s = new StringBuilder(String.valueOf(s)).append(String.valueOf(s)).toString();
        s = new StringBuilder(s).append(String.valueOf(s)).toString();
        return s;
    }
}
