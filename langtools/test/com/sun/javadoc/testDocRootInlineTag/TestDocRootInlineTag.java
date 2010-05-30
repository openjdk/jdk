/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4369014 4851991
 * @summary Determine if the docRoot inline tag works properly.
 * If docRoot performs as documented, the test passes.
 * Make sure that the docRoot tag works with the -bottom option.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestDocRootInlineTag
 * @run main TestDocRootInlineTag
 */

public class TestDocRootInlineTag extends JavadocTester {

    private static final String BUG_ID = "4369014-4851991";
    private static final String[][] TEST = {
        {BUG_ID + FS + "TestDocRootTag.html",
            "<A HREF=\"http://www.java.sun.com/j2se/1.4/docs/api/java/io/File.html?is-external=true\" " +
            "title=\"class or interface in java.io\"><CODE>File</CODE></A>"},
        {BUG_ID + FS + "TestDocRootTag.html",
            "<a href=\"./glossary.html\">glossary</a>"},
        {BUG_ID + FS + "TestDocRootTag.html",
            "<A HREF=\"http://www.java.sun.com/j2se/1.4/docs/api/java/io/File.html?is-external=true\" " +
            "title=\"class or interface in java.io\"><CODE>Second File Link</CODE></A>"},
        {BUG_ID + FS + "TestDocRootTag.html", "The value of @docRoot is \"./\""},
        {BUG_ID + FS + "index-all.html", "My package page is " +
            "<a href=\"./pkg/package-summary.html\">here</a>"}
    };
    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS =
        new String[] {
            "-bottom", "The value of @docRoot is \"{@docRoot}\"",
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-linkoffline", "http://www.java.sun.com/j2se/1.4/docs/api",
            SRC_DIR, SRC_DIR + FS + "TestDocRootTag.java", "pkg"
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestDocRootInlineTag tester = new TestDocRootInlineTag();
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
