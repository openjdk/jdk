/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025633
 * @summary Test for valid name attribute in HTML anchors.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestAnchorNames
 * @run main TestAnchorNames
 */

public class TestAnchorNames extends JavadocTester {

    private static final String BUG_ID = "8025633";

    //Input for string search tests.
    private static final String[][] TEST = {

        //Test some section markers and links to these markers

        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"skip.navbar.top\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"#skip.navbar.top\" title=\"Skip navigation links\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"nested.class.summary\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"#nested.class.summary\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"method.summary\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"#method.summary\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"field.detail\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"#field.detail\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"constructor.detail\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"#constructor.detail\">"
        },

        //Test some members and link to these members

        //The marker for this appears in the serialized-form.html which we will
        //test below
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../serialized-form.html#pkg1.RegClass\">"
        },
        //Test some fields
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z_\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z_\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z_:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z_:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z:D_\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z:D_\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z:Dfield\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z:Dfield\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"fieldInCla:D:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#fieldInCla:D:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"S_:D:D:D:D:DINT\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#S_:D:D:D:D:DINT\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"method:D:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#method:D:D\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "DeprMemClass.html",
            "<a name=\"Z:Z_field_In_Class\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "DeprMemClass.html",
            "<a href=\"../pkg1/DeprMemClass.html#Z:Z_field_In_Class\">"
        },
        //Test constructor
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"RegClass-java.lang.String-int-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#RegClass-java.lang.String-int-\">"
        },
        //Test some methods
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z_methodInClass-java.lang.String-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z_methodInClass-java.lang.String-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"method--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#method--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"foo-java.util.Map-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#foo-java.util.Map-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"methodInCla:Ds-java.lang.String:A-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#methodInCla:Ds-java.lang.String:A-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"Z:Z_methodInClas:D-java.lang.String-int-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#Z:Z_methodInClas:D-java.lang.String-int-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"methodD-pkg1.RegClass.:DA-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#methodD-pkg1.RegClass.:DA-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a name=\"methodD-pkg1.RegClass.D:A-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            "<a href=\"../pkg1/RegClass.html#methodD-pkg1.RegClass.D:A-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "DeprMemClass.html",
            "<a name=\"Z:Z:Dmethod_In_Class--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "DeprMemClass.html",
            "<a href=\"../pkg1/DeprMemClass.html#Z:Z:Dmethod_In_Class--\">"
        },

        //Test enum

        {BUG_ID + FS + "pkg1" + FS + "RegClass.Te$t_Enum.html",
            "<a name=\"Z:Z:DFLD2\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.Te$t_Enum.html",
            "<a href=\"../pkg1/RegClass.Te$t_Enum.html#Z:Z:DFLD2\">"
        },

        //Test nested class

        {BUG_ID + FS + "pkg1" + FS + "RegClass._NestedClas$.html",
            "<a name=\"RegClass._NestedClas:D--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass._NestedClas$.html",
            "<a href=\"../pkg1/RegClass._NestedClas$.html#RegClass._NestedClas:D--\">"
        },

        //Test class use page

        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "DeprMemClass.html",
            "<a href=\"../../pkg1/RegClass.html#d____mc\">"
        },

        //Test deprecated list page

        {BUG_ID + FS + "deprecated-list.html",
            "<a href=\"pkg1/DeprMemClass.html#Z:Z_field_In_Class\">"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<a href=\"pkg1/DeprMemClass.html#Z:Z:Dmethod_In_Class--\">"
        },

        //Test constant values page

        {BUG_ID + FS + "constant-values.html",
            "<a href=\"pkg1/RegClass.html#S_:D:D:D:D:DINT\">"
        },

        //Test serialized form page

        //This is the marker for the link that appears in the pkg1.RegClass.html page
        {BUG_ID + FS + "serialized-form.html",
            "<a name=\"pkg1.RegClass\">"
        },

        //Test member name index page

        {BUG_ID + FS + "index-all.html",
            "<a name=\"I:Z:Z:D\">"
        },
        {BUG_ID + FS + "index-all.html",
            "<a href=\"#I:Z:Z:D\">$"
        },
        {BUG_ID + FS + "index-all.html",
            "<a href=\"#I:Z:Z_\">_"
        }
    };

    private static final String[][] NEGATED_TEST = {
        //The marker name conversion should only affect HTML anchors. It should not
        //affect the lables.
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            " Z:Z_"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            " Z:Z:Dfield"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            " Z:Z_field_In_Class"
        },
        {BUG_ID + FS + "pkg1" + FS + "RegClass.html",
            " S_:D:D:D:D:DINT"
        },
    };

    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-use", "pkg1"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) throws Exception {
        TestAnchorNames tester = new TestAnchorNames();
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
