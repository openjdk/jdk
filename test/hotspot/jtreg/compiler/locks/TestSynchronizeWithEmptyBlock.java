/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8337660
 * @summary Test that C2 does not remove blocks with BoxLock nodes that are
 *          otherwise empty.
 * @run main/othervm -Xbatch
 *                   -XX:CompileOnly=compiler.locks.TestSynchronizeWithEmptyBlock::*
 *                   compiler.locks.TestSynchronizeWithEmptyBlock
 */

package compiler.locks;

public class TestSynchronizeWithEmptyBlock {

    static int c;
    static final Object obj = new Object();

    static void test1() {
        synchronized (TestSynchronizeWithEmptyBlock.class) {
            int i = 0;
            while (i < 1000) {
                i++;
                if (i < 5);
            }
        }
        synchronized (TestSynchronizeWithEmptyBlock.class) {
            int i = 0;
            do {
                i++;
                if (i < 4) {
                    boolean p = true;
                    int j = 0;
                    do {
                        j++;
                        if (p) {
                            c++;
                        }
                    } while (j < 100);
                }
            } while (i < 1000);
        }
    }

    static void test2() {
        synchronized (obj) {
            for (long i = 0; i < 1_000_000_000_000L; i+=6_500_000_000L) {}
        }
        synchronized (obj) {
            for (long i = 0; i < 1_000_000_000_000L; i+=6_500_000_000L) {}
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test1();
        }
        for (int i = 0; i < 10_000; i++) {
            test2();
        }
    }
}
