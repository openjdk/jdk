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
 * @summary Similar type of test scenarios as in TestStringConcatValidateMerge.java
 *          but for problems which manifested with -Xcomp
 *          (f): stringopts shouldn't confuse ternary expression with string null check
 *               and fold away diamond phi arbitrarily leading to wrong result when depending on
 *               toString of SB1.
 *          (g): variant of (f) with append instead of toString
 * @library /test/lib /
 * @run main/othervm ${test.main.class}
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=${test.main.class}::* ${test.main.class}
 */

package compiler.stringopts;

import jdk.test.lib.Asserts;

public class TestStringConcatValidateMergeXcomp {

    public static void main (String... args) {
        new StringBuilder(); // load the class
        f();
        g();
    }

    static String f() {
        String s = "a";
        s = new StringBuilder().append(s).append(s).toString();
        s = new StringBuilder().append(s).append((s == "xx") ? s : "aa").toString();
        Asserts.assertEQ(s, "aaaa"); // in particular, we should not have s.equals("aaxx");
        return s;
    }

    static String g() {
        String s = "a";
        StringBuilder sb0 = new StringBuilder();
        s = new StringBuilder().append(s).append(s).toString();
        StringBuilder sb2 = new StringBuilder().append(s);
        s = sb2.append((sb2 == sb0) ? "xx" : "aa").toString();
        Asserts.assertEQ(s, "aaaa"); // in particular, we should not have s.equals("aaxx").
        return s;
    }
}
