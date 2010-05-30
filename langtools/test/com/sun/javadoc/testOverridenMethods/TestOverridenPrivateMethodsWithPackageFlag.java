/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4634891
 * @summary Determine if overriden methods are properly documented when
 * -protected (default) visibility flag is used.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestOverridenPrivateMethodsWithPackageFlag
 * @run main TestOverridenPrivateMethodsWithPackageFlag
 */

public class TestOverridenPrivateMethodsWithPackageFlag extends JavadocTester {

    private static final String BUG_ID = "4634891";

    private static final String[][] TEST = {
        //The public method should be overriden
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#publicMethod"},

        //The public method in different package should be overriden
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#publicMethod"},

        //The package private method should be overriden since the base and sub class are in the same
        //package.
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#packagePrivateMethod"}
    };

    private static final String[][] NEGATED_TEST = {

        //The private method in should not be overriden
        {BUG_ID + FS + "pkg1" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#privateMethod"},

        //The private method in different package should not be overriden
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#privateMethod"},

        //The package private method should not be overriden since the base and sub class are in
        //different packages.
        {BUG_ID + FS + "pkg2" + FS + "SubClass.html",
         "Overrides:</STRONG></DT><DD><CODE><A HREF=\"../pkg1/BaseClass.html#packagePrivateMethod"},
    };

    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "-package", "pkg1", "pkg2"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestOverridenPrivateMethodsWithPackageFlag tester = new TestOverridenPrivateMethodsWithPackageFlag();
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
