/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8362117
 * @summary Prevent crashes and miscompilations when uncommon trap tests
 *          are confused with string null checks
 * @run main/othervm compiler.stringopts.TestStackedConcatsValidateMerge
 * @run main/othervm -Xbatch
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsValidateMerge::test*
 *                   compiler.stringopts.TestStackedConcatsValidateMerge
 * @run main/othervm -Xbatch
 *                   -XX:CompileThreshold=500
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsValidateMerge::test*
 *                   compiler.stringopts.TestStackedConcatsValidateMerge
 * @run main/othervm -Xbatch
 *                   -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.stringopts.TestStackedConcatsValidateMerge::test*
 *                   compiler.stringopts.TestStackedConcatsValidateMerge
 */

package compiler.stringopts;

public class TestStackedConcatsValidateMerge {

    public static void main (String... args) {

        String gold = test1(false);
        for (int i = 0; i < 10_000; i++) {
            test1((i & 1) == 0);
        }
        String val = test1(false);
        if (!val.equals(gold)) {
            throw new RuntimeException("wrong value: " + val + " vs " + gold);
        }

        for (int t = 0; t < 10_000; t++) {
            // The following line is probably important for profiling.
            try { new String((String) null); } catch (NullPointerException e) {}
            test2();
        }

        for (int t = 0; t < 10_000; t++) {
            try {
                if (t % 2 != 0) {
                    test3(null, "B");
                } else {
                    test3("A", null);
                }
            } catch (NullPointerException e) {
                // expected
            }
        }

        for (int i = 0; i < 100_000; i++) {
            test4();
        }

    }

    // JDK-8385429
    static String test1(boolean flag) {
        String s = new StringBuilder("ABC").toString();
        return new StringBuilder().append(s).append(s == null ? "x" : "y").append(flag ? "z" : s).toString();
    }

    // JDK-8385428
    static int test2() {
        String s1 = (("a" == null) ? "b" : "c") + 'd';
        String s2 = new StringBuilder(s1).toString();
        String s3 = new StringBuilder(s2).append(s1).append(s2 == null ? "" : s2).toString();
        return s3.length();
    }

    // JDK-8385415
    static Object test3(String a, String b) {
        String s1 = new String(b);
        String s2 = new StringBuffer(s1).append(s1).toString();
        return new StringBuffer(s2).append(a).append(s2 == null ? "" : s2).toString();
    }

    // JDK-8384130
    static String test4() {
        String s = new StringBuilder().toString();
        return new StringBuilder(s).toString() == s ? "a" : "b";
    }
}
