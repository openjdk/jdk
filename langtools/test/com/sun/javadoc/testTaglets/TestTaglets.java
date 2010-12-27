/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4654308 4767038
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

    //Test information.
    private static final String BUG_ID = "4654308-4767038";
    private static final String OUTPUT_DIR = BUG_ID;

    //Javadoc arguments.
    private static final String[] ARGS_4654308 = new String[] {
        "-d", "4654308", "-tagletpath", SRC_DIR, "-taglet", "taglets.Foo",
        "-sourcepath", SRC_DIR, SRC_DIR + FS + "C.java"
    };

    private static final String[] ARGS_4767038 = new String[] {
        "-d", "4767038", "-sourcepath", SRC_DIR, SRC_DIR + FS + "Parent.java",
        SRC_DIR + FS + "Child.java"
    };

    //Input for string search tests.
    private static final String[][] TEST_4654308 = new String[][] {
        {"4654308" + FS + "C.html", "<span class=\"strong\">Foo:</span></dt>" +
                 "<dd>my only method is <a href=\"C.html#method()\"><code>here" +
                 "</code></a></dd></dl>"}
    };
    private static final String[][] NEGATED_TEST_4654308 = NO_TEST;

    private static final String[][] TEST_4767038 = new String[][] {
        {"4767038" + FS + "Child.html",
            "This is the first sentence."}
    };
    private static final String[][] NEGATED_TEST_4767038 = NO_TEST;


    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestTaglets tester = new TestTaglets();
        run(tester, ARGS_4654308, TEST_4654308, NEGATED_TEST_4654308);
        tester.printSummary();
        tester = new TestTaglets();
        run(tester, ARGS_4767038, TEST_4767038, NEGATED_TEST_4767038);
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
