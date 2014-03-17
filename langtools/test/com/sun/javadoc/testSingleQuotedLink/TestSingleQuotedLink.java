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
 * @bug 6457406
 * @summary Verify that a link in single quotes copied to the class-use page as is.
 * @author Yuri Nesterenko
 * @library ../lib/
 * @build JavadocTester TestSingleQuotedLink
 * @run main TestSingleQuotedLink
 */
public class TestSingleQuotedLink extends JavadocTester {

    private static final String BUG_ID = "6457406";
    // We are testing the redirection algorithm with a known scenario when a writer is not forced to ignore it: "-use".
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<a href=\'http://download.oracle.com/javase/8/docs/technotes/guides/indexC2.html\'>"
        }
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "pkg1/\'http://download.oracle.com/javase/8/docs/technotes/guides/indexC2.html\'>"
        }
    };
    private static final String[] ARGS =
            new String[]{
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-use", "pkg1"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSingleQuotedLink tester = new TestSingleQuotedLink();
        run(tester, ARGS, TEST, NEGATED_TEST);
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
