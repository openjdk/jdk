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
 * @bug 4634891 8026567
 * @summary Determine if overriden methods are properly documented when
 * -protected (default) visibility flag is used.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestOverridenPrivateMethods
 * @run main TestOverridenPrivateMethods
 */

public class TestOverridenPrivateMethods extends JavadocTester {

    private static final String BUG_ID = "4634891";

    private static final String[][] TEST = {
        //The public method should be overriden
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>" + NL +
                 "<dd><code><a href=\"../pkg1/BaseClass.html#publicMethod"},

        //The public method in different package should be overriden
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>" + NL +
                 "<dd><code><a href=\"../pkg1/BaseClass.html#publicMethod"}
    };

    private static final String[][] NEGATED_TEST = {

        //The package private method should be overriden since the base and sub class are in the same
        //package.  However, the link should not show up because the package private methods are not documented.
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>" + NL +
                 "<dd><code><a href=\"../pkg1/BaseClass.html#packagePrivateMethod"},

        //The private method in should not be overriden
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>" + NL +
                 "<dd><code><a href=\"../pkg1/BaseClass.html#privateMethod"},

        //The private method in different package should not be overriden
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>" + NL +
                 "<dd><code><a href=\"../pkg1/BaseClass.html#privateMethod"},

        //The package private method should not be overriden since the base and sub class are in
        //different packages.
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "Overrides:</span></dt><dd><code><a href=\"../pkg1/BaseClass.html#packagePrivateMethod"}
    };

    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1", "pkg2"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestOverridenPrivateMethods tester = new TestOverridenPrivateMethods();
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
