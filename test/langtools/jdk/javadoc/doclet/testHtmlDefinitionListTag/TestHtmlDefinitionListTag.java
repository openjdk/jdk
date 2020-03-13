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
 * @bug 6786690 6820360 8025633 8026567 8175200 8183511 8186332 8074407 8182765
 *      8230136
 * @summary This test verifies the nesting of definition list tags.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestHtmlDefinitionListTag
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javadoc.tester.JavadocTester;

public class TestHtmlDefinitionListTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestHtmlDefinitionListTag tester = new TestHtmlDefinitionListTag();
        tester.runTests();
    }

    @Test
    public void test_Comment_Deprecated() {
        javadoc("-Xdoclint:none",
                "-d", "out-1",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommon(true);
        checkCommentDeprecated(true);
    }

    @Test
    public void test_NoComment_Deprecated() {
        javadoc("-Xdoclint:none",
                "-d", "out-2",
                "-nocomment",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommon(true);
        checkCommentDeprecated(false); // ??
    }

    @Test
    public void test_Comment_NoDeprecated() {
        javadoc("-Xdoclint:none",
                "-d", "out-3",
                "-nodeprecated",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommon(false);
        checkNoDeprecated();
        checkNoCommentNoDeprecated(false);
    }

    @Test
    public void testNoCommentNoDeprecated() {
        javadoc("-Xdoclint:none",
                "-d", "out-4",
                "-nocomment",
                "-nodeprecated",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommon(false);
        checkNoCommentNoDeprecated(true);
        checkCommentDeprecated(false);
    }

    void checkCommon(boolean checkC5) {
        // Test common to all runs of javadoc. The class signature should print
        // properly enclosed definition list tags and the Annotation Type
        // Optional Element should print properly nested definition list tags
        // for default value.
        checkOutput("pkg1/C1.html", true,
                "<pre>public class <span class=\"type-name-label\">C1</span>\n" +
                "extends java.lang.Object\n" +
                "implements java.io.Serializable</pre>");
        checkOutput("pkg1/C4.html", true,
                "<dl class=\"notes\">\n" +
                "<dt>Default:</dt>\n" +
                "<dd>true</dd>\n" +
                "</dl>");

        // Test for valid HTML generation which should not comprise of empty
        // definition list tags.
        List<String> files= new ArrayList<>(Arrays.asList(
            "pkg1/package-summary.html",
            "pkg1/C1.html",
            "pkg1/C1.ModalExclusionType.html",
            "pkg1/C2.html",
            "pkg1/C2.ModalType.html",
            "pkg1/C3.html",
            "pkg1/C4.html",
            "overview-tree.html",
            "serialized-form.html"
        ));

        if (checkC5)
            files.add("pkg1/C5.html");

        for (String f: files) {
            checkOutput(f, false,
                    "<dl></dl>",
                    "<dl>\n</dl>",
                    "<dl class=\"notes\"></dl>",
                    "<dl class=\"notes\">\n</dl>");
        }
    }

    void checkCommentDeprecated(boolean expectFound) {
        // Test for normal run of javadoc in which various ClassDocs and
        // serialized form should have properly nested definition list tags
        // enclosing comments, tags and deprecated information.
        checkOutput("pkg1/package-summary.html", expectFound,
                "<dl class=\"notes\">\n" +
                "<dt>Since:</dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", expectFound,
                "<dl class=\"notes\">\n"
                + "<dt>Since:</dt>\n"
                + "<dd>JDK1.0</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"C2.html\" title=\"class in pkg1\"><code>"
                + "C2</code></a>, \n"
                + "<a href=\"../serialized-form.html#pkg1.C1\">"
                + "Serialized Form</a></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Parameters:</dt>\n"
                + "<dd><code>title</code> - the title</dd>\n"
                + "<dd><code>test</code> - boolean value"
                + "</dd>\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.lang.IllegalArgumentException</code> - if the "
                + "<code>owner</code>'s\n"
                + "     <code>GraphicsConfiguration</code> is not from a screen "
                + "device</dd>\n"
                + "<dd><code>HeadlessException</code></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Parameters:</dt>\n"
                + "<dd><code>undecorated"
                + "</code> - <code>true</code> if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> "
                + "if decorations are to be enabled.</dd>\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd>"
                + "<a href=\"#readObject()\"><code>readObject()"
                + "</code></a></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("pkg1/C2.html", expectFound,
                "<dl class=\"notes\">\n"
                + "<dt>Parameters:</dt>\n"
                + "<dd><code>set</code> - boolean</dd>\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", expectFound,
                "<dl class=\"notes\">\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl class=\"notes\">\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl class=\"notes\">\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>"
                + "</div>\n"
                + "<div class=\"block\">The name for this class.</div>");
    }

    void checkNoDeprecated() {
        // Test with -nodeprecated option. The ClassDocs should have properly nested
        // definition list tags enclosing comments and tags. The ClassDocs should not
        // display definition list for deprecated information. The serialized form
        // should display properly nested definition list tags for comments, tags
        // and deprecated information.
        checkOutput("pkg1/package-summary.html", true,
                "<dl class=\"notes\">\n" +
                "<dt>Since:</dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", true,
                "<dl class=\"notes\">\n" +
                "<dt>Since:</dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "<dt>See Also:</dt>\n" +
                "<dd><a href=\"C2.html\" title=\"class in pkg1\">" +
                "<code>C2</code></a>, \n" +
                "<a href=\"../serialized-form.html#pkg1.C1\">" +
                "Serialized Form</a></dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", true,
                "<dl class=\"notes\">\n"
                + "<dt>Parameters:</dt>\n"
                + "<dd><code>title</code> - the title</dd>\n"
                + "<dd><code>"
                + "test</code> - boolean value</dd>\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.lang.IllegalArgumentException"
                + "</code> - if the <code>owner</code>'s\n"
                + "     <code>GraphicsConfiguration"
                + "</code> is not from a screen device</dd>\n"
                + "<dd><code>"
                + "HeadlessException</code></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Parameters:</dt>\n"
                + "<dd><code>undecorated</code> - <code>true</code>"
                + " if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> if decorations are to be enabled."
                + "</dd>\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"#readObject()\">"
                + "<code>readObject()</code></a></dd>\n"
                + "</dl>",
                "<dl class=\"notes\">\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", true,
                "<dl class=\"notes\">\n"
                + "<dt>Throws:"
                + "</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl class=\"notes\">\n"
                + "<dt>Since:</dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt>See Also:</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl class=\"notes\">\n"
                + "<dt>Throws:</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>",
                "<span class=\"deprecated-label\">Deprecated.</span>"
                + "</div>\n"
                + "<div class=\"block\">"
                + "The name for this class.</div>");
    }

    void checkNoCommentNoDeprecated(boolean expectFound) {
        // Test with -nocomment and -nodeprecated options. The ClassDocs whould
        // not display definition lists for any member details.
        checkOutput("pkg1/C1.html", expectFound,
                "<div class=\"member-signature\"><span class=\"modifiers\">public</span>&nbsp;" +
                "<span class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">readObject</span>()\n" +
                "                throws <span class=\"exceptions\">java.io.IOException</span></div>\n" +
                "</section>\n" +
                "</li>");

        checkOutput("pkg1/C2.html", expectFound,
                "<div class=\"member-signature\"><span class=\"modifiers\">public</span>" +
                "&nbsp;<span class=\"member-name\">C2</span>()</div>\n" +
                "</section>\n" +
                "</li>");

        checkOutput("pkg1/C1.ModalExclusionType.html", expectFound,
                "<div class=\"member-signature\"><span class=\"modifiers\">public static final</span>&nbsp;" +
                "<span class=\"return-type\"><a href=\"C1.ModalExclusionType.html\" title=\"enum in pkg1\">" +
                "C1.ModalExclusionType</a></span>&nbsp;<span class=\"member-name\">APPLICATION_EXCLUDE</span></div>\n" +
                "</section>\n" +
                "</li>");

        checkOutput("serialized-form.html", expectFound,
                "<pre>boolean " +
                "undecorated</pre>\n" +
                "<div class=\"deprecation-block\"><span class=\"deprecated-label\">" +
                "Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\"><code>"
                + "setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                +
                "</li>",
                "<span class=\"deprecated-label\">"
                + "Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">As of JDK version"
                + " 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "</li>");
    }
}
