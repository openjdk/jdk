/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test for nested inline tags. *
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build testtaglets.UnderlineTaglet
 * @build testtaglets.BoldTaglet
 * @build testtaglets.GreenTaglet
 * @build TestNestedInlineTag
 * @run main TestNestedInlineTag
 */

/**
 * This should be green, underlined and bold (Class): {@underline {@bold {@green My test}}} .
 */
public class TestNestedInlineTag extends JavadocTester {

    /**
     * This should be green, underlined and bold (Field): {@underline {@bold {@green My test}}} .
     */
    public int field;

    /**
     * This should be green, underlined and bold (Constructor): {@underline {@bold {@green My test}}} .
     */
    public TestNestedInlineTag(){}

    /**
     * This should be green, underlined and bold (Method): {@underline {@bold {@green My test}}} .
     */
    public void method(){}

    private static final String BUG_ID = "no-bug-id";
    private static final String[][] TEST = {
        //Test nested inline tag in class description.
        {BUG_ID + FS + "TestNestedInlineTag.html",
         "This should be green, underlined and bold (Class): <u><b><font color=\"green\">My test</font></b></u>"
        },

        //Test nested inline tag in field description.
        {BUG_ID + FS + "TestNestedInlineTag.html",
         "This should be green, underlined and bold (Field): <u><b><font color=\"green\">My test</font></b></u>"
        },

        //Test nested inline tag in constructor description.
        {BUG_ID + FS + "TestNestedInlineTag.html",
         "This should be green, underlined and bold (Constructor): <u><b><font color=\"green\">My test</font></b></u>"
        },

        //Test nested inline tag in method description.
        {BUG_ID + FS + "TestNestedInlineTag.html",
         "This should be green, underlined and bold (Method): <u><b><font color=\"green\">My test</font></b></u>"
        }
    };

    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-taglet", "testtaglets.UnderlineTaglet",
            "-taglet", "testtaglets.BoldTaglet",
            "-taglet", "testtaglets.GreenTaglet",
            SRC_DIR + FS + "TestNestedInlineTag.java"
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestNestedInlineTag tester = new TestNestedInlineTag();
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
