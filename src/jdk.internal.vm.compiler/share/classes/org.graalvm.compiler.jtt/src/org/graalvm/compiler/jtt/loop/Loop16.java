/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop


package org.graalvm.compiler.jtt.loop;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests exiting 2 loops at the same time with escape-analysed values flowing out of loops
 */
public class Loop16 extends JTTTest {

    private static class TestClass {
        public int a;
        public int b;
        public int c;

        public int run(int count) {
            l1: for (int i = 0; i <= count; i++) {
                if (i > 5) {
                    for (int j = 0; j < i; j++) {
                        a += i;
                        if (a > 500) {
                            break l1;
                        }
                    }
                } else if (i > 7) {
                    b += i;
                } else {
                    c += i;
                }
            }
            return a + b + c;
        }
    }

    public static int test(int count) {
        return new TestClass().run(count);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 40);
    }
}
