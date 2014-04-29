/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6553182 8025416 8029504
 * @summary This test verifies the -Xdocrootparent option.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestDocRootLink
 * @run main TestDocRootLink
 */
public class TestDocRootLink extends JavadocTester {

    private static final String[][] TEST1 = {
        { "pkg1/C1.html",
            "Refer <a href=\"../../technotes/guides/index.html\">Here</a>"
        },
        { "pkg1/C1.html",
            "This <a href=\"../pkg2/C2.html\">Here</a> should not be replaced\n" +
            " with an absolute link."
        },
        { "pkg1/C1.html",
            "Testing <a href=\"../technotes/guides/index.html\">Link 1</a> and\n" +
            " <a href=\"../pkg2/C2.html\">Link 2</a>."
        },
        { "pkg1/package-summary.html",
            "<a href=\"../../technotes/guides/index.html\">\n" +
            "            Test document 1</a>"
        },
        { "pkg1/package-summary.html",
            "<a href=\"../pkg2/C2.html\">\n" +
            "            Another Test document 1</a>"
        },
        { "pkg1/package-summary.html",
            "<a href=\"../technotes/guides/index.html\">\n" +
            "            Another Test document 2.</a>"
        }
    };
    private static final String[][] NEGATED_TEST1 = {
        { "pkg1/C1.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/technotes/guides/index.html\">"
        },
        { "pkg1/C1.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/pkg2/C2.html\">"
        },
        { "pkg1/package-summary.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/technotes/guides/index.html\">"
        },
        { "pkg1/package-summary.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/pkg2/C2.html\">"
        }
    };
    private static final String[][] TEST2 = {
        { "pkg2/C2.html",
            "Refer <a href=\"http://download.oracle.com/javase/7/docs/technotes/guides/index.html\">Here</a>"
        },
        { "pkg2/C2.html",
            "This <a href=\"../pkg1/C1.html\">Here</a> should not be replaced\n" +
            " with an absolute link."
        },
        { "pkg2/C2.html",
            "Testing <a href=\"../technotes/guides/index.html\">Link 1</a> and\n" +
            " <a href=\"../pkg1/C1.html\">Link 2</a>."
        },
        { "pkg2/package-summary.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/technotes/guides/index.html\">\n" +
            "            Test document 1</a>"
        },
        { "pkg2/package-summary.html",
            "<a href=\"../pkg1/C1.html\">\n" +
            "            Another Test document 1</a>"
        },
        { "pkg2/package-summary.html",
            "<a href=\"../technotes/guides/index.html\">\n" +
            "            Another Test document 2.</a>"
        }
    };
    private static final String[][] NEGATED_TEST2 = {
        { "pkg2/C2.html",
            "<a href=\"../../technotes/guides/index.html\">"
        },
        { "pkg2/C2.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/pkg1/C1.html\">"
        },
        { "pkg2/package-summary.html",
            "<a href=\"../../technotes/guides/index.html\">"
        },
        { "pkg2/package-summary.html",
            "<a href=\"http://download.oracle.com/javase/7/docs/pkg1/C1.html\">"
        }
    };
    private static final String[] ARGS1 =
            new String[]{
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "pkg1", "pkg2"
    };
    private static final String[] ARGS2 =
            new String[]{
        "-d", OUTPUT_DIR + "-1", "-Xdocrootparent",
        "http://download.oracle.com/javase/7/docs", "-sourcepath",
        SRC_DIR, "pkg1", "pkg2"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestDocRootLink tester = new TestDocRootLink();
        tester.run(ARGS1, TEST1, NEGATED_TEST1);
        tester.run(ARGS2, TEST2, NEGATED_TEST2);
        tester.printSummary();
    }
}
