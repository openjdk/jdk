/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8250768 8261976 8277300 8282452 8287597 8325325
 * @summary  test generated docs for items declared using preview
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.resources:+open
 * @build    javadoc.tester.*
 * @run main TestPreview
 */

import java.nio.file.Paths;
import javadoc.tester.JavadocTester;

public class TestPreview extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestPreview();
        tester.runTests();
    }

    @Test
    public void testUserJavadoc() {
        String doc = Paths.get(testSrc, "doc").toUri().toString();
        javadoc("-d", "out-user-javadoc",
                "-XDforcePreview", "--enable-preview", "-source", System.getProperty("java.specification.version"),
                "--patch-module", "java.base=" + Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--add-exports", "java.base/preview=m",
                "--module-source-path", testSrc,
                "-linkoffline", doc, doc,
                "m/pkg");
        checkExit(Exit.OK);

        checkOutput("m/pkg/TestPreviewDeclarationUse.html", true,
                    "<code><a href=\"TestPreviewDeclaration.html\" title=\"interface in pkg\">TestPreviewDeclaration</a></code>");
        checkOutput("m/pkg/TestPreviewAPIUse.html", true,
                "<a href=\"" + doc + "java.base/preview/Core.html\" title=\"class or interface in preview\" class=\"external-link\">Core</a><sup><a href=\"" + doc + "java.base/preview/Core.html#preview-preview.Core\" title=\"class or interface in preview\" class=\"external-link\">PREVIEW</a>");
        checkOutput("m/pkg/DocAnnotation.html", true,
                "<span class=\"modifiers\">public @interface </span><span class=\"element-name type-name-label\">DocAnnotation</span>");
        checkOutput("m/pkg/DocAnnotationUse1.html", true,
                "<div class=\"inheritance\">pkg.DocAnnotationUse1</div>");
        checkOutput("m/pkg/DocAnnotationUse2.html", true,
                "<div class=\"inheritance\">pkg.DocAnnotationUse2</div>");
    }

    @Test
    public void testPreviewAPIJavadoc() {
        javadoc("-d", "out-preview-api",
                "--patch-module", "java.base=" + Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--add-exports", "java.base/preview=m",
                "--source-path", Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--show-packages=all",
                "preview");
        checkExit(Exit.OK);

        checkOutput("preview-list.html", true,
                """
                    <ul class="preview-feature-list">
                    <li><label for="feature-1">
                    <input type="checkbox" id="feature-1" disabled checked onclick="toggleGlobal(this, '1', 3)">
                    <span>0: <a href="https://openjdk.org/jeps/0">Test Feature (Preview)</a></span></label></li>
                    </ul>
                    <h2 title="Contents">Contents</h2>
                    <ul class="contents-list">
                    <li id="contents-package"><a href="#package">Packages</a></li>
                    <li id="contents-class"><a href="#class">Classes</a></li>
                    <li id="contents-record-class"><a href="#record-class">Record Classes</a></li>
                    <li id="contents-method"><a href="#method">Methods</a></li>
                    </ul>
                    """,
                """
                    <div id="package">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal">
                    <div class="caption"><span>Packages</span></div>
                    </div>
                    <div id="package.tabpanel" role="tabpanel" aria-labelledby="package-tab0">
                    <div class="summary-table three-column-summary">
                    <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Package</div>
                    <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Preview Feature</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-summary-item-name even-row-color package package-tab1"><a href="java.base/preview/package-summary.html">preview</a><sup><a href="java.base/preview/package-summary.html#preview-preview">PREVIEW</a></sup></div>
                    <div class="col-second even-row-color package package-tab1">Test Feature</div>
                    <div class="col-last even-row-color package package-tab1">
                    <div class="block">Preview package.</div>
                    </div>
                    """,
                """
                    <div id="record-class">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal">
                    <div class="caption"><span>Record Classes</span></div>
                    </div>
                    <div id="record-class.tabpanel" role="tabpanel" aria-labelledby="record-class-tab0">
                    <div class="summary-table three-column-summary">
                    <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Record Class</div>
                    <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Preview Feature</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-summary-item-name even-row-color record-class record-class-tab1"><a href="java.base/preview/CoreRecord.html" title="class in preview">preview.CoreRecord</a><sup><a href="java.base/preview/CoreRecord.html#preview-preview.CoreRecord">PREVIEW</a></sup></div>
                    <div class="col-second even-row-color record-class record-class-tab1">Test Feature</div>
                    <div class="col-last even-row-color record-class record-class-tab1"></div>
                    </div>
                    """,
                """
                    <div id="method">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal">
                    <div class="caption"><span>Methods</span></div>
                    </div>
                    <div id="method.tabpanel" role="tabpanel" aria-labelledby="method-tab0">
                    <div class="summary-table three-column-summary">
                    <div class="table-header col-first sort-asc" onclick="sortTable(this, 0, 3)">Method</div>
                    <div class="table-header col-second" onclick="sortTable(this, 1, 3)">Preview Feature</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-summary-item-name even-row-color method method-tab1"><a href="java.base/preview/CoreRecordComponent.html#i()">preview.CoreRecordComponent.i()</a><sup><a href="java.base/preview/CoreRecordComponent.html#preview-i()">PREVIEW</a></sup></div>
                    <div class="col-second even-row-color method method-tab1">Test Feature</div>
                    <div class="col-last even-row-color method method-tab1">
                    <div class="block">Returns the value of the <code>i</code> record component.</div>
                    </div>
                    """);

        // 8325325: Breadcrumb navigation links should not contain PREVIEW link
        checkOutput("java.base/preview/package-summary.html", true,
                """
                    <ol class="sub-nav-list">
                    <li><a href="../module-summary.html">java.base</a></li>
                    &nbsp;&gt;&nbsp;
                    <li><a href="package-summary.html" class="current-selection">preview</a></li>
                    </ol>""");
        checkOutput("java.base/preview/Core.html", true,
                """
                    <ol class="sub-nav-list">
                    <li><a href="../module-summary.html">java.base</a></li>
                    &nbsp;&gt;&nbsp;
                    <li><a href="package-summary.html">preview</a></li>
                    &nbsp;&gt;&nbsp;
                    <li><a href="Core.html" class="current-selection">Core</a></li>
                    </ol>""");
    }

    @Test
    public void test8277300() {
        javadoc("-d", "out-8277300",
                "--add-exports", "java.base/jdk.internal.javac=api2",
                "--source-path", Paths.get(testSrc, "api2").toAbsolutePath().toString(),
                "--show-packages=all",
                "api2/api");
        checkExit(Exit.OK);

        checkOutput("api2/api/API.html", true,
                    "<p><a href=\"#test()\"><code>test()</code></a></p>",
                    "<p><a href=\"#testNoPreviewInSig()\"><code>testNoPreviewInSig()</code></a></p>",
                    "title=\"class or interface in java.util\" class=\"external-link\">List</a>&lt;<a href=\"API.html\" title=\"class in api\">API</a><sup><a href=\"#preview-api.API\">PREVIEW</a></sup>&gt;");
        checkOutput("api2/api/API2.html", true,
                    "<a href=\"API.html#test()\"><code>API.test()</code></a><sup><a href=\"API.html#preview-api.API\">PREVIEW</a></sup>",
                    "<a href=\"API.html#testNoPreviewInSig()\"><code>API.testNoPreviewInSig()</code></a><sup><a href=\"API.html#preview-api.API\">PREVIEW</a></sup>",
                    "<a href=\"API3.html#test()\"><code>API3.test()</code></a><sup><a href=\"API3.html#preview-test()\">PREVIEW</a></sup>");
        checkOutput("api2/api/API3.html", true,
                    "<div class=\"block\"><a href=\"#test()\"><code>test()</code></a><sup><a href=\"#preview-test()\">PREVIEW</a></sup></div>");
    }

    @Test
    public void test8282452() {
        javadoc("-d", "out-8282452",
                "--patch-module", "java.base=" + Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--add-exports", "java.base/preview=m",
                "--source-path", Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--show-packages=all",
                "preview");
        checkExit(Exit.OK);

        checkOutput("java.base/preview/NoPreview.html", false,
                    "refers to one or more preview");
    }
}
