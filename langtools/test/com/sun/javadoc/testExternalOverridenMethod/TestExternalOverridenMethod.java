/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4857717
 * @summary Test to make sure that externally overriden and implemented methods
 * are documented properly.  The method should still include "implements" or
 * "overrides" documentation even though the method is external.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestExternalOverridenMethod
 * @run main TestExternalOverridenMethod
 */

public class TestExternalOverridenMethod extends JavadocTester {

    private static final String BUG_ID = "4857717";
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "XReader.html",
            "<STRONG>Overrides:</STRONG></DT><DD><CODE><A HREF=\"" +
            "http://java.sun.com/j2se/1.4.1/docs/api/java/io/FilterReader.html?is-external=true#read()\"" +
                " title=\"class or interface in java.io\">read</A></CODE> in class " +
                "<CODE><A HREF=\"http://java.sun.com/j2se/1.4.1/docs/api/java/io/FilterReader.html?is-external=true\"" +
                " title=\"class or interface in java.io\">FilterReader</A>"},
        {BUG_ID + FS + "pkg" + FS + "XReader.html",
            "<STRONG>Specified by:</STRONG></DT><DD><CODE><A HREF=\"" +
            "http://java.sun.com/j2se/1.4.1/docs/api/java/io/DataInput.html?is-external=true#readInt()\"" +
            " title=\"class or interface in java.io\">readInt</A></CODE> in interface " +
            "<CODE><A HREF=\"http://java.sun.com/j2se/1.4.1/docs/api/java/io/DataInput.html?is-external=true\"" +
            " title=\"class or interface in java.io\">DataInput</A>"}};



    private static final String[][] NEGATED_TEST = NO_TEST;

    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-linkoffline", "http://java.sun.com/j2se/1.4.1/docs/api", SRC_DIR,
            "pkg"
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestExternalOverridenMethod tester = new TestExternalOverridenMethod();
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
