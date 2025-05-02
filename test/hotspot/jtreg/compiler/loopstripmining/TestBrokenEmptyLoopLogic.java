/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8315088
 * @requires vm.compiler2.enabled
 * @summary C2: assert(wq.size() - before == EMPTY_LOOP_SIZE) failed: expect the EMPTY_LOOP_SIZE nodes of this body if empty
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,TestBrokenEmptyLoopLogic::* -XX:-TieredCompilation TestBrokenEmptyLoopLogic
 *
 */

public class TestBrokenEmptyLoopLogic {

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    static void test() {
        int i8 = 209, i = 1, i12 = 1;
        while (++i < 8) {
            for (int j = 5; j > 1; j -= 2) {
                i12 = 1;
                do {
                } while (++i12 < 3);
            }
            for (int j = i; j < 5; ++j) {
                i8 += i12;
            }
        }
    }
}
