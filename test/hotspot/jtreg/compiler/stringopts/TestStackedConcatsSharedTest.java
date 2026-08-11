/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356246 8362117
 * @summary Test stacked string concatenations where the toString of the first StringBuilder
 *          is used as a shared test by two diamond Ifs in the second StringBuilder.
 *          (f): make sure we don't crash outright
 *          (g): external null checks depending on the same test/removed call should not give a wrong result.
 *          (h): multiple phis attached to the same diamond region; only one is a proper null check phi.
 *          (i): non-null check phi reused after intermediate stacked concat: check for correct result
 * @run main/othervm ${test.main.class}
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:CompileOnly=${test.main.class}::* ${test.main.class}
 */

package compiler.stringopts;

public class TestStackedConcatsSharedTest {

    public static void main(String... args) {
        f(); // one warmup call
        String s = f();
        if (!s.equals("")) {
            throw new RuntimeException("wrong result");
        }
        s = g();
        if (!s.equals("abcabcabc")) {
            System.out.println(s);
            throw new RuntimeException("wrong result");
        }
        s = h();
        if (!s.equals("abcabcnotnull")) {
            System.out.println(s);
            throw new RuntimeException("wrong result");
        }
        s = i();
        if (!s.equals("abcabcnotnull")) {
            System.out.println(s);
            throw new RuntimeException("wrong result");
        }
    }

    static String f() {
        String s = "";
        s = new StringBuilder().toString();
        // Warming up with many iterations invalidated the optimization due to an unstable If
        // associated with the valueOf calls below. Using -Xcomp for the test.
        s = new StringBuilder(String.valueOf(s)).append(String.valueOf(s)).toString();
        return s;
    }

    static String g() {
        String s = "abc";
        s = new StringBuilder(s).toString();
        s = new StringBuilder(String.valueOf(s)).append(String.valueOf(s)).toString() + (s == null ? "def" : "abc");
        return s;
    }

    static String h() {
        String s1 = new String("abc");
        String s2 = new StringBuilder(s1).append(s1).toString();
        String arg2 = "";
        if (s2 == null) {
          arg2 = "null";
        } else {
          arg2 = "notnull";
        }
        return new StringBuilder(s2).append(arg2).toString();
    }

    static String i() {
        String s1 = new String("abc");
        String s2 = new StringBuilder(s1).append(s1).toString();
        String arg2 = "";
        if (s2 == null) {
          arg2 = "null";
        } else {
          arg2 = "notnull";
        }
        String s3 = new StringBuilder(s2).toString();
        String s4 = new StringBuilder(s3).append(arg2).toString();
        return s4;
    }
}
