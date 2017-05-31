/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Test;

/**
 * Collection of tests for
 * {@link org.graalvm.compiler.phases.common.DominatorConditionalEliminationPhase} including those
 * that triggered bugs in this phase.
 */
public class ConditionalEliminationTest7 extends ConditionalEliminationTestBase {

    @SuppressWarnings("all")
    public static int test1Snippet(int a, Object b) {
        int sum = 0;
        for (int j = 0;; ++j) {
            ++sum;
            if (b instanceof String) {
                if (sum == 100) {
                    break;
                }
            }
        }
        String s = (String) b;
        return s.length() + sum;
    }

    @Test
    public void test1() {
        // One loop exit is skipped.
        testProxies("test1Snippet", 1);
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a, Object b) {
        int sum = 0;
        for (int j = 0;; ++j) {
            ++sum;
            if (b instanceof String) {
                break;
            }
        }
        String s = (String) b;
        return s.length() + sum;
    }

    @Test
    public void test2() {
        // The loop exit is the anchor => no proxy necessary.
        testProxies("test2Snippet", 0);
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a, Object b) {
        int sum = a;
        outer: while (true) {
            sum++;
            while (sum++ != 20) {
                while (sum++ != 30) {
                    while (sum++ != 40) {
                        while (sum++ != 50) {
                            if (b instanceof String) {
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        String s = (String) b;
        return s.length() + sum;
    }

    @Test
    public void test3() {
        // The break skips over 4 other loops.
        testProxies("test3Snippet", 4);
    }
}
