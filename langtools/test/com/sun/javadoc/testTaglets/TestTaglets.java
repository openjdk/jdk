/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4654308 4767038 8025633
 * @summary  Use a Taglet and include some inline tags such as {@link}.  The
 *           inline tags should be interpreted properly.
 *           Run Javadoc on some sample source that uses {@inheritDoc}.  Make
 *           sure that only the first sentence shows up in the summary table.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestTaglets
 * @build    taglets.Foo
 * @run main TestTaglets
 */

public class TestTaglets extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS_4654308 = new String[] {
        "-d", "4654308", "-tagletpath", SRC_DIR, "-taglet", "taglets.Foo",
        "-sourcepath", SRC_DIR, SRC_DIR + "/C.java"
    };

    private static final String[] ARGS_4767038 = new String[] {
        "-d", "4767038", "-sourcepath", SRC_DIR, SRC_DIR + "/Parent.java",
        SRC_DIR + "/Child.java"
    };

    //Input for string search tests.
    private static final String[][] TEST_4654308 = new String[][] {
        { "C.html", "<span class=\"simpleTagLabel\">Foo:</span></dt>" +
                 "<dd>my only method is <a href=\"C.html#method--\"><code>here" +
                 "</code></a></dd></dl>"}
    };

    private static final String[][] TEST_4767038 = new String[][] {
        { "Child.html",
            "This is the first sentence."}
    };


    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestTaglets tester = new TestTaglets();
        tester.run(ARGS_4654308, TEST_4654308, NO_TEST);
        tester.printSummary();
        tester = new TestTaglets();
        tester.run(ARGS_4767038, TEST_4767038, NO_TEST);
        tester.printSummary();
    }
}
