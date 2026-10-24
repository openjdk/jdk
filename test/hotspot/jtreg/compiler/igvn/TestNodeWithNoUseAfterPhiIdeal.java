/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8373524
 * @summary C2: no reachable node should have no use
 * @run main/othervm -Xbatch ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.igvn;

public class TestNodeWithNoUseAfterPhiIdeal {
    volatile boolean _mutatorToggle;

    boolean _mutatorFlip() {
        synchronized (TestNodeWithNoUseAfterPhiIdeal.class) {
            _mutatorToggle = new MyBoolean(!_mutatorToggle).v;
            return _mutatorToggle;
        }
    }

    class MyBoolean {
        boolean v;
        MyBoolean(boolean v) {
            int N = 32;
            for (int i = 0; i < N; i++) {
                this.v = v;
            }
        }
    }

    int[] arr;
    void test() {
        int limit = 2;
        boolean flag1 = _mutatorFlip();
        for (; limit < 4; limit *= 2) {
            if (flag1) {
                break;
            }
        }
        int zero = 34;
        for (int peel = 2; peel < limit; peel++) {
            synchronized (TestNodeWithNoUseAfterPhiIdeal.class) {
                zero = 0;
            }
        }
        if (zero == 0) {
            arr = new int[8];
        } else {
            int M = 4;
            for (int i = 0; i < M; i++) {
                boolean flag2 = _mutatorFlip();
                boolean flag3 = _mutatorFlip();
                if (flag2) {
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        TestNodeWithNoUseAfterPhiIdeal t = new TestNodeWithNoUseAfterPhiIdeal();
        for (int i = 0; i < 10_000; i++) {
            t.test();
        }
    }
}
