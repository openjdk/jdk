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

import org.graalvm.compiler.api.directives.GraalDirectives;

/**
 * Collection of tests for
 * {@link org.graalvm.compiler.phases.common.DominatorConditionalEliminationPhase} including those
 * that triggered bugs in this phase.
 */
public class ConditionalEliminationTest1 extends ConditionalEliminationTestBase {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    @SuppressWarnings("all")
    public static int referenceSnippet(int a) {
        if (a == 0) {
            return 1;
        }
        return 0;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", REFERENCE_SNIPPET);
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        if (a == 0) {
            if (a == 5) {
                return 100;
            }
            if (a > 100) {
                if (a == 0) {
                    return 200;
                }
            }
            if (a != 2) {
                return 1;
            }
        }
        return 0;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", REFERENCE_SNIPPET);
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        if (a == 0) {
            if (a > 100) {
                if (a == 0) {
                    return 200;
                }
            }
            if (a != 2) {
                return 1;
            }
        }
        return 0;
    }

    @Test
    public void test3() {
        testConditionalElimination("test3Snippet", REFERENCE_SNIPPET);
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        if (a == 0) {
            if (a < 1) {
                if (a < 2) {
                    if (a < 3) {
                        if (a > -1) {
                            if (a > -2) {
                                if (a > -3) {
                                    if (a == 1) {
                                        return 42;
                                    } else {
                                        return 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    @SuppressWarnings("all")
    public static int test4Snippet(int a, int b) {
        if (b < 1) {
            GraalDirectives.controlFlowAnchor();
            if (b < 0) {
                return 1;
            }
        }
        return 0;
    }

    @Test
    public void test4() {
        testConditionalElimination("test4Snippet", "test4Snippet");
    }

    @SuppressWarnings("all")
    public static int test5Snippet(int a, int b) {
        if ((b & 3) == 0) {
            GraalDirectives.controlFlowAnchor();
            if ((b & 7) == 0) {
                GraalDirectives.controlFlowAnchor();
                return 1;
            }
        } else {
            GraalDirectives.controlFlowAnchor();
            if ((b & 1) == 0) {
                GraalDirectives.controlFlowAnchor();
                return 2;
            }
        }
        return 0;
    }

    @Test
    public void test5() {
        testConditionalElimination("test5Snippet", "test5Snippet");
    }
}
