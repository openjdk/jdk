/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary This test verifies the nesting of definition list tags.
 * @author Bhavesh Patel
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
//        tester.run(ARGS1, TEST_ALL, NEGATED_TEST_NO_C5);
//        tester.runTestsOnHTML(NO_TEST,  NEGATED_TEST_C5);
//        tester.runTestsOnHTML(TEST_CMNT_DEPR, NO_TEST);
        javadoc("-Xdoclint:none",
                "-d", "out-1",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommon(true);
        checkCommentDeprecated(true);
    }

    @Test
    public void test_Comment_Deprecated_html4() {
        javadoc("-Xdoclint:none",
                "-d", "out-1-html4",
                "-html4",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommentDeprecated_html4(true);
    }

    @Test
    public void test_NoComment_Deprecated() {
//        tester.run(ARGS2, TEST_ALL, NEGATED_TEST_NO_C5);
//        tester.runTestsOnHTML(NO_TEST,  NEGATED_TEST_C5);
//        tester.runTestsOnHTML(NO_TEST, TEST_CMNT_DEPR);
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
    public void test_NoComment_Deprecated_html4() {
        javadoc("-Xdoclint:none",
                "-d", "out-2-html4",
                "-html4",
                "-nocomment",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkCommentDeprecated_html4(false);
    }

    @Test
    public void test_Comment_NoDeprecated() {
//        tester.run(ARGS3, TEST_ALL, NEGATED_TEST_NO_C5);
//        tester.runTestsOnHTML(TEST_NODEPR, TEST_NOCMNT_NODEPR);
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
    public void test_Comment_NoDeprecated_html4() {
        javadoc("-Xdoclint:none",
                "-d", "out-3-html4",
                "-html4",
                "-nodeprecated",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);
        checkNoDeprecated_html4();
        checkNoCommentNoDeprecated_html4(false);
    }

    @Test
    public void testNoCommentNoDeprecated() {
//        tester.run(ARGS4, TEST_ALL, NEGATED_TEST_NO_C5);
//        tester.runTestsOnHTML(TEST_NOCMNT_NODEPR, TEST_CMNT_DEPR);
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

    @Test
    public void testNoCommentNoDeprecated_html4() {
        javadoc("-Xdoclint:none",
                "-d", "out-4-html4",
                "-html4",
                "-nocomment",
                "-nodeprecated",
                "-sourcepath", testSrc,
                "pkg1");
        checkNoCommentNoDeprecated_html4(true);
        checkCommentDeprecated_html4(false);
    }

    void checkCommon(boolean checkC5) {
        // Test common to all runs of javadoc. The class signature should print
        // properly enclosed definition list tags and the Annotation Type
        // Optional Element should print properly nested definition list tags
        // for default value.
        checkOutput("pkg1/C1.html", true,
                "<pre>public class <span class=\"typeNameLabel\">C1</span>\n" +
                "extends java.lang.Object\n" +
                "implements java.io.Serializable</pre>");
        checkOutput("pkg1/C4.html", true,
                "<dl>\n" +
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
                    "<dl>\n</dl>");
        }
    }

    void checkCommentDeprecated(boolean expectFound) {
        // Test for normal run of javadoc in which various ClassDocs and
        // serialized form should have properly nested definition list tags
        // enclosing comments, tags and deprecated information.
        checkOutput("pkg1/package-summary.html", expectFound,
                "<dl>\n" +
                "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", expectFound,
                "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JDK1.0</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"C2.html\" title=\"class in pkg1\"><code>"
                + "C2</code></a>, \n"
                + "<a href=\"../serialized-form.html#pkg1.C1\">"
                + "Serialized Form</a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:</span></dt>\n"
                + "<dd><code>title</code> - the title</dd>\n"
                + "<dd><code>test</code> - boolean value"
                + "</dd>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span></dt>\n"
                + "<dd><code>java.lang.IllegalArgumentException</code> - if the "
                + "<code>owner</code>'s\n"
                + "     <code>GraphicsConfiguration</code> is not from a screen "
                + "device</dd>\n"
                + "<dd><code>HeadlessException</code></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:</span></dt>\n"
                + "<dd><code>undecorated"
                + "</code> - <code>true</code> if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> "
                + "if decorations are to be enabled.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:"
                + "</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd>"
                + "<a href=\"#readObject()\"><code>readObject()"
                + "</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:"
                + "</span></dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("pkg1/C2.html", expectFound,
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:"
                + "</span></dt>\n"
                + "<dd><code>set</code> - boolean</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">"
                + "Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", expectFound,
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:"
                + "</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>"
                + "</div>\n"
                + "<div class=\"block\">The name for this class.</div>");
    }

    void checkCommentDeprecated_html4(boolean expectFound) {
        // Test for normal run of javadoc in which various ClassDocs and
        // serialized form should have properly nested definition list tags
        // enclosing comments, tags and deprecated information.
        checkOutput("pkg1/C1.html", expectFound,
                "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:</span></dt>\n"
                + "<dd><code>undecorated"
                + "</code> - <code>true</code> if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> "
                + "if decorations are to be enabled.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:"
                + "</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd>"
                + "<a href=\"#readObject--\"><code>readObject()"
                + "</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:"
                + "</span></dt>\n"
                + "<dd><a href=\"#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", expectFound,
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:"
                + "</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>");
    }

    void checkNoDeprecated() {
        // Test with -nodeprecated option. The ClassDocs should have properly nested
        // definition list tags enclosing comments and tags. The ClassDocs should not
        // display definition list for deprecated information. The serialized form
        // should display properly nested definition list tags for comments, tags
        // and deprecated information.
        checkOutput("pkg1/package-summary.html", true,
                "<dl>\n" +
                "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", true,
                "<dl>\n" +
                "<dt><span class=\"simpleTagLabel\">Since:</span>" +
                "</dt>\n" +
                "<dd>JDK1.0</dd>\n" +
                "<dt><span class=\"seeLabel\">See Also:" +
                "</span></dt>\n" +
                "<dd><a href=\"C2.html\" title=\"class in pkg1\">" +
                "<code>C2</code></a>, \n" +
                "<a href=\"../serialized-form.html#pkg1.C1\">" +
                "Serialized Form</a></dd>\n" +
                "</dl>");

        checkOutput("pkg1/C1.html", true,
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:"
                + "</span></dt>\n"
                + "<dd><code>title</code> - the title</dd>\n"
                + "<dd><code>"
                + "test</code> - boolean value</dd>\n"
                + "<dt><span class=\"throwsLabel\">Throws:"
                + "</span></dt>\n"
                + "<dd><code>java.lang.IllegalArgumentException"
                + "</code> - if the <code>owner</code>'s\n"
                + "     <code>GraphicsConfiguration"
                + "</code> is not from a screen device</dd>\n"
                + "<dd><code>"
                + "HeadlessException</code></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:"
                + "</span></dt>\n"
                + "<dd><code>undecorated</code> - <code>true</code>"
                + " if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> if decorations are to be enabled."
                + "</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#readObject()\">"
                + "<code>readObject()</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt>"
                + "<span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", true,
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:"
                + "</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>"
                + "</div>\n"
                + "<div class=\"block\">"
                + "The name for this class.</div>");
    }

    void checkNoDeprecated_html4() {
        // Test with -nodeprecated option. The ClassDocs should have properly nested
        // definition list tags enclosing comments and tags. The ClassDocs should not
        // display definition list for deprecated information. The serialized form
        // should display properly nested definition list tags for comments, tags
        // and deprecated information.
        checkOutput("pkg1/C1.html", true,
                "<dl>\n"
                + "<dt><span class=\"paramLabel\">Parameters:"
                + "</span></dt>\n"
                + "<dd><code>undecorated</code> - <code>true</code>"
                + " if no decorations are\n"
                + "         to be enabled;\n"
                + "         <code>false</code> if decorations are to be enabled."
                + "</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#readObject--\">"
                + "<code>readObject()</code></a></dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "<dt>"
                + "<span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>");

        checkOutput("serialized-form.html", true,
                "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:</span>"
                + "</dt>\n"
                + "<dd><code>"
                + "java.io.IOException</code></dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">This field indicates whether the C1 is "
                + "undecorated.</div>\n"
                + "&nbsp;\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>1.4</dd>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span>"
                + "</dt>\n"
                + "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>C1.setUndecorated(boolean)</code></a></dd>\n"
                + "</dl>",
                "<span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "<div class=\"block\">Reads the object stream.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"throwsLabel\">Throws:"
                + "</span></dt>\n"
                + "<dd><code>java.io.IOException</code></dd>\n"
                + "</dl>");
    }

    void checkNoCommentNoDeprecated(boolean expectFound) {
        // Test with -nocomment and -nodeprecated options. The ClassDocs whould
        // not display definition lists for any member details.
        checkOutput("pkg1/C1.html", expectFound,
                "<pre class=\"methodSignature\">public&nbsp;void&nbsp;readObject()\n" +
                "                throws java.io.IOException</pre>\n" +
                "</li>");

        checkOutput("pkg1/C2.html", expectFound,
                "<pre>public&nbsp;C2()</pre>\n" +
                "</li>");

        checkOutput("pkg1/C1.ModalExclusionType.html", expectFound,
                "<pre>public " +
                "static final&nbsp;<a href=\"C1.ModalExclusionType.html\" " +
                "title=\"enum in pkg1\">C1.ModalExclusionType</a> " +
                "APPLICATION_EXCLUDE</pre>\n" +
                "</li>");

        checkOutput("serialized-form.html", expectFound,
                "<pre>boolean " +
                "undecorated</pre>\n" +
                "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">" +
                "Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\"><code>"
                + "setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                +
                "</li>",
                "<span class=\"deprecatedLabel\">"
                + "Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version"
                + " 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated(boolean)\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "</li>");
    }

    void checkNoCommentNoDeprecated_html4(boolean expectFound) {
        // Test with -nocomment and -nodeprecated options. The ClassDocs whould
        // not display definition lists for any member details.
        checkOutput("serialized-form.html", expectFound,
                "<pre>boolean " +
                "undecorated</pre>\n" +
                "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">" +
                "Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\"><code>"
                + "setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                +
                "</li>",
                "<span class=\"deprecatedLabel\">"
                + "Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">As of JDK version"
                + " 1.5, replaced by\n"
                + " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">"
                + "<code>setUndecorated(boolean)</code></a>.</div>\n"
                + "</div>\n"
                + "</li>");
    }
}
