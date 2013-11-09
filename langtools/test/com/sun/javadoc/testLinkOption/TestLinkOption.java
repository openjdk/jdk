/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4720957 5020118 8026567
 * @summary Test to make sure that -link and -linkoffline link to
 * right files.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester TestLinkOption
 * @run main TestLinkOption
 */

public class TestLinkOption extends JavadocTester {

    private static final String BUG_ID = "4720957-5020118";

    //Generate the documentation using -linkoffline and a URL as the first parameter.
    private static final String[] ARGS1 = new String[] {
        "-d", BUG_ID + "-1", "-sourcepath", SRC_DIR,
        "-linkoffline", "http://java.sun.com/j2se/1.4/docs/api/",
        SRC_DIR, "-package", "pkg", "java.lang"
    };

    private static final String[][] TEST1 = {
        {BUG_ID + "-1" + FS + "pkg" + FS + "C.html",
            "<a href=\"http://java.sun.com/j2se/1.4/docs/api/java/lang/String.html?is-external=true\" " +
            "title=\"class or interface in java.lang\"><code>Link to String Class</code></a>"
        },
        //Make sure the parameters are indented properly when the -link option is used.
        {BUG_ID + "-1" + FS + "pkg" + FS + "C.html",
                                "(int&nbsp;p1," + NL +
                                "      int&nbsp;p2," + NL +
                                "      int&nbsp;p3)"
        },
        {BUG_ID + "-1" + FS + "pkg" + FS + "C.html",
                                "(int&nbsp;p1," + NL +
                                "      int&nbsp;p2," + NL +
                                "      <a href=\"http://java.sun.com/j2se/1.4/docs/api/java/lang/Object.html?is-external=true\" title=\"class or interface in java.lang\">" +
                                "Object</a>&nbsp;p3)"
        },
        {BUG_ID + "-1" + FS + "java" + FS + "lang" + FS + "StringBuilderChild.html",
                "<pre>public abstract class <span class=\"typeNameLabel\">StringBuilderChild</span>" + NL +
                "extends <a href=\"http://java.sun.com/j2se/1.4/docs/api/java/lang/Object.html?is-external=true\" " +
                "title=\"class or interface in java.lang\">Object</a></pre>"
        },

    };
    private static final String[][] NEGATED_TEST1 = NO_TEST;

    //Generate the documentation using -linkoffline and a relative path as the first parameter.
    //We will try linking to the docs generated in test 1 with a relative path.
    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID + "-2", "-sourcepath", SRC_DIR,
        "-linkoffline", "../" + BUG_ID + "-1", BUG_ID + "-1", "-package", "pkg2"
    };

    private static final String[][] TEST2 = {
        {BUG_ID + "-2" + FS + "pkg2" + FS + "C2.html",
            "This is a link to <a href=\"../../" + BUG_ID + "-1/pkg/C.html?is-external=true\" " +
            "title=\"class or interface in pkg\"><code>Class C</code></a>."
        }
    };
    private static final String[][] NEGATED_TEST2 = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestLinkOption tester = new TestLinkOption();
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        run(tester, ARGS2, TEST2, NEGATED_TEST2);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
