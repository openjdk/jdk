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

/*
 * @test
 * @bug 8291665
 * @summary C2: assert compiling SSLEngineInputRecord::decodeInputRecord
 * @run main/othervm -Xbatch TestNewArrayOutsideLoopValidLengthTestInLoop
 */

import java.util.Arrays;

public class TestNewArrayOutsideLoopValidLengthTestInLoop {
    private static volatile int barrier;

    public static void main(String[] args) {
        boolean[] allFalse = new boolean[100];
        boolean[] allTrue = new boolean[100];
        Arrays.fill(allTrue, true);
        for (int i = 0; i < 20_000; i++) {
            test1(allFalse, allFalse, true);
            test1(allTrue, allFalse, true);
            test1(allFalse, allTrue, true);
            test1(allFalse, allFalse, false);
        }
    }

    private static int[] test1(boolean[] flags1, boolean[] flags2, boolean flag) {
        for (int i = 1; i < 100; i *= 2) {
            boolean f = false;
            int j = i;
            if (flags1[i]) {
                barrier = 1;
                f = true;
                j = i / 2;
            }
            if (flag) {
                barrier = 1;
            }
            if (f) {
                return new int[j];
            }
            if (flags2[i]) {
                return new int[j];
            }
        }
        return null;
    }
}
