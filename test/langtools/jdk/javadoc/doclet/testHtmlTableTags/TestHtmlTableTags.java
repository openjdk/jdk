/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6786688 8008164 8162363 8169819 8183037 8182765 8184205 8242649
 * @summary  HTML tables should have table summary, caption and table headers.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestHtmlTableTags
 */

import javadoc.tester.JavadocTester;

public class TestHtmlTableTags extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {

    };


    public static void main(String... args) throws Exception {
        TestHtmlTableTags tester = new TestHtmlTableTags();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-use",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkHtmlTableTag();
        checkHtmlTableCaptions();
        checkHtmlTableHeaders();
    }

    /*
     * Tests for validating table tag for HTML tables
     */
    void checkHtmlTableTag() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/package-summary.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                """
                    <div class="summary-table three-column-summary">""",
                """
                    <div class="summary-table three-column-summary" aria-labelledby="method-summary-table-tab0">""");

        checkOutput("pkg2/C2.html", true,
                """
                    <div class="summary-table three-column-summary">""",
                """
                    <div class="summary-table three-column-summary" aria-labelledby="method-summary-table-tab0">""");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/C3.html", true,
                """
                    <div class="summary-table three-column-summary">""");

        checkOutput("pkg2/C4.html", true,
                """
                    <div class="summary-table three-column-summary">""");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg1/class-use/C1.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/class-use/C2.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="summary-table two-column-summary">""");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        checkOutput("pkg2/package-use.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                """
                    <div class="summary-table two-column-summary">""",
                """
                    <div class="summary-table two-column-summary">""");

        // Constant values
        checkOutput("constant-values.html", true,
                """
                    <div class="summary-table three-column-summary">""");

        // Overview Summary
        checkOutput("index.html", true,
                """
                    <div class="summary-table two-column-summary">""");
    }

    /*
     * Tests for validating summary for HTML tables
     */
    void checkHtmlTableSummaries() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                """
                    <div class="type-summary">
                    <table summary="Class Summary table, listing classes, and an explanation">""",
                """
                    <div class="type-summary">
                    <table summary="Interface Summary table, listing interfaces, and an explanation">""");

        checkOutput("pkg2/package-summary.html", true,
                """
                    <div class="type-summary">
                    <table summary="Enum Summary table, listing enums, and an explanation">""",
                """
                    <div class="type-summary">
                    <table summary="Annotation Types Summary table, listing annotation types, and an explanation">""");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                """
                    <div class="member-summary">
                    <table summary="Field Summary table, listing fields, and an explanation">""",
                "<div class=\"member-summary\">\n",
                """
                    <table summary="Method Summary table, listing methods, and an explanation" aria-labelledby="t0">""");

        checkOutput("pkg2/C2.html", true,
                """
                    <div class="member-summary">
                    <table summary="Nested Class Summary table, listing nested classes, and an explanation">""",
                """
                    <div class="member-summary">
                    <table summary="Constructor Summary table, listing constructors, and an explanation">""");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                """
                    <div class="member-summary">
                    <table summary="Enum Constant Summary table, listing enum constants, and an explanation">""");

        checkOutput("pkg2/C3.html", true,
                """
                    <div class="member-summary">
                    <table summary="Required Element Summary table, listing required elements, and an explanation">""");

        checkOutput("pkg2/C4.html", true,
                """
                    <div class="member-summary">
                    <table summary="Optional Element Summary table, listing optional elements, and an explanation">""");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing packages, and an explanation">""");

        checkOutput("pkg1/class-use/C1.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing fields, and an explanation">""",
                """
                    <div class="use-summary">
                    <table summary="Use table, listing methods, and an explanation">""");

        checkOutput("pkg2/class-use/C2.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing fields, and an explanation">""",
                """
                    <div class="use-summary">
                    <table summary="Use table, listing methods, and an explanation">""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing packages, and an explanation">""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing methods, and an explanation">""");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing packages, and an explanation">""",
                """
                    <div class="use-summary">
                    <table summary="Use table, listing classes, and an explanation">""");

        checkOutput("pkg2/package-use.html", true,
                """
                    <div class="use-summary">
                    <table summary="Use table, listing packages, and an explanation">""",
                """
                    <div class="use-summary">
                    <table summary="Use table, listing classes, and an explanation">""");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                """
                    <div class="deprecated-summary" id="field">
                    <table summary="Fields table, listing fields, and an explanation">""",
                """
                    <div class="deprecated-summary" id="method">
                    <table summary="Methods table, listing methods, and an explanation">""");

        // Constant values
        checkOutput("constant-values.html", true,
                """
                    <div class="constants-summary">
                    <table summary="Constant Field Values table, listing constant fields, and values">""");

        // Overview Summary
        checkOutput("index.html", true,
                """
                    <div class="overview-summary" id="all-packages">
                    <table summary="Package Summary table, listing packages, and an explanation">""");
    }

    /*
     * Tests for validating caption for HTML tables
     */
    void checkHtmlTableCaptions() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<div class=\"caption\"><span>Class Summary</span></div>",
                "<div class=\"caption\"><span>Interface Summary</span></div>");

        checkOutput("pkg2/package-summary.html", true,
                "<div class=\"caption\"><span>Enum Summary</span></div>",
                "<div class=\"caption\"><span>Annotation Types Summary</span></div>");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<div class=\"caption\"><span>Fields</span></div>",
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal">\
                    <button id="method-summary-table-tab0" role="tab" aria-selected="true" aria-cont\
                    rols="method-summary-table.tabpanel" tabindex="0" onkeydown="switchTab(event)" o\
                    nclick="show('method-summary-table', 'method-summary-table', 3)" class="active-t\
                    able-tab">All Methods</button>\
                    <button id="method-summary-table-tab2" role="tab" aria-selected="false" aria-con\
                    trols="method-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)"\
                     onclick="show('method-summary-table', 'method-summary-table-tab2', 3)" class="t\
                    able-tab">Instance Methods</button>\
                    <button id="method-summary-table-tab4" role="tab" aria-selected="false" aria-con\
                    trols="method-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)"\
                     onclick="show('method-summary-table', 'method-summary-table-tab4', 3)" class="t\
                    able-tab">Concrete Methods</button>\
                    <button id="method-summary-table-tab6" role="tab" aria-selected="false" aria-con\
                    trols="method-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)"\
                     onclick="show('method-summary-table', 'method-summary-table-tab6', 3)" class="t\
                    able-tab">Deprecated Methods</button>\
                    </div>
                    """);

        checkOutput("pkg2/C2.html", true,
                "<div class=\"caption\"><span>Nested Classes</span></div>",
                "<div class=\"caption\"><span>Constructors</span></div>");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<div class=\"caption\"><span>Enum Constants</span></div>");

        checkOutput("pkg2/C3.html", true,
                "<div class=\"caption\"><span>Required Elements</span></div>");

        checkOutput("pkg2/C4.html", true,
                "<div class=\"caption\"><span>Optional Elements</span></div>");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                """
                    <div class="caption"><span>Packages that use <a href="../I1.html" title="interface in pkg1">I1</a></span></div>""");

        checkOutput("pkg1/class-use/C1.html", true,
                """
                    <div class="caption"><span>Fields in <a href="../../pkg2/package-summary.html">pkg2</a> decl\
                    ared as <a href="../C1.html" title="class in pkg1">C1</a></span></div>""",
                """
                    <div class="caption"><span>Methods in <a href="../../pkg2/package-summary.html">pkg2</a> tha\
                    t return <a href="../C1.html" title="class in pkg1">C1</a></span></div>""");

        checkOutput("pkg2/class-use/C2.html", true,
                """
                    <div class="caption"><span>Fields in <a href="../../pkg1/package-summary.html">pkg1</a> decl\
                    ared as <a href="../C2.html" title="class in pkg2">C2</a></span></div>""",
                """
                    <div class="caption"><span>Methods in <a href="../../pkg1/package-summary.html">pkg1</a> tha\
                    t return <a href="../C2.html" title="class in pkg2">C2</a></span></div>""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="caption"><span>Methods in <a href="../package-summary.html">pkg2</a> that return\
                     <a href="../C2.ModalExclusionType.html" title="enum in pkg2">C2.ModalExclusionT\
                    ype</a></span></div>""");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                """
                    <div class="caption"><span>Packages that use <a href="package-summary.html">pkg1</a></span></div>""",
                """
                    <div class="caption"><span>Classes in <a href="package-summary.html">pkg1</a> used by <a hre\
                    f="package-summary.html">pkg1</a></span></div>""");

        checkOutput("pkg2/package-use.html", true,
                """
                    <div class="caption"><span>Packages that use <a href="package-summary.html">pkg2</a></span></div>""",
                """
                    <div class="caption"><span>Classes in <a href="package-summary.html">pkg2</a> used by <a hre\
                    f="../pkg1/package-summary.html">pkg1</a></span></div>""");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<div class=\"caption\"><span>Fields</span></div>",
                "<div class=\"caption\"><span>Methods</span></div>");

        // Constant values
        checkOutput("constant-values.html", true,
                """
                    <div class="caption"><span>pkg1.<a href="pkg1/C1.html" title="class in pkg1">C1</a></span></div>""");

        // Overview Summary
        checkOutput("index.html", true,
                "<div class=\"caption\"><span>Packages</span></div>");
    }

    /*
     * Test for validating headers for HTML tables
     */
    void checkHtmlTableHeaders() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                """
                    <div class="table-header col-first">Class</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Interface</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/package-summary.html", true,
                """
                    <div class="table-header col-first">Enum</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Annotation Type</div>
                    <div class="table-header col-last">Description</div>""");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Field</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Method</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/C2.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Class</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Constructor</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                """
                    <div class="table-header col-first">Enum Constant</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/C3.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Required Element</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/C4.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Optional Element</div>
                    <div class="table-header col-last">Description</div>""");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                """
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg1/class-use/C1.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Field</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Method</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/class-use/C2.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Field</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Method</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                """
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Method</div>
                    <div class="table-header col-last">Description</div>""");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                """
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Class</div>
                    <div class="table-header col-last">Description</div>""");

        checkOutput("pkg2/package-use.html", true,
                """
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Class</div>
                    <div class="table-header col-last">Description</div>""");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                """
                    <div class="table-header col-first">Field</div>
                    <div class="table-header col-last">Description</div>""",
                """
                    <div class="table-header col-first">Method</div>
                    <div class="table-header col-last">Description</div>""");

        // Constant values
        checkOutput("constant-values.html", true,
                """
                    <div class="table-header col-first">Modifier and Type</div>
                    <div class="table-header col-second">Constant Field</div>
                    <div class="table-header col-last">Value</div>""");

        // Overview Summary
        checkOutput("index.html", true,
                """
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>""");
    }
}
