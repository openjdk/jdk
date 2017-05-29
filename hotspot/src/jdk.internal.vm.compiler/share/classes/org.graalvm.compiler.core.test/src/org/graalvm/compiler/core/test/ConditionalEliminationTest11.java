/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTest11 extends ConditionalEliminationTestBase {
    @SuppressWarnings("all")
    public static int referenceSnippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        if ((a & 8) == 0) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    @Test
    public void test3() {
        // Test forward elimination of bitwise tests
        testConditionalElimination("test3Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test4Snippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) == 0) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    @Test
    public void test4() {
        // Test forward elimination of bitwise tests
        testConditionalElimination("test4Snippet", "referenceSnippet");
    }

    public static int test5Snippet(int a) {
        if ((a & 5) == 5) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 7) != 0) {
            return 0;
        }
        return 1;
    }

    @Test
    public void test5() {
        // Shouldn't be possible to optimize this
        testConditionalElimination("test5Snippet", "test5Snippet");
    }

    public static int test6Snippet(int a) {
        if ((a & 8) != 0) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return 0;
    }

    public static int reference6Snippet(int a) {
        if ((a & 8) != 0) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return 0;
    }

    @Test
    public void test6() {
        testConditionalElimination("test6Snippet", "reference6Snippet");
    }

    public static int test7Snippet(int a) {
        if ((a & 15) == 15) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) == 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    public static int reference7Snippet(int a) {
        if ((a & 8) == 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    @Test
    public void test7() {
        testConditionalElimination("test7Snippet", "reference7Snippet");
    }

    public static int test8Snippet(int a) {
        if ((a & 16) == 16) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 44) != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    public static int reference8Snippet(int a) {
        if ((a & 60) != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    @Ignore("requires merging of bit tests")
    @Test
    public void test8() {
        testConditionalElimination("test8Snippet", "reference8Snippet");
    }

    public static int test9Snippet(int a) {
        if ((a & 16) == 16) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 44) != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (a != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    public static int reference9Snippet(int a) {
        if (a != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return a;
    }

    @Test
    public void test9() {
        testConditionalElimination("test9Snippet", "reference9Snippet");
    }

    static class ByteHolder {
        public byte b;

        byte byteValue() {
            return b;
        }
    }

    public static int test10Snippet(ByteHolder b) {
        int v = b.byteValue();
        long a = v & 0xffffffff;
        if (v != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 16) == 16) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 44) != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }

        return v;
    }

    public static int reference10Snippet(ByteHolder b) {
        byte v = b.byteValue();
        if (v != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return v;
    }

    @Test
    @Ignore
    public void test10() {
        testConditionalElimination("test10Snippet", "reference10Snippet");
    }

    public static int test11Snippet(ByteHolder b) {
        int v = b.byteValue();
        long a = v & 0xffffffff;

        if ((a & 16) == 16) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if ((a & 44) != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (v != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return v;
    }

    public static int reference11Snippet(ByteHolder b) {
        byte v = b.byteValue();
        if (v != 44) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return v;
    }

    @Test
    @Ignore
    public void test11() {
        testConditionalElimination("test11Snippet", "reference11Snippet");
    }

}
