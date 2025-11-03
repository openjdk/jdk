/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8356975
 * @summary  Provide alternative way to generate preview API docs
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.* taglet.PreviewFeature taglet.PreviewNote
 * @run main TestPreviewTag
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;

public class TestPreviewTag  extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestPreviewTag();
        tester.runTests();
    }

    @Test
    public void testPreviewTag(Path base) throws Exception {

        javadoc("-d", base.resolve("out").toString(),
                "-tagletpath", System.getProperty("test.classes"),
                "-taglet", "taglet.PreviewFeature",
                "-taglet", "taglet.PreviewNote",
                "--preview-note-tag", "previewNote",
                "--preview-feature-tag", "previewFeature",
                "-sourcepath", testSrc,
                "api");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                """
                        warning: Multiple preview notes in otherPreviewMethod.
                            * @previewNote    Extra note tag triggers a warning""");

        checkOrder("api/package-summary.html",
                """
                        PreviewApi</a><sup class="preview-mark"><a href="PreviewApi.html#preview-api.PreviewApi">PREVIEW</a>""",
                """
                        <div class="block"><span class="preview-label">Preview.</span></div>
                        <div class="block">This is a preview API marked by javadoc tags.</div>""");

        checkOrder("api/OtherApi.html",
                """
                        <div class="preview-block" id="preview-previewMethod()">
                        <div class="preview-comment">Alternative preview note. <a href="PreviewApi.html" titl\
                        e="class in api"><code>PreviewApi</code></a><sup class="preview-mark"><a href="Previe\
                        wApi.html#preview-api.PreviewApi">PREVIEW</a></sup> is a preview API.</div>""",
                """
                        <div class="preview-block" id="preview-otherPreviewMethod()">
                        <div class="preview-comment">Alternative preview note for second preview feature.</div>""");

        checkOrder("api/PreviewApi.html",
                """
                        <div class="preview-block" id="preview-api.PreviewApi">
                        <div class="preview-comment">Alternative preview note. <a href="PreviewApi.html" titl\
                        e="class in api"><code>PreviewApi</code></a><sup class="preview-mark"><a href="#previ\
                        ew-api.PreviewApi">PREVIEW</a></sup> is a preview API.</div>""");

        checkOrder("preview-list.html",
                """
                        <li><label for="feature-1"><input type="checkbox" id="feature-1" disabled checked onc\
                        lick="toggleGlobal(this, '1', 3)"><span>First preview feature</span></label></li>
                        <li><label for="feature-2"><input type="checkbox" id="feature-2" disabled checked onc\
                        lick="toggleGlobal(this, '2', 3)"><span>Second preview feature</span></label></li>
                        <li><label for="feature-all"><input type="checkbox" id="feature-all" disabled checked\
                         onclick="toggleGlobal(this, 'all', 3)"><span>Toggle all</span></label></li>""",
                """
                        <div class="col-summary-item-name even-row-color class class-tab1"><a href="api/Previ\
                        ewApi.html" title="class in api">api.PreviewApi</a><sup class="preview-mark"><a href=\
                        "api/PreviewApi.html#preview-api.PreviewApi">PREVIEW</a></sup></div>
                        <div class="col-second even-row-color class class-tab1">First preview feature</div>
                        <div class="col-last even-row-color class class-tab1">
                        <div class="block">This is a preview API marked by javadoc tags.</div>""",
                """
                        <div class="col-summary-item-name even-row-color method method-tab2"><a href="api/Oth\
                        erApi.html#otherPreviewMethod()">api.OtherApi.otherPreviewMethod()</a><sup class="pre\
                        view-mark"><a href="api/OtherApi.html#preview-otherPreviewMethod()">PREVIEW</a></sup></div>
                        <div class="col-second even-row-color method method-tab2">Second preview feature</div>
                        <div class="col-last even-row-color method method-tab2">
                        <div class="block">This is another preview method.</div>""",
                """
                        <div class="col-summary-item-name odd-row-color method method-tab1"><a href="api/Othe\
                        rApi.html#previewMethod()">api.OtherApi.previewMethod()</a><sup class="preview-mark">\
                        <a href="api/OtherApi.html#preview-previewMethod()">PREVIEW</a></sup></div>
                        <div class="col-second odd-row-color method method-tab1">First preview feature</div>
                        <div class="col-last odd-row-color method method-tab1">
                        <div class="block">This is a preview method.</div>""");
    }
}
