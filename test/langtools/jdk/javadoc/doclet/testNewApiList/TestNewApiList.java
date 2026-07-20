/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8263468 8269401 8268422 8287524 8325874 8331873 8345555 8359024
 * @summary  New page for "recent" new API
 * @library  ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestNewApiList
 */

import javadoc.tester.JavadocTester;

/**
 * Test --since option and "New API" list.
 */
public class TestNewApiList extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestNewApiList test = new TestNewApiList();
        test.runTests();
    }

    @Test
    public void testMultiRelease() throws Exception {
        javadoc("-d", "out-multi",
                "--no-platform-links",
                "--module-source-path", testSrc,
                "--since", "0.9,v1.0,1.2,2.0b,3.2,5",
                "--since-label", "New API in recent releases",
                "--module", "mdl",
                "pkg");
        checkExit(Exit.OK);
        checkMultiReleaseContents();
        checkMultiReleaseNewElements();
        checkMultiReleaseDeprecatedElements();
    }

    @Test
    public void testSingleRelease() throws Exception {
        javadoc("-d", "out-single",
                "--no-platform-links",
                "--module-source-path", testSrc,
                "--since", "5",
                "--module", "mdl",
                "pkg");
        checkExit(Exit.OK);
        checkSingleReleaseContents();
        checkSingleReleaseNewElements();
        checkSingleReleaseDeprecatedElements();
    }

    @Test
    public void testPackage() throws Exception {
        javadoc("-d", "out-package",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "--since", "1.2,2.0b,3.2,5,6",
                "pkg");
        checkExit(Exit.OK);
        checkPackageContents();
        checkPackageNewElements();
        checkPackageDeprecatedElements();
    }

    @Test
    public void testNoList() throws Exception {
        javadoc("-d", "out-none",
                "--no-platform-links",
                "--module-source-path", testSrc,
                "--since", "foo,bar",
                "--since-label", "New API in foo and bar",
                "--module", "mdl",
                "pkg");
        checkExit(Exit.OK);
        checkFiles(false, "new-list.html");
    }

    private void checkMultiReleaseContents() {
        checkOutput("new-list.html", true,
            """
                <h1 title="New API in recent releases" class="title">New API in recent releases</h1>
                </div>
                <div class="checkboxes">Show API added in: <label for="release-1"><input type="checkbox" id="release-1" disabled checked onclick="toggleGlobal(this, '1', 3)"><span>0.9</span></label> <label for="release-2"><input type="checkbox" id="release-2" disabled checked onclick="toggleGlobal(this, '2', 3)"><span>v1.0</span></label> <label for="release-3"><input type="checkbox" id="release-3" disabled checked onclick="toggleGlobal(this, '3', 3)"><span>1.2</span></label> <label for="release-4"><input type="checkbox" id="release-4" disabled checked onclick="toggleGlobal(this, '4', 3)"><span>2.0b</span></label> <label for="release-5"><input type="checkbox" id="release-5" disabled checked onclick="toggleGlobal(this, '5', 3)"><span>3.2</span></label> <label for="release-6"><input type="checkbox" id="release-6" disabled checked onclick="toggleGlobal(this, '6', 3)"><span>5</span></label> <label for="release-all"><input type="checkbox" id="release-all" disabled checked onclick="toggleGlobal(this, 'all', 3)"><span>Toggle all</span></label></div>
                <h2 title="Contents">Contents</h2>
                <ul class="contents-list">
                <li id="contents-module"><a href="#module">Modules</a></li>
                <li id="contents-package"><a href="#package">Packages</a></li>
                <li id="contents-interface"><a href="#interface">Interfaces</a></li>
                <li id="contents-class"><a href="#class">Classes</a></li>
                <li id="contents-enum-class"><a href="#enum-class">Enum Classes</a></li>
                <li id="contents-exception-class"><a href="#exception-class">Exception Classes</a></li>
                <li id="contents-record-class"><a href="#record-class">Record Classes</a></li>
                <li id="contents-annotation-interface"><a href="#annotation-interface">Annotation Interfaces</a></li>
                <li id="contents-field"><a href="#field">Fields</a></li>
                <li id="contents-method"><a href="#method">Methods</a></li>
                <li id="contents-constructor"><a href="#constructor">Constructors</a></li>
                <li id="contents-enum-constant"><a href="#enum-constant">Enum Constants</a></li>
                <li id="contents-annotation-interface-member"><a href="#annotation-interface-member">Annotation Interface Elements</a></li>
                </ul>""");
    }

    private void checkMultiReleaseNewElements() {
        checkOutput("new-list.html", true,
            """
                <div id="module">
                <div class="table-tabs">
                <div class="caption"><span>New Modules</span></div>
                </div>
                <div id="module.tabpanel" role="tabpanel" aria-labelledby="module-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Module</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color module module-tab5"><a href="mdl/mod\
                ule-summary.html">mdl</a></div>
                <div class="col-second even-row-color module module-tab5">3.2</div>
                <div class="col-last even-row-color module module-tab5">
                <div class="block">Module mdl.</div>
                </div>""",
            """
                <div id="package">
                <div class="table-tabs">
                <div class="caption"><span>New Packages</span></div>
                </div>
                <div id="package.tabpanel" role="tabpanel" aria-labelledby="package-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Package</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color package package-tab2"><a href="mdl/p\
                kg/package-summary.html">pkg</a></div>
                <div class="col-second even-row-color package package-tab2">v1.0</div>
                <div class="col-last even-row-color package package-tab2">
                <div class="block">Package pkg.</div>
                </div>""",
            """
                <div id="interface">
                <div class="table-tabs">
                <div class="caption"><span>New Interfaces</span></div>
                </div>
                <div id="interface.tabpanel" role="tabpanel" aria-labelledby="interface-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Interface</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color interface interface-tab1"><a href="m\
                dl/pkg/TestInterface.html" title="interface in pkg">pkg.TestInterface</a></div>
                <div class="col-second even-row-color interface interface-tab1">0.9</div>
                <div class="col-last even-row-color interface interface-tab1">
                <div class="block">Test interface.</div>
                </div>""",
            """
                <div id="class">
                <div class="table-tabs">
                <div class="caption"><span>New Classes</span></div>
                </div>
                <div id="class.tabpanel" role="tabpanel" aria-labelledby="class-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Class</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color class class-tab3"><a href="mdl/pkg/\
                TestClass.html" title="class in pkg">pkg.TestClass</a></div>
                <div class="col-second even-row-color class class-tab3">1.2</div>
                <div class="col-last even-row-color class class-tab3">
                <div class="block">TestClass declaration.</div>
                </div>""",
            """
                <div id="enum-class">
                <div class="table-tabs">
                <div class="caption"><span>New Enum Classes</span></div>
                </div>
                <div id="enum-class.tabpanel" role="tabpanel" aria-labelledby="enum-class-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Enum Class</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color enum-class enum-class-tab1"><a href\
                ="mdl/pkg/TestEnum.html" title="enum class in pkg">pkg.TestEnum</a></div>
                <div class="col-second even-row-color enum-class enum-class-tab1">0.9</div>
                <div class="col-last even-row-color enum-class enum-class-tab1">
                <div class="block">Test enum class.</div>
                </div>""",
            """
                <div id="exception-class">
                <div class="table-tabs">
                <div class="caption"><span>New Exception Classes</span></div>
                </div>
                <div id="exception-class.tabpanel" role="tabpanel" aria-labelledby="exception-class-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Exception Class</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color exception-class exception-class-tab\
                4"><a href="mdl/pkg/TestError.html" title="class in pkg">pkg.TestError</a></div>
                <div class="col-second even-row-color exception-class exception-class-tab4">2.0b</div>
                <div class="col-last even-row-color exception-class exception-class-tab4">
                <div class="block">Test error class.</div>
                </div>
                <div class="col-summary-item-name odd-row-color exception-class exception-class-tab1\
                "><a href="mdl/pkg/TestException.html" title="class in pkg">pkg.TestException</a></div>
                <div class="col-second odd-row-color exception-class exception-class-tab1">0.9</div>
                <div class="col-last odd-row-color exception-class exception-class-tab1">
                <div class="block">Test exception class.</div>
                </div>""",
            """
                <div id="record-class">
                <div class="table-tabs">
                <div class="caption"><span>New Record Classes</span></div>
                </div>
                <div id="record-class.tabpanel" role="tabpanel" aria-labelledby="record-class-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Record Class</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color record-class record-class-tab5"><a\
                 href="mdl/pkg/TestRecord.html" title="class in pkg">pkg.TestRecord</a></div>
                <div class="col-second even-row-color record-class record-class-tab5">3.2</div>
                <div class="col-last even-row-color record-class record-class-tab5">
                <div class="block">Test record.</div>
                </div>""",
            """
                <div id="annotation-interface">
                <div class="table-tabs">
                <div class="caption"><span>New Annotation Interfaces</span></div>
                </div>
                <div id="annotation-interface.tabpanel" role="tabpanel" aria-labelledby="annotation-interface-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Annotation Interface</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color annotation-interface annotation-interface-tab4"><a href="mdl/pkg/TestAnnotation.html" title="annotation interface in pkg">pkg.TestAnnotation</a></div>
                <div class="col-second even-row-color annotation-interface annotation-interface-tab4">2.0b</div>
                <div class="col-last even-row-color annotation-interface annotation-interface-tab4">
                <div class="block">An annotation interface.</div>
                </div>""",
            """
                <div id="field">
                <div class="table-tabs">
                <div class="caption"><span>New Fields</span></div>
                </div>
                <div id="field.tabpanel" role="tabpanel" aria-labelledby="field-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Field</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color field field-tab3"><a href="mdl/pkg/\
                TestClass.html#field">pkg.TestClass.field</a></div>
                <div class="col-second even-row-color field field-tab3">1.2</div>
                <div class="col-last even-row-color field field-tab3">
                <div class="block">TestClass field.</div>
                </div>
                <div class="col-summary-item-name odd-row-color field field-tab2"><a href="mdl/pkg/T\
                estError.html#field">pkg.TestError.field</a></div>
                <div class="col-second odd-row-color field field-tab2">v1.0</div>
                <div class="col-last odd-row-color field field-tab2">
                <div class="block">Test error field.</div>
                </div>
                <div class="col-summary-item-name even-row-color field field-tab5"><a href="mdl/pkg/\
                TestException.html#field">pkg.TestException.field</a></div>
                <div class="col-second even-row-color field field-tab5">3.2</div>
                <div class="col-last even-row-color field field-tab5">
                <div class="block">Exception field.</div>
                </div>
                <div class="col-summary-item-name odd-row-color field field-tab4"><a href="mdl/pkg/T\
                estInterface.html#field">pkg.TestInterface.field</a></div>
                <div class="col-second odd-row-color field field-tab4">2.0b</div>
                <div class="col-last odd-row-color field field-tab4">
                <div class="block">Test interface field.</div>
                </div>""",
            """
                <div id="method">
                <div class="table-tabs">
                <div class="caption"><span>New Methods</span></div>
                </div>
                <div id="method.tabpanel" role="tabpanel" aria-labelledby="method-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color method method-tab5"><a href="mdl/pk\
                g/TestAnnotation.html#optional()">pkg.TestAnnotation.optional()</a></div>
                <div class="col-second even-row-color method method-tab5">3.2</div>
                <div class="col-last even-row-color method method-tab5">
                <div class="block">Optional annotation interface element.</div>
                </div>
                <div class="col-summary-item-name odd-row-color method method-tab4"><a href="mdl/pkg\
                /TestAnnotation.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second odd-row-color method method-tab4">2.0b</div>
                <div class="col-last odd-row-color method method-tab4">
                <div class="block">Required annotation interface element.</div>
                </div>
                <div class="col-summary-item-name even-row-color method method-tab4"><a href="mdl/pk\
                g/TestClass.html#method()">pkg.TestClass.method()</a></div>
                <div class="col-second even-row-color method method-tab4">2.0b</div>
                <div class="col-last even-row-color method method-tab4">
                <div class="block">TestClass method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color method method-tab6"><a href="mdl/pkg\
                /TestClass.html#overloadedMethod(java.lang.String)">pkg.TestClass.overloadedMethod<w\
                br>(String)</a></div>
                <div class="col-second odd-row-color method method-tab6">5</div>
                <div class="col-last odd-row-color method method-tab6">
                <div class="block">TestClass overloaded method.</div>
                </div>
                <div class="col-summary-item-name even-row-color method method-tab5"><a href="mdl/pk\
                g/TestError.html#method()">pkg.TestError.method()</a></div>
                <div class="col-second even-row-color method method-tab5">3.2</div>
                <div class="col-last even-row-color method method-tab5">
                <div class="block">Test error method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color method method-tab3"><a href="mdl/pkg\
                /TestException.html#method()">pkg.TestException.method()</a></div>
                <div class="col-second odd-row-color method method-tab3">1.2</div>
                <div class="col-last odd-row-color method method-tab3">
                <div class="block">Exception method.</div>
                </div>
                <div class="col-summary-item-name even-row-color method method-tab2"><a href="mdl/pk\
                g/TestInterface.html#method1()">pkg.TestInterface.method1()</a></div>
                <div class="col-second even-row-color method method-tab2">v1.0</div>
                <div class="col-last even-row-color method method-tab2">
                <div class="block">Interface method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color method method-tab5"><a href="mdl/pkg\
                /TestInterface.html#method2(java.lang.Class)">pkg.TestInterface.method2<wbr>(Class&l\
                t;?&gt;)</a></div>
                <div class="col-second odd-row-color method method-tab5">3.2</div>
                <div class="col-last odd-row-color method method-tab5">
                <div class="block">Interface method.</div>
                </div>
                <div class="col-summary-item-name even-row-color method method-tab6"><a href="mdl/pk\
                g/TestRecord.html#x()">pkg.TestRecord.x()</a></div>
                <div class="col-second even-row-color method method-tab6">5</div>
                <div class="col-last even-row-color method method-tab6">
                <div class="block">Test record getter.</div>
                </div>""",
            """
                <div id="constructor">
                <div class="table-tabs">
                <div class="caption"><span>New Constructors</span></div>
                </div>
                <div id="constructor.tabpanel" role="tabpanel" aria-labelledby="constructor-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color constructor constructor-tab4"><a hr\
                ef="mdl/pkg/TestClass.html#%3Cinit%3E()">pkg.TestClass()</a></div>
                <div class="col-second even-row-color constructor constructor-tab4">2.0b</div>
                <div class="col-last even-row-color constructor constructor-tab4">
                <div class="block">TestClass constructor.</div>
                </div>
                <div class="col-summary-item-name odd-row-color constructor constructor-tab5"><a hre\
                f="mdl/pkg/TestClass.html#%3Cinit%3E(java.lang.String)">pkg.TestClass<wbr>(String)</\
                a></div>
                <div class="col-second odd-row-color constructor constructor-tab5">3.2</div>
                <div class="col-last odd-row-color constructor constructor-tab5">
                <div class="block">TestClass constructor.</div>
                </div>
                <div class="col-summary-item-name even-row-color constructor constructor-tab6"><a hr\
                ef="mdl/pkg/TestError.html#%3Cinit%3E()">pkg.TestError()</a></div>
                <div class="col-second even-row-color constructor constructor-tab6">5</div>
                <div class="col-last even-row-color constructor constructor-tab6">
                <div class="block">Test error constructor.</div>
                </div>
                <div class="col-summary-item-name odd-row-color constructor constructor-tab6"><a hre\
                f="mdl/pkg/TestException.html#%3Cinit%3E()">pkg.TestException()</a></div>
                <div class="col-second odd-row-color constructor constructor-tab6">5</div>
                <div class="col-last odd-row-color constructor constructor-tab6">
                <div class="block">Exception constructor.</div>
                </div>""",
            """
                <div id="enum-constant">
                <div class="table-tabs">
                <div class="caption"><span>New Enum Constants</span></div>
                </div>
                <div id="enum-constant.tabpanel" role="tabpanel" aria-labelledby="enum-constant-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Enum Constant</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color enum-constant enum-constant-tab5"><\
                a href="mdl/pkg/TestEnum.html#DEPRECATED">pkg.TestEnum.DEPRECATED</a></div>
                <div class="col-second even-row-color enum-constant enum-constant-tab5">3.2</div>
                <div class="col-last even-row-color enum-constant enum-constant-tab5">
                <div class="block">Deprecated.</div>
                </div>
                <div class="col-summary-item-name odd-row-color enum-constant enum-constant-tab1"><a\
                 href="mdl/pkg/TestEnum.html#ONE">pkg.TestEnum.ONE</a></div>
                <div class="col-second odd-row-color enum-constant enum-constant-tab1">0.9</div>
                <div class="col-last odd-row-color enum-constant enum-constant-tab1">
                <div class="block">One.</div>
                </div>
                <div class="col-summary-item-name even-row-color enum-constant enum-constant-tab3"><\
                a href="mdl/pkg/TestEnum.html#THREE">pkg.TestEnum.THREE</a></div>
                <div class="col-second even-row-color enum-constant enum-constant-tab3">1.2</div>
                <div class="col-last even-row-color enum-constant enum-constant-tab3">
                <div class="block">Three.</div>
                </div>
                <div class="col-summary-item-name odd-row-color enum-constant enum-constant-tab2"><a\
                 href="mdl/pkg/TestEnum.html#TWO">pkg.TestEnum.TWO</a></div>
                <div class="col-second odd-row-color enum-constant enum-constant-tab2">v1.0</div>
                <div class="col-last odd-row-color enum-constant enum-constant-tab2">
                <div class="block">Two.</div>
                </div>""",
            """
                <div id="annotation-interface-member">
                <div class="table-tabs">
                <div class="caption"><span>New Annotation Interface Elements</span></div>
                </div>
                <div id="annotation-interface-member.tabpanel" role="tabpanel" aria-labelledby="annotation-interface-member-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Annotation Interface Element</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color annotation-interface-member annotat\
                ion-interface-member-tab5"><a href="mdl/pkg/TestAnnotation.html#optional()">pkg.Test\
                Annotation.optional()</a></div>
                <div class="col-second even-row-color annotation-interface-member annotation-interface-member-tab5">3.2</div>
                <div class="col-last even-row-color annotation-interface-member annotation-interface-member-tab5">
                <div class="block">Optional annotation interface element.</div>
                </div>
                <div class="col-summary-item-name odd-row-color annotation-interface-member annotati\
                on-interface-member-tab4"><a href="mdl/pkg/TestAnnotation.html#required()">pkg.TestA\
                nnotation.required()</a></div>
                <div class="col-second odd-row-color annotation-interface-member annotation-interface-member-tab4">2.0b</div>
                <div class="col-last odd-row-color annotation-interface-member annotation-interface-member-tab4">
                <div class="block">Required annotation interface element.</div>
                </div>""");
    }

    private void checkMultiReleaseDeprecatedElements() {
        checkOutput("deprecated-list.html", true,
            """
                <div id="for-removal">
                <div class="table-tabs">
                <div class="caption"><span>Terminally Deprecated Elements</span></div>
                </div>
                <div id="for-removal.tabpanel" role="tabpanel" aria-labelledby="for-removal-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Element</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color for-removal for-removal-tab1"><a hr\
                ef="mdl/pkg/TestAnnotation.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second even-row-color for-removal for-removal-tab1">3.2</div>
                <div class="col-last even-row-color for-removal for-removal-tab1"></div>
                </div>""",
            """
                <div id="method">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Methods</span></div>
                </div>
                <div id="method.tabpanel" role="tabpanel" aria-labelledby="method-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color method method-tab1"><a href="mdl/pk\
                g/TestAnnotation.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second even-row-color method method-tab1">3.2</div>
                <div class="col-last even-row-color method method-tab1"></div>
                </div>""",
            """
                <div id="constructor">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Constructors</span></div>
                </div>
                <div id="constructor.tabpanel" role="tabpanel" aria-labelledby="constructor-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color constructor"><a href="mdl/pkg/TestC\
                lass.html#%3Cinit%3E()">pkg.TestClass()</a></div>
                <div class="col-second even-row-color constructor">6</div>
                <div class="col-last even-row-color constructor"></div>
                </div>""",
            """
                <div id="enum-constant">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Enum Constants</span></div>
                </div>
                <div id="enum-constant.tabpanel" role="tabpanel" aria-labelledby="enum-constant-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Enum Constant</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color enum-constant enum-constant-tab2"><\
                a href="mdl/pkg/TestEnum.html#DEPRECATED">pkg.TestEnum.DEPRECATED</a></div>
                <div class="col-second even-row-color enum-constant enum-constant-tab2">5</div>
                <div class="col-last even-row-color enum-constant enum-constant-tab2"></div>
                </div>""",
            """
                <div id="annotation-interface-member">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Annotation Interface Elements</span></div>
                </div>
                <div id="annotation-interface-member.tabpanel" role="tabpanel" aria-labelledby="annotation-interface-member-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Annotation Interface Element</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color annotation-interface-member annotat\
                ion-interface-member-tab1"><a href="mdl/pkg/TestAnnotation.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second even-row-color annotation-interface-member annotation-interface-member-tab1">3.2</div>
                <div class="col-last even-row-color annotation-interface-member annotation-interface-member-tab1"></div>
                </div>""");
    }

    private void checkSingleReleaseContents() {
        checkOutput("new-list.html", true,
            """
                <h1 title="New API" class="title">New API</h1>
                </div>
                <h2 title="Contents">Contents</h2>
                <ul class="contents-list">
                <li id="contents-method"><a href="#method">Methods</a></li>
                <li id="contents-constructor"><a href="#constructor">Constructors</a></li>
                </ul>
                <ul class="block-list">""");
    }

    private void checkSingleReleaseNewElements() {
        checkOutput("new-list.html", true,
                """
                <div class="caption"><span>New Methods</span></div>
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color"><a href="mdl/pkg/TestClass.html#ov\
                erloadedMethod(java.lang.String)">pkg.TestClass.overloadedMethod<wbr>(String)</a></div>
                <div class="col-second even-row-color">5</div>
                <div class="col-last even-row-color">
                <div class="block">TestClass overloaded method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color"><a href="mdl/pkg/TestRecord.html#x(\
                )">pkg.TestRecord.x()</a></div>
                <div class="col-second odd-row-color">5</div>
                <div class="col-last odd-row-color">
                <div class="block">Test record getter.</div>
                </div>""",
            """
                <div id="constructor">
                <div class="caption"><span>New Constructors</span></div>
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color"><a href="mdl/pkg/TestError.html#%3\
                Cinit%3E()">pkg.TestError()</a></div>
                <div class="col-second even-row-color">5</div>
                <div class="col-last even-row-color">
                <div class="block">Test error constructor.</div>
                </div>
                <div class="col-summary-item-name odd-row-color"><a href="mdl/pkg/TestException.html\
                #%3Cinit%3E()">pkg.TestException()</a></div>
                <div class="col-second odd-row-color">5</div>
                <div class="col-last odd-row-color">
                <div class="block">Exception constructor.</div>
                </div>""");
    }

    private void checkSingleReleaseDeprecatedElements() {
        checkOutput("deprecated-list.html", true,
            """
                <h1 title="Deprecated API" class="title">Deprecated API</h1>
                </div>
                <div class="checkboxes">Show API deprecated in: <label for="release-1"><input type="\
                checkbox" id="release-1" disabled checked onclick="toggleGlobal(this, '1', 3)"><span\
                >5</span></label> <label for="release-other"><input type="checkbox" id="release-othe\
                r" disabled checked onclick="toggleGlobal(this, 'other', 3)"><span>other</span></lab\
                el> <label for="release-all"><input type="checkbox" id="release-all" disabled checke\
                d onclick="toggleGlobal(this, 'all', 3)"><span>Toggle all</span></label></div>
                <h2 title="Contents">Contents</h2>
                <ul class="contents-list">
                <li id="contents-for-removal"><a href="#for-removal">Terminally Deprecated</a></li>
                <li id="contents-method"><a href="#method">Methods</a></li>
                <li id="contents-constructor"><a href="#constructor">Constructors</a></li>
                <li id="contents-enum-constant"><a href="#enum-constant">Enum Constants</a></li>
                <li id="contents-annotation-interface-member"><a href="#annotation-interface-member">Annotation Interface Elements</a></li>
                </ul>""",
            """
                <div id="for-removal">
                <div class="table-tabs">
                <div class="caption"><span>Terminally Deprecated Elements</span></div>
                </div>
                <div id="for-removal.tabpanel" role="tabpanel" aria-labelledby="for-removal-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Element</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color for-removal"><a href="mdl/pkg/TestA\
                nnotation.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second even-row-color for-removal">3.2</div>
                <div class="col-last even-row-color for-removal"></div>
                </div>""",
            """
                <div id="method">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Methods</span></div>
                </div>
                <div id="method.tabpanel" role="tabpanel" aria-labelledby="method-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color method"><a href="mdl/pkg/TestAnnota\
                tion.html#required()">pkg.TestAnnotation.required()</a></div>
                <div class="col-second even-row-color method">3.2</div>
                <div class="col-last even-row-color method"></div>
                </div>""",
            """
                <div id="constructor">
                <div class="table-tabs">
                <div class="caption"><span>Deprecated Constructors</span></div>
                </div>
                <div id="constructor.tabpanel" role="tabpanel" aria-labelledby="constructor-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color constructor"><a href="mdl/pkg/TestC\
                lass.html#%3Cinit%3E()">pkg.TestClass()</a></div>
                <div class="col-second even-row-color constructor">6</div>
                <div class="col-last even-row-color constructor"></div>
                </div>""");
    }

    private void checkPackageContents() {
        checkOutput("new-list.html", true,
            """
                <h1 title="New API" class="title">New API</h1>
                </div>
                <div class="checkboxes">Show API added in: <label for="release-1"><input type="check\
                box" id="release-1" disabled checked onclick="toggleGlobal(this, '1', 3)"><span>1.2<\
                /span></label> <label for="release-2"><input type="checkbox" id="release-2" disabled\
                 checked onclick="toggleGlobal(this, '2', 3)"><span>2.0b</span></label> <label for="\
                release-3"><input type="checkbox" id="release-3" disabled checked onclick="toggleGlo\
                bal(this, '3', 3)"><span>3.2</span></label> <label for="release-4"><input type="chec\
                kbox" id="release-4" disabled checked onclick="toggleGlobal(this, '4', 3)"><span>5</\
                span></label> <label for="release-5"><input type="checkbox" id="release-5" disabled \
                checked onclick="toggleGlobal(this, '5', 3)"><span>6</span></label> <label for="rele\
                ase-all"><input type="checkbox" id="release-all" disabled checked onclick="toggleGlo\
                bal(this, 'all', 3)"><span>Toggle all</span></label></div>
                <h2 title="Contents">Contents</h2>
                <ul class="contents-list">
                <li id="contents-class"><a href="#class">Classes</a></li>
                <li id="contents-field"><a href="#field">Fields</a></li>
                <li id="contents-method"><a href="#method">Methods</a></li>
                <li id="contents-constructor"><a href="#constructor">Constructors</a></li>
                </ul>""");
    }

    private void checkPackageNewElements() {
        checkOutput("new-list.html", true,
            """
                <div id="class">
                <div class="table-tabs">
                <div class="caption"><span>New Classes</span></div>
                </div>
                <div id="class.tabpanel" role="tabpanel" aria-labelledby="class-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Class</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color class class-tab1"><a href="pkg/TestClass.html" \
                title="class in pkg">pkg.TestClass</a></div>
                <div class="col-second even-row-color class class-tab1">1.2</div>
                <div class="col-last even-row-color class class-tab1">
                <div class="block">TestClass declaration.</div>
                </div>""",
            """
                <div id="field">
                <div class="table-tabs">
                <div class="caption"><span>New Fields</span></div>
                </div>
                <div id="field.tabpanel" role="tabpanel" aria-labelledby="field-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Field</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color field field-tab1"><a href="pkg/Test\
                Class.html#field">pkg.TestClass.field</a></div>
                <div class="col-second even-row-color field field-tab1">1.2</div>
                <div class="col-last even-row-color field field-tab1">
                <div class="block">TestClass field.</div>
                </div>""",
            """
                <div id="method">
                <div class="table-tabs">
                <div class="caption"><span>New Methods</span></div>
                </div>
                <div id="method.tabpanel" role="tabpanel" aria-labelledby="method-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color method method-tab2"><a href="pkg/Te\
                stClass.html#method()">pkg.TestClass.method()</a></div>
                <div class="col-second even-row-color method method-tab2">2.0b</div>
                <div class="col-last even-row-color method method-tab2">
                <div class="block">TestClass method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color method method-tab5"><a href="pkg/Tes\
                tClass.html#overloadedMethod(int)">pkg.TestClass.overloadedMethod<wbr>(int)</a></div>
                <div class="col-second odd-row-color method method-tab5">6</div>
                <div class="col-last odd-row-color method method-tab5">
                <div class="block">TestClass overloaded method.</div>
                </div>
                <div class="col-summary-item-name even-row-color method method-tab4"><a href="pkg/Tes\
                tClass.html#overloadedMethod(java.lang.String)">pkg.TestClass.overloadedMethod<wbr>(S\
                tring)</a></div>
                <div class="col-second even-row-color method method-tab4">5</div>
                <div class="col-last even-row-color method method-tab4">
                <div class="block">TestClass overloaded method.</div>
                </div>""",
            """
                <div id="constructor">
                <div class="table-tabs">
                <div class="caption"><span>New Constructors</span></div>
                </div>
                <div id="constructor.tabpanel" role="tabpanel" aria-labelledby="constructor-tab0">
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Added in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color constructor constructor-tab2"><a hr\
                ef="pkg/TestClass.html#%3Cinit%3E()">pkg.TestClass()</a></div>
                <div class="col-second even-row-color constructor constructor-tab2">2.0b</div>
                <div class="col-last even-row-color constructor constructor-tab2">
                <div class="block">TestClass constructor.</div>
                </div>
                <div class="col-summary-item-name odd-row-color constructor constructor-tab3"><a hre\
                f="pkg/TestClass.html#%3Cinit%3E(java.lang.String)">pkg.TestClass<wbr>(String)</a></div>
                <div class="col-second odd-row-color constructor constructor-tab3">3.2</div>
                <div class="col-last odd-row-color constructor constructor-tab3">
                <div class="block">TestClass constructor.</div>
                </div>""");
    }

    private void checkPackageDeprecatedElements() {
        checkOutput("deprecated-list.html", true,
            """
                <h1 title="Deprecated API" class="title">Deprecated API</h1>
                </div>
                <h2 title="Contents">Contents</h2>
                <ul class="contents-list">
                <li id="contents-constructor"><a href="#constructor">Constructors</a></li>
                </ul>
                <ul class="block-list">
                """,
            """
                <div id="constructor">
                <div class="caption"><span>Deprecated Constructors</span></div>
                <div class="summary-table three-column-release-summary">
                <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Constructor</div>
                <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Deprecated in</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color"><a href="pkg/TestClass.html#%3Cini\
                t%3E()">pkg.TestClass()</a></div>
                <div class="col-second even-row-color">5</div>
                <div class="col-last even-row-color"></div>
                </div>""");
    }
}
