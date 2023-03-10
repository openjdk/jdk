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

/*
 * @test
 * @bug 8275202	
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-BackgroundCompilation TestLSMDeadInLoopPredication
 */


public class TestLSMDeadInLoopPredication {
    private static volatile int barrier;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(false, 100);
            testHelper(100, 1000);
            test2(false, 100);
            testHelper2(100, 1000);
        }
    }

    private static void test(boolean flag, int stop) {
        for (int i = 0; i < 10; i++) {
            testHelper(stop, 1);
            if (i % 2 == 0) {
                if (flag) {
                }
            }
        }
    }

    private static void testHelper(int stop, int stop2) {
        int j = 0;
        for(;;) {
            synchronized (new Object()) {
            }
            barrier = 0x42;
            if (j+1 > stop2) {
                break;
            }
            j++;
            if (j >= stop) {
                break;
            }
        }
    }

    private static void test2(boolean flag, int stop) {
        for (int i = 0; i < 10; i++) {
            testHelper2(stop, 0);
            if (i % 2 == 0) {
                if (flag) {
                }
            }
        }
    }

    private static void testHelper2(int stop, int stop2) {
        int j = 0;
        for(;;) {
            synchronized (new Object()) {
            }
            barrier = 0x42;
            if (j > stop2) {
                break;
            }
            j++;
            if (j >= stop) {
                break;
            }
        }
    }
}
