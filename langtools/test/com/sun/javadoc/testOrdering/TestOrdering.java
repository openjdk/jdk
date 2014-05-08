/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8039410 8042601
 * @summary test to determine if members are ordered correctly
 * @author ksrini
 * @library ../lib/
 * @build JavadocTester
 * @build TestOrdering
 * @run main TestOrdering
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestOrdering extends JavadocTester {
    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) throws Exception {
        TestOrdering tester = new TestOrdering();
        // test unnamed packages
        String[] ARGS = {
            "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "-use",
             SRC_DIR + "/C.java", SRC_DIR + "/UsedInC.java"
        };
        tester.runJavadoc(ARGS);
        checkExecutableMemberOrdering(tester.readFileToString("class-use/UsedInC.html"));

        // next test using packages
        String[] ARGS1 = {
            "-d", OUTPUT_DIR + "-1", "-sourcepath", SRC_DIR, "-use",
            "pkg1"
        };
        tester.runJavadoc(ARGS1);
        checkClassUseOrdering(tester.readFileToString("pkg1/class-use/UsedClass.html"));
        checkIndexPathOrdering(tester.readFileToString("index-all.html"));
    }

    static void checkExecutableMemberOrdering(String usePage) {
        // check constructors
        int idx1 = usePage.indexOf("C.html#C-UsedInC");
        int idx2 = usePage.indexOf("C.html#C-UsedInC-int");
        int idx3 = usePage.indexOf("C.html#C-UsedInC-java.lang.String");
        if (idx1 == -1 || idx2 == -1 || idx3 == -1) {
            throw new Error("ctor strings not found");
        }
        if (idx1 > idx2 || idx2 > idx3 || idx1 > idx3) {
            throw new Error("ctor strings are out of order");
        }

        // check methods
        idx1 = usePage.indexOf("C.html#ymethod-int");
        idx2 = usePage.indexOf("C.html#ymethod-java.lang.String");
        if (idx1 == -1 || idx2 == -1) {
            throw new Error("#ymethod strings not found");
        }
        if (idx1 > idx2) {
            throw new Error("#ymethod strings are out of order");
        }
        System.out.println("Executable Member Ordering: OK");
    }

    static void checkClassUseOrdering(String usePage) {
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#zfield");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#fieldInC#ITERATION#");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#zmethod-pkg1.UsedClass");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#methodInC#ITERATION#");
    }

    static void checkClassUseOrdering(String usePage, String searchString) {
        int lastidx = 0;
        System.out.println("testing for " + searchString);
        for (int i = 1; i < 5; i++) {
            String s = searchString.replaceAll("#ITERATION#", Integer.toString(i));
            System.out.println(s);
            int idx = usePage.indexOf(s);
            if (idx < lastidx) {
                throw new Error(s + ", member ordering error, last:" + lastidx + ", got:" + idx);
            }
            System.out.println("\tlast: " + lastidx + " got:" + idx);
            lastidx = idx;
        }
    }

    static void checkIndexPathOrdering(String indexPage) {
        String[] OrderedExpectedStrings = {
            "pkg1/UsedClass.html#add--",
            "pkg1/ZZTop.html#add--",
            "pkg1/UsedClass.html#add-double-",
            "pkg1/UsedClass.html#add-java.lang.Double-",
            "pkg1/ZZTop.html#add-double-",
            "pkg1/ZZTop.html#add-java.lang.Double-",
            "pkg1/UsedClass.html#add-double-byte-",
            "pkg1/ZZTop.html#add-double-byte-",
            "pkg1/UsedClass.html#add-double-double-",
            "pkg1/UsedClass.html#add-double-java.lang.Double-",
            "pkg1/ZZTop.html#add-double-double-",
            "pkg1/ZZTop.html#add-double-java.lang.Double-",
            "pkg1/UsedClass.html#add-float-",
            "pkg1/ZZTop.html#add-float-",
            "pkg1/UsedClass.html#add-float-int-",
            "pkg1/ZZTop.html#add-float-int-",
            "pkg1/UsedClass.html#add-int-",
            "pkg1/ZZTop.html#add-int-",
            "pkg1/UsedClass.html#add-int-float-",
            "pkg1/ZZTop.html#add-int-float-",
            "pkg1/UsedClass.html#add-java.lang.Integer-",
            "pkg1/ZZTop.html#add-java.lang.Integer-"
        };
        int lastidx = 0;
        for (String x : OrderedExpectedStrings) {
            int idx = indexPage.indexOf(x);
            if (idx < lastidx) {
                throw new Error(x + ", index is out of order, last:" + lastidx + ", got:" + idx);
            }
            System.out.println(x + ": OK");
            lastidx = idx;
        }
    }
}
