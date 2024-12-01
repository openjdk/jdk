/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * bug 8312980
 * @summary C2: "malformed control flow" created during incremental inlining
 * @requires vm.compiler2.enabled
 * @run main/othervm  -XX:CompileCommand=compileonly,TestReplacedNodesAfterLateInlineManyPaths::* -XX:-BackgroundCompilation
 *                    -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline TestReplacedNodesAfterLateInlineManyPaths
 */

public class TestReplacedNodesAfterLateInlineManyPaths {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test("" + i);
        }
    }

    public static int test(String s) {
        int result = 0;
        int len = s.length();
        int i = 0;
        while (i < len) {
            // charAt is inlined late, and i is constrained by CastII(i >= 0)
            // The constraint comes from intrinsic checkIndex
            s.charAt(i);
            // Graph below intentionally branches out 4x, and merges again (4-fold diamonds).
            // This creates an exponential explosion in number of paths.
            int e = i;
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            // Comment out lines below to make it not assert
            // assert(C->live_nodes() <= C->max_node_limit()) failed: Live Node limit exceeded limit
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            e = (e & 7) + (e & 31) + (e & 1111) + (e & 1000_000);
            result += e;
            i++;
        }
        return result;
    }
}
