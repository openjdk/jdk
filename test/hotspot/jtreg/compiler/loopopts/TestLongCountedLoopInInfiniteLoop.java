/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8336478
 * @summary C2: assert(!n->as_Loop()->is_loop_nest_inner_loop() || _loop_opts_cnt == 0) failed: should have been turned into a counted loop
 * @compile LongCountedLoopInInfiniteLoop.jasm
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -Xcomp -XX:PerMethodTrapLimit=0 -XX:PerMethodSpecTrapLimit=0
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:StressLongCountedLoop=2000000
 *                   -XX:CompileCommand=compileonly,TestLongCountedLoopInInfiniteLoop::test TestLongCountedLoopInInfiniteLoop
 */

public class TestLongCountedLoopInInfiniteLoop {
    public static void main(String[] args) {
        LongCountedLoopInInfiniteLoop obj = new LongCountedLoopInInfiniteLoop();
        test(false, obj);
    }

    private static void test(boolean flag, LongCountedLoopInInfiniteLoop obj) {
        if (flag) {
            obj.test();
        }
    }
}
