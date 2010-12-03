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
 * @bug      4933335
 * @summary  Make sure that all inherited methods from multiple extended
 *           interfaces are documented
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestMultiInheritence
 * @run main TestMultiInheritence
 */

public class TestMultiInheritence extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4933335";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg3"
    };

    //Method foo() is inherited from BOTH I2 and I3
    private static final String[][] TEST = {
       {BUG_ID + FS + "pkg3" + FS + "I1.html",
        "Methods inherited from interface&nbsp;pkg3." +
                "<a href=\"../pkg3/I2.html\" title=\"interface in pkg3\">" +
                "I2</a>"},
        {BUG_ID + FS + "pkg3" + FS +"I1.html",
        "Methods inherited from interface&nbsp;pkg3." +
                 "<a href=\"../pkg3/I3.html\" title=\"interface in pkg3\">" +
                 "I3</a>"},
        {BUG_ID + FS + "pkg3" + FS + "I0.html",
        "Methods inherited from interface&nbsp;pkg3." +
                 "<a href=\"../pkg3/I2.html\" title=\"interface in pkg3\">" +
                 "I2</a>"},
        {BUG_ID + FS + "pkg3" + FS +"I0.html",
        "Methods inherited from interface&nbsp;pkg3." +
                 "<a href=\"../pkg3/I3.html\" title=\"interface in pkg3\">" +
                 "I3</a>"},
    };

    //Method foo() is NOT inherited from I4 because it is overriden by
    //I3.
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg3" + FS + "I1.html",
        "Methods inherited from interface&nbsp;pkg3." +
                 "<a href=\"../pkg3/I4.html\" title=\"interface in pkg3\">" +
                 "I4</a>"},
        {BUG_ID + FS + "pkg3" + FS + "I0.html",
        "Methods inherited from interface&nbsp;pkg3." +
                 "<a href=\"../pkg3/I4.html\" title=\"interface in pkg3\">" +
                 "I4</a>"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestMultiInheritence tester = new TestMultiInheritence();
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
