/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @author Bhavesh Patel
 * @build TestHtmlDocument
 * @run main TestHtmlDocument
 */

import java.io.*;
import com.sun.tools.doclets.formats.html.markup.*;

/**
 * The class reads each file, complete with newlines, into a string to easily
 * compare the existing markup with the generated markup.
 */
public class TestHtmlDocument {

    private static final String BUGID = "6851834";
    private static final String BUGNAME = "TestHtmlDocument";
    private static final String FS = System.getProperty("file.separator");
    private static final String LS = System.getProperty("line.separator");
    private static String srcdir = System.getProperty("test.src", ".");

    // Entry point
    public static void main(String[] args) throws IOException {
        // Check whether the generated markup is same as the existing markup.
        if (generateHtmlTree().equals(readFileToString(srcdir + FS + "testMarkup.html"))) {
            System.out.println("\nTest passed for bug " + BUGID + " (" + BUGNAME + ")\n");
        } else {
            throw new Error("\nTest failed for bug " + BUGID + " (" + BUGNAME + ")\n");
        }
    }

    // Generate the HTML output using the HTML document generation within doclet.
    public static String generateHtmlTree() {
        // Document type for the HTML document
        DocType htmlDocType = DocType.Transitional();
        HtmlTree html = new HtmlTree(HtmlTag.HTML);
        HtmlTree head = new HtmlTree(HtmlTag.HEAD);
        HtmlTree title = new HtmlTree(HtmlTag.TITLE);
        // String content within the document
        StringContent titleContent = new StringContent("Markup test");
        title.addContent(titleContent);
        head.addContent(title);
        // Test META tag
        HtmlTree meta = new HtmlTree(HtmlTag.META);
        meta.addAttr(HtmlAttr.NAME, "keywords");
        meta.addAttr(HtmlAttr.CONTENT, "testContent");
        head.addContent(meta);
        // Test invalid META tag
        HtmlTree invmeta = new HtmlTree(HtmlTag.META);
        head.addContent(invmeta);
        // Test LINK tag
        HtmlTree link = new HtmlTree(HtmlTag.LINK);
        link.addAttr(HtmlAttr.REL, "testRel");
        link.addAttr(HtmlAttr.HREF, "testLink.html");
        head.addContent(link);
        // Test invalid LINK tag
        HtmlTree invlink = new HtmlTree(HtmlTag.LINK);
        head.addContent(invlink);
        html.addContent(head);
        // Comment within the document
        Comment bodyMarker = new Comment("======== START OF BODY ========");
        html.addContent(bodyMarker);
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        Comment pMarker = new Comment("======== START OF PARAGRAPH ========");
        body.addContent(pMarker);
        HtmlTree p = new HtmlTree(HtmlTag.P);
        StringContent bodyContent = new StringContent(
                "This document is generated from sample source code and HTML " +
                "files with examples of a wide variety of Java language constructs: packages, " +
                "subclasses, subinterfaces, nested classes, nested interfaces," +
                "inheriting from other packages, constructors, fields," +
                "methods, and so forth. ");
        p.addContent(bodyContent);
        StringContent anchorContent = new StringContent("Click Here");
        p.addContent(HtmlTree.A("testLink.html", anchorContent));
        StringContent pContent = new StringContent(" to <test> out a link.");
        p.addContent(pContent);
        body.addContent(p);
        HtmlTree p1 = new HtmlTree(HtmlTag.P);
        // Test another version of A tag.
        HtmlTree anchor = new HtmlTree(HtmlTag.A);
        anchor.addAttr(HtmlAttr.HREF, "testLink.html");
        anchor.addAttr(HtmlAttr.NAME, "Another version of a tag");
        p1.addContent(anchor);
        body.addContent(p1);
        // Test for empty tags.
        HtmlTree dl = new HtmlTree(HtmlTag.DL);
        html.addContent(dl);
        // Test for empty nested tags.
        HtmlTree dlTree = new HtmlTree(HtmlTag.DL);
        dlTree.addContent(new HtmlTree(HtmlTag.DT));
        dlTree.addContent(new HtmlTree (HtmlTag.DD));
        html.addContent(dlTree);
        HtmlTree dlDisplay = new HtmlTree(HtmlTag.DL);
        dlDisplay.addContent(new HtmlTree(HtmlTag.DT));
        HtmlTree dd = new HtmlTree (HtmlTag.DD);
        StringContent ddContent = new StringContent("Test DD");
        dd.addContent(ddContent);
        dlDisplay.addContent(dd);
        body.addContent(dlDisplay);
        StringContent emptyString = new StringContent("");
        body.addContent(emptyString);
        Comment emptyComment = new Comment("");
        body.addContent(emptyComment);
        HtmlTree hr = new HtmlTree(HtmlTag.HR);
        body.addContent(hr);
        html.addContent(body);
        HtmlDocument htmlDoc = new HtmlDocument(htmlDocType, html);
        return htmlDoc.toString();
    }

    // Read the file into a String
    public static String readFileToString(String filename) throws IOException {
        File file = new File(filename);
        if ( !file.exists() ) {
            System.out.println("\nFILE DOES NOT EXIST: " + filename);
        }
        BufferedReader in = new BufferedReader(new FileReader(file));
        StringBuilder fileString = new StringBuilder();
        // Create an array of characters the size of the file
        try {
            String line;
            while ((line = in.readLine()) != null) {
                fileString.append(line);
                fileString.append(LS);
            }
        } finally {
            in.close();
        }
        return fileString.toString();
    }
}
