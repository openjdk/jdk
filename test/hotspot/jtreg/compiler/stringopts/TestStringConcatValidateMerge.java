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
 * @summary Prevent crashes and miscompilations when external constructs
 *          could be confused for StringConcat append/toString-null checks.
 * @run main/othervm ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileOnly=${test.main.class}::test* ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileThreshold=500
 *                   -XX:CompileOnly=${test.main.class}::test* ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:-TieredCompilation
 *                   -XX:CompileOnly=${test.main.class}::test* ${test.main.class}
 */

package compiler.stringopts;

public class TestStringConcatValidateMerge {

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

        for (int i = 0; i < 100_000; i++) {
            test5(i % 2 == 0);
        }

        gold = test6(new StringBuilder(" "));
        for (int i = 0; i < 100_000; i++) {
            val = test6(new StringBuilder(" "));
        }
        if (!val.equals(gold)) {
            throw new RuntimeException("wrong result.");
        }

    }

    // test1-3: StringOpts can't stack as SB1 is used in a compare in SB2 (previously confused as a valid string null check).
    // test4: can't remove SB1's toString as it's used in an external comparison that needs it -> reject stacking
    // test5: hand-written branching that changes return value (previously mistaken to be a valid string null check).
    // test6: merge an unresolved stringbuilder with the intermediate value used in a compare: reject single concat.

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

    static String test5(boolean test) {
        String s1 = new String("b");
        String s2 = new StringBuilder(s1).append(s1).toString();
        String arg1 = "";
        String arg2 = "";
        if (s2 == null) {
          arg2 = "null";
        } else {
          arg2 = "Some other string";
        }
        return new StringBuffer(s2).append(arg2).toString();
    }

    static String test6(StringBuilder c) {
        StringBuilder s = new StringBuilder().append(" ");
        String ret = s.append(s == c ? "abc" : "   ").toString();
        return ret;
    }

}
