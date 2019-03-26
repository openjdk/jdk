/*
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6851834
 * @summary This test verifies the HTML document generation for javadoc output.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.markup
 *          jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.util
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @author Bhavesh Patel
 * @run main TestHtmlDocument
 */


import jdk.javadoc.internal.doclets.formats.html.markup.*;

import static jdk.javadoc.internal.doclets.toolkit.util.DocletConstants.NL;

/**
 * The class reads each file, complete with newlines, into a string to easily
 * compare the existing markup with the generated markup.
 */
import javadoc.tester.JavadocTester;

public class TestHtmlDocument extends JavadocTester {

    // Entry point
    public static void main(String... args) throws Exception {
        TestHtmlDocument tester = new TestHtmlDocument();
        tester.runTests();
    }

    @Test
    public void test() {
        checking("markup");
        // Check whether the generated markup is same as the existing markup.
        String expected = readFile(testSrc, "testMarkup.html").replace("\n", NL);
        String actual = generateHtmlTree();
        if (actual.equals(expected)) {
            passed("");
        } else {
            failed("expected content in " + testSrc("testMarkup.html") + "\n"
                + "Actual output:\n"
                + actual);
        }
    }

    // Generate the HTML output using the HTML document generation within doclet.
    public static String generateHtmlTree() {
        // Document type for the HTML document
        HtmlTree html = new HtmlTree(HtmlTag.HTML);
        HtmlTree head = new HtmlTree(HtmlTag.HEAD);
        HtmlTree title = new HtmlTree(HtmlTag.TITLE);
        // String content within the document
        StringContent titleContent = new StringContent("Markup test");
        title.add(titleContent);
        head.add(title);
        // Test META tag
        HtmlTree meta = new HtmlTree(HtmlTag.META);
        meta.put(HtmlAttr.NAME, "keywords");
        meta.put(HtmlAttr.CONTENT, "testContent");
        head.add(meta);
        // Test invalid META tag
        HtmlTree invmeta = new HtmlTree(HtmlTag.META);
        head.add(invmeta);
        // Test LINK tag
        HtmlTree link = new HtmlTree(HtmlTag.LINK);
        link.put(HtmlAttr.REL, "testRel");
        link.put(HtmlAttr.HREF, "testLink.html");
        head.add(link);
        // Test invalid LINK tag
        HtmlTree invlink = new HtmlTree(HtmlTag.LINK);
        head.add(invlink);
        html.add(head);
        // Comment within the document
        Comment bodyMarker = new Comment("======== START OF BODY ========");
        html.add(bodyMarker);
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        Comment pMarker = new Comment("======== START OF PARAGRAPH ========");
        body.add(pMarker);
        HtmlTree p = new HtmlTree(HtmlTag.P);
        StringContent bodyContent = new StringContent(
                "This document is generated from sample source code and HTML " +
                "files with examples of a wide variety of Java language constructs: packages, " +
                "subclasses, subinterfaces, nested classes, nested interfaces," +
                "inheriting from other packages, constructors, fields," +
                "methods, and so forth. ");
        p.add(bodyContent);
        StringContent anchorContent = new StringContent("Click Here");
        p.add(HtmlTree.A("testLink.html", anchorContent));
        StringContent pContent = new StringContent(" to <test> out a link.");
        p.add(pContent);
        body.add(p);
        HtmlTree p1 = new HtmlTree(HtmlTag.P);
        // Test another version of A tag.
        HtmlTree anchor = new HtmlTree(HtmlTag.A);
        anchor.put(HtmlAttr.HREF, "testLink.html");
        anchor.put(HtmlAttr.ID, "Another version of a tag");
        p1.add(anchor);
        body.add(p1);
        // Test for empty tags.
        HtmlTree dl = new HtmlTree(HtmlTag.DL);
        html.add(dl);
        // Test for empty nested tags.
        HtmlTree dlTree = new HtmlTree(HtmlTag.DL);
        dlTree.add(new HtmlTree(HtmlTag.DT));
        dlTree.add(new HtmlTree (HtmlTag.DD));
        html.add(dlTree);
        HtmlTree dlDisplay = new HtmlTree(HtmlTag.DL);
        dlDisplay.add(new HtmlTree(HtmlTag.DT));
        HtmlTree dd = new HtmlTree (HtmlTag.DD);
        StringContent ddContent = new StringContent("Test DD");
        dd.add(ddContent);
        dlDisplay.add(dd);
        body.add(dlDisplay);
        StringContent emptyString = new StringContent("");
        body.add(emptyString);
        Comment emptyComment = new Comment("");
        body.add(emptyComment);
        HtmlTree hr = new HtmlTree(HtmlTag.HR);
        body.add(hr);
        html.add(body);
        HtmlDocument htmlDoc = new HtmlDocument(html);
        return htmlDoc.toString();
    }
}
