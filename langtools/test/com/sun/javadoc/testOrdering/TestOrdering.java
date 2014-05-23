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
 * @run main TestOrdering
 */

public class TestOrdering extends JavadocTester {

    public static void main(String[] args) throws Exception {
        TestOrdering tester = new TestOrdering();
        tester.runTests();
    }

    @Test
    void testUnnamedPackages() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-use",
                testSrc("C.java"), testSrc("UsedInC.java"));
        checkExit(Exit.OK);
        checkExecutableMemberOrdering("class-use/UsedInC.html");
    }

    @Test
    void testNamedPackages() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "-use",
                "pkg1");
        checkExit(Exit.OK);
        checkClassUseOrdering("pkg1/class-use/UsedClass.html");
        checkIndexPathOrdering("index-all.html");
    }

    void checkExecutableMemberOrdering(String usePage) {
        String contents = readFile(usePage);
        // check constructors
        checking("constructors");
        int idx1 = contents.indexOf("C.html#C-UsedInC");
        int idx2 = contents.indexOf("C.html#C-UsedInC-int");
        int idx3 = contents.indexOf("C.html#C-UsedInC-java.lang.String");
        if (idx1 == -1 || idx2 == -1 || idx3 == -1) {
            failed("ctor strings not found");
        } else if (idx1 > idx2 || idx2 > idx3 || idx1 > idx3) {
            failed("ctor strings are out of order");
        } else
            passed("ctor strings are in order");

        // check methods
        checking("methods");
        idx1 = contents.indexOf("C.html#ymethod-int");
        idx2 = contents.indexOf("C.html#ymethod-java.lang.String");
        if (idx1 == -1 || idx2 == -1) {
            failed("#ymethod strings not found");
        } else if (idx1 > idx2) {
            failed("#ymethod strings are out of order");
        } else
            passed("Executable Member Ordering: OK");
    }

    void checkClassUseOrdering(String usePage) {
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#zfield");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#fieldInC#ITERATION#");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#zmethod-pkg1.UsedClass");
        checkClassUseOrdering(usePage, "pkg1/C#ITERATION#.html#methodInC#ITERATION#");
    }

    void checkClassUseOrdering(String usePage, String searchString) {
        String contents = readFile(usePage);
        int lastidx = 0;
        System.out.println("testing for " + searchString);
        for (int i = 1; i < 5; i++) {
            String s = searchString.replaceAll("#ITERATION#", Integer.toString(i));
            checking(s);
            int idx = contents.indexOf(s);
            if (idx < lastidx) {
                failed(s + ", member ordering error, last:" + lastidx + ", got:" + idx);
            } else {
                passed("\tlast: " + lastidx + " got:" + idx);
            }
            lastidx = idx;
        }
    }

    void checkIndexPathOrdering(String indexPage) {
        checkOrder(indexPage,
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
            "pkg1/ZZTop.html#add-java.lang.Integer-");
    }
}
