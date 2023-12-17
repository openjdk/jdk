/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8296412
 * @compile TestInfiniteLoopWithUnmergedBackedges.jasm
 * @summary Infinite loops may not have the backedges merged, before we call IdealLoopTree::check_safepts
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:-LoopUnswitching
 *      -XX:CompileCommand=compileonly,TestInfiniteLoopWithUnmergedBackedges::test*
 *      TestInfiniteLoopWithUnmergedBackedgesMain
 */

public class TestInfiniteLoopWithUnmergedBackedgesMain {
    public static void main (String[] args) {
        TestInfiniteLoopWithUnmergedBackedges.test_001(1, 0, 0, 0, 0);
        TestInfiniteLoopWithUnmergedBackedges.test_002(1, 0, 0, 0, 0);
        TestInfiniteLoopWithUnmergedBackedges.test_003(1, 0, 0, 0, 0);
        TestInfiniteLoopWithUnmergedBackedges.test_004(1, 0, 0, 0, 0);
    }
}
