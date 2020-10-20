/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8250768
 * @summary  test generated docs for items declared using preview
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.resources:+open
 * @build    javadoc.tester.*
 * @run main TestPreview
 */

import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javadoc.tester.JavadocTester;

public class TestPreview extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestPreview tester = new TestPreview();
        tester.runTests();
    }

    @Test
    public void test() {
        String doc = "file://" + Paths.get(testSrc, "doc").toAbsolutePath().toString();
        javadoc("-d", "out",
                "-XDforcePreview", "--enable-preview", "-source", System.getProperty("java.specification.version"),
                "--patch-module", "java.base=" + Paths.get(testSrc, "api").toAbsolutePath().toString(),
                "--add-exports", "java.base/preview=m",
                "--module-source-path", testSrc,
                "-linkoffline", doc, doc,
                "m/pkg");
        checkExit(Exit.OK);

        ResourceBundle bundle = ResourceBundle.getBundle("jdk.javadoc.internal.doclets.formats.html.resources.standard", ModuleLayer.boot().findModule("jdk.javadoc").get());

        {
            String zero = MessageFormat.format(bundle.getString("doclet.PreviewLeadingNote"), "TestPreviewDeclaration");
            String one = MessageFormat.format(bundle.getString("doclet.Declared_Using_Preview"), "TestPreviewDeclaration", "Sealed Classes", "<code>sealed</code>");
            String two = MessageFormat.format(bundle.getString("doclet.PreviewTrailingNote1"), "TestPreviewDeclaration");
            String three = MessageFormat.format(bundle.getString("doclet.PreviewTrailingNote2"), new Object[0]);
            String expectedTemplate = """
                                      <div class="preview-block" id="preview-pkg.TestPreviewDeclaration"><span class="preview-label">{0}</span>
                                      <ul class="preview-comment">
                                      <li>{1}</li>
                                      </ul>
                                      <div class="preview-comment">{2}</div>
                                      <div class="preview-comment">{3}</div>
                                      </div>""";
            String expected = MessageFormat.format(expectedTemplate, zero, one, two, three);
            expected = expected.replace("\n", NL);
            checkOutput("m/pkg/TestPreviewDeclaration.html", true, expected);
        }

        checkOutput("m/pkg/TestPreviewDeclarationUse.html", true,
                    "<code><a href=\"TestPreviewDeclaration.html\" title=\"interface in pkg\">TestPreviewDeclaration</a><sup><a href=\"TestPreviewDeclaration.html#preview-pkg.TestPreviewDeclaration\">PREVIEW</a></sup></code>");
        checkOutput("m/pkg/TestPreviewAPIUse.html", true,
                "<a href=\"" + doc + "/java.base/preview/Core.html\" title=\"class or interface in preview\" class=\"external-link\">Core</a><sup><a href=\"" + doc + "/java.base/preview/Core.html#preview-preview.Core\" title=\"class or interface in preview\" class=\"external-link\">PREVIEW</a>");
        checkOutput("m/pkg/DocAnnotation.html", true,
                "<div class=\"preview-block\" id=\"preview-pkg.DocAnnotation\"><span class=\"preview-label\">");
        checkOutput("m/pkg/DocAnnotationUse1.html", true,
                "<div class=\"preview-block\" id=\"preview-pkg.DocAnnotationUse1\"><span class=\"preview-label\">");
        checkOutput("m/pkg/DocAnnotationUse2.html", true,
                "<div class=\"preview-block\" id=\"preview-pkg.DocAnnotationUse2\"><span class=\"preview-label\">");
    }
}
