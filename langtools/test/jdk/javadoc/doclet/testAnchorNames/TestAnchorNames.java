/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025633 8025524 8081854
 * @summary Test for valid name attribute in HTML anchors.
 * @author Bhavesh Patel
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestAnchorNames
 */

public class TestAnchorNames extends JavadocTester {

    private static final String[] ARGS = new String[] {

    };

    public static void main(String[] args) throws Exception {
        TestAnchorNames tester = new TestAnchorNames();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-source", "8", //so that '_' can be used as an identifier
                "-use",
                "pkg1");
        checkExit(Exit.OK);

        // Test some section markers and links to these markers
        checkOutput("pkg1/RegClass.html", true,
                "<a name=\"skip.navbar.top\">",
                "<a href=\"#skip.navbar.top\" title=\"Skip navigation links\">",
                "<a name=\"nested.class.summary\">",
                "<a href=\"#nested.class.summary\">",
                "<a name=\"method.summary\">",
                "<a href=\"#method.summary\">",
                "<a name=\"field.detail\">",
                "<a href=\"#field.detail\">",
                "<a name=\"constructor.detail\">",
                "<a href=\"#constructor.detail\">");

        // Test some members and link to these members
        checkOutput("pkg1/RegClass.html", true,
                //The marker for this appears in the serialized-form.html which we will
                //test below
                "<a href=\"../serialized-form.html#pkg1.RegClass\">");

        // Test some fields
        checkOutput("pkg1/RegClass.html", true,
                "<a name=\"Z:Z_\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z_\">",
                "<a name=\"Z:Z_:D\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z_:D\">",
                "<a name=\"Z:Z:D_\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z:D_\">",
                "<a name=\"Z:Z:Dfield\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z:Dfield\">",
                "<a name=\"fieldInCla:D:D\">",
                "<a href=\"../pkg1/RegClass.html#fieldInCla:D:D\">",
                "<a name=\"S_:D:D:D:D:DINT\">",
                "<a href=\"../pkg1/RegClass.html#S_:D:D:D:D:DINT\">",
                "<a name=\"method:D:D\">",
                "<a href=\"../pkg1/RegClass.html#method:D:D\">");

        checkOutput("pkg1/DeprMemClass.html", true,
                "<a name=\"Z:Z_field_In_Class\">",
                "<a href=\"../pkg1/DeprMemClass.html#Z:Z_field_In_Class\">");

        // Test constructor
        checkOutput("pkg1/RegClass.html", true,
                "<a name=\"RegClass-java.lang.String-int-\">",
                "<a href=\"../pkg1/RegClass.html#RegClass-java.lang.String-int-\">");

        // Test some methods
        checkOutput("pkg1/RegClass.html", true,
                "<a name=\"Z:Z_methodInClass-java.lang.String-\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z_methodInClass-java.lang.String-\">",
                "<a name=\"method--\">",
                "<a href=\"../pkg1/RegClass.html#method--\">",
                "<a name=\"foo-java.util.Map-\">",
                "<a href=\"../pkg1/RegClass.html#foo-java.util.Map-\">",
                "<a name=\"methodInCla:Ds-java.lang.String:A-\">",
                "<a href=\"../pkg1/RegClass.html#methodInCla:Ds-java.lang.String:A-\">",
                "<a name=\"Z:Z_methodInClas:D-java.lang.String-int-\">",
                "<a href=\"../pkg1/RegClass.html#Z:Z_methodInClas:D-java.lang.String-int-\">",
                "<a name=\"methodD-pkg1.RegClass.:DA-\">",
                "<a href=\"../pkg1/RegClass.html#methodD-pkg1.RegClass.:DA-\">",
                "<a name=\"methodD-pkg1.RegClass.D:A-\">",
                "<a href=\"../pkg1/RegClass.html#methodD-pkg1.RegClass.D:A-\">");

        checkOutput("pkg1/DeprMemClass.html", true,
                "<a name=\"Z:Z:Dmethod_In_Class--\">",
                "<a href=\"../pkg1/DeprMemClass.html#Z:Z:Dmethod_In_Class--\">");

        // Test enum
        checkOutput("pkg1/RegClass.Te$t_Enum.html", true,
                "<a name=\"Z:Z:DFLD2\">",
                "<a href=\"../pkg1/RegClass.Te$t_Enum.html#Z:Z:DFLD2\">");

        // Test nested class
        checkOutput("pkg1/RegClass._NestedClas$.html", true,
                "<a name=\"Z:Z_NestedClas:D--\">",
                "<a href=\"../pkg1/RegClass._NestedClas$.html#Z:Z_NestedClas:D--\">");

        // Test class use page
        checkOutput("pkg1/class-use/DeprMemClass.html", true,
                "<a href=\"../../pkg1/RegClass.html#d____mc\">");

        // Test deprecated list page
        checkOutput("deprecated-list.html", true,
                "<a href=\"pkg1/DeprMemClass.html#Z:Z_field_In_Class\">",
                "<a href=\"pkg1/DeprMemClass.html#Z:Z:Dmethod_In_Class--\">");

        // Test constant values page
        checkOutput("constant-values.html", true,
                "<a href=\"pkg1/RegClass.html#S_:D:D:D:D:DINT\">");

        // Test serialized form page
        checkOutput("serialized-form.html", true,
                //This is the marker for the link that appears in the pkg1.RegClass.html page
                "<a name=\"pkg1.RegClass\">");

        // Test member name index page
        checkOutput("index-all.html", true,
                "<a name=\"I:Z:Z:D\">",
                "<a href=\"#I:Z:Z:D\">$",
                "<a href=\"#I:Z:Z_\">_");

        // The marker name conversion should only affect HTML anchors. It should not
        // affect the lables.
        checkOutput("pkg1/RegClass.html", false,
                " Z:Z_",
                " Z:Z:Dfield",
                " Z:Z_field_In_Class",
                " S_:D:D:D:D:DINT");
    }
}
