/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6786690 6820360 8025633 8026567
 * @summary This test verifies the nesting of definition list tags.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestHtmlDefinitionListTag
 * @run main TestHtmlDefinitionListTag
 */

public class TestHtmlDefinitionListTag extends JavadocTester {

    private static final String BUG_ID = "6786690-6820360";

    // Test common to all runs of javadoc. The class signature should print
    // properly enclosed definition list tags and the Annotation Type
    // Optional Element should print properly nested definition list tags
    // for default value.
    private static final String[][] TEST_ALL = {
        {BUG_ID + "/pkg1/C1.html", "<pre>public class " +
                 "<span class=\"typeNameLabel\">C1</span>\n" +
                 "extends java.lang.Object\n" +
                 "implements java.io.Serializable</pre>"},
        {BUG_ID + "/pkg1/C4.html", "<dl>\n" +
                 "<dt>Default:</dt>\n" +
                 "<dd>true</dd>\n" +
                 "</dl>"}};

    // Test for normal run of javadoc in which various ClassDocs and
    // serialized form should have properly nested definition list tags
    // enclosing comments, tags and deprecated information.
    private static final String[][] TEST_CMNT_DEPR = {
        {BUG_ID + "/pkg1/package-summary.html", "<dl>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>JDK1.0</dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>JDK1.0</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span></dt>\n" +
                 "<dd><a href=\"../pkg1/C2.html\" title=\"class in pkg1\"><code>" +
                 "C2</code></a>, \n" +
                 "<a href=\"../serialized-form.html#pkg1.C1\">" +
                 "Serialized Form</a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span></dt>\n" +
                 "<dd>" +
                 "<a href=\"../pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
                 "<dt><span class=\"paramLabel\">Parameters:</span></dt>\n" +
                 "<dd><code>title" +
                 "</code> - the title</dd>\n" +
                 "<dd><code>test</code> - boolean value" +
                 "</dd>\n" +
                 "<dt><span class=\"throwsLabel\">Throws:</span></dt>\n" +
                 "<dd><code>java.lang.IllegalArgumentException</code> - if the " +
                 "<code>owner</code>'s\n" +
                 "     <code>GraphicsConfiguration</code> is not from a screen " +
                 "device</dd>\n" +
                 "<dd><code>HeadlessException</code></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"paramLabel\">Parameters:</span></dt>\n" +
        "<dd><code>undecorated" +
                 "</code> - <code>true</code> if no decorations are\n" +
                 "         to be enabled;\n" +
                 "         <code>false</code> " +
                 "if decorations are to be enabled.</dd>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:" +
                 "</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span></dt>\n" +
                 "<dd>" +
                 "<a href=\"../pkg1/C1.html#readObject--\"><code>readObject()" +
                 "</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"throwsLabel\">Throws:</span></dt>\n" +
                 "<dd><code>java.io.IOException</code></dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:" +
                 "</span></dt>\n" +
                 "<dd><a href=\"../pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C2.html", "<dl>\n" +
        "<dt><span class=\"paramLabel\">Parameters:" +
                 "</span></dt>\n" +
                 "<dd><code>set</code> - boolean</dd>\n" +
                 "<dt><span class=\"simpleTagLabel\">" +
                 "Since:</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html", "<dl>\n" +
        "<dt><span class=\"throwsLabel\">Throws:</span>" +
                 "</dt>\n" +
                 "<dd><code>" +
                 "java.io.IOException</code></dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span>" +
                 "</dt>\n" +
                 "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>C1.setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;<span class=\"deprecationComment\">As of JDK version 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "<div class=\"block\">This field indicates whether the C1 is " +
                 "undecorated.</div>\n" +
                 "&nbsp;\n" +
                 "<dl>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span>" +
                 "</dt>\n" +
                 "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>C1.setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;<span class=\"deprecationComment\">As of JDK version 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "<div class=\"block\">Reads the object stream.</div>\n" +
                 "<dl>\n" +
                 "<dt><span class=\"throwsLabel\">Throws:" +
                 "</span></dt>\n" +
                 "<dd><code><code>" +
                 "IOException</code></code></dd>\n" +
                 "<dd><code>java.io.IOException</code></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;</div>\n" +
                 "<div class=\"block\">The name for this class.</div>"}};

    // Test with -nodeprecated option. The ClassDocs should have properly nested
    // definition list tags enclosing comments and tags. The ClassDocs should not
    // display definition list for deprecated information. The serialized form
    // should display properly nested definition list tags for comments, tags
    // and deprecated information.
    private static final String[][] TEST_NODEPR = {
        {BUG_ID + "/pkg1/package-summary.html", "<dl>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>JDK1.0</dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"simpleTagLabel\">Since:</span>" +
                 "</dt>\n" +
                 "<dd>JDK1.0</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:" +
                 "</span></dt>\n" +
                 "<dd><a href=\"../pkg1/C2.html\" title=\"class in pkg1\">" +
                 "<code>C2</code></a>, \n" +
                 "<a href=\"../serialized-form.html#pkg1.C1\">" +
                 "Serialized Form</a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"paramLabel\">Parameters:" +
                 "</span></dt>\n" +
                 "<dd><code>title</code> - the title</dd>\n" +
                 "<dd><code>" +
                 "test</code> - boolean value</dd>\n" +
                 "<dt><span class=\"throwsLabel\">Throws:" +
                 "</span></dt>\n" +
                 "<dd><code>java.lang.IllegalArgumentException" +
                 "</code> - if the <code>owner</code>'s\n" +
                 "     <code>GraphicsConfiguration" +
                 "</code> is not from a screen device</dd>\n" +
                 "<dd><code>" +
                 "HeadlessException</code></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"paramLabel\">Parameters:" +
                 "</span></dt>\n" +
                 "<dd><code>undecorated</code> - <code>true</code>" +
                 " if no decorations are\n" +
                 "         to be enabled;\n" +
                 "         <code>false</code> if decorations are to be enabled." +
                 "</dd>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span></dt>\n" +
                 "<dd><a href=\"../pkg1/C1.html#readObject--\">" +
                 "<code>readObject()</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "<dt><span class=\"throwsLabel\">Throws:</span>" +
                 "</dt>\n" +
                 "<dd><code>java.io.IOException</code></dd>\n" +
                 "<dt>" +
                 "<span class=\"seeLabel\">See Also:</span></dt>\n" +
                 "<dd><a href=\"../pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html", "<dl>\n" +
        "<dt><span class=\"throwsLabel\">Throws:</span>" +
                 "</dt>\n" +
                 "<dd><code>" +
                 "java.io.IOException</code></dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span>" +
                 "</dt>\n" +
                 "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>C1.setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;<span class=\"deprecationComment\">As of JDK version 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "<div class=\"block\">This field indicates whether the C1 is " +
                 "undecorated.</div>\n" +
                 "&nbsp;\n" +
                 "<dl>\n" +
                 "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n" +
                 "<dd>1.4</dd>\n" +
                 "<dt><span class=\"seeLabel\">See Also:</span>" +
                 "</dt>\n" +
                 "<dd><a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>C1.setUndecorated(boolean)</code></a></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;<span class=\"deprecationComment\">As of JDK version 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "<div class=\"block\">Reads the object stream.</div>\n" +
                 "<dl>\n" +
                 "<dt><span class=\"throwsLabel\">Throws:" +
                 "</span></dt>\n" +
                 "<dd><code><code>" +
                 "IOException</code></code></dd>\n" +
                 "<dd><code>java.io.IOException</code></dd>\n" +
                 "</dl>"},
        {BUG_ID + "/serialized-form.html",
                 "<span class=\"deprecatedLabel\">Deprecated.</span>" +
                 "&nbsp;</div>\n" +
                 "<div class=\"block\">" +
                 "The name for this class.</div>"}};

    // Test with -nocomment and -nodeprecated options. The ClassDocs whould
    // not display definition lists for any member details.
    private static final String[][] TEST_NOCMNT_NODEPR = {
        {BUG_ID + "/pkg1/C1.html",
                 "<pre>public&nbsp;void&nbsp;readObject()\n" +
                 "                throws java.io.IOException</pre>\n" +
                 "</li>"},
        {BUG_ID + "/pkg1/C2.html", "<pre>public&nbsp;C2()</pre>\n" +
                 "</li>"},
        {BUG_ID + "/pkg1/C1.ModalExclusionType.html", "<pre>public " +
                 "static final&nbsp;<a href=\"../pkg1/C1.ModalExclusionType.html\" " +
                 "title=\"enum in pkg1\">C1.ModalExclusionType</a> " +
                 "APPLICATION_EXCLUDE</pre>\n" +
                 "</li>"},
        {BUG_ID + "/serialized-form.html", "<pre>boolean " +
                 "undecorated</pre>\n" +
                 "<div class=\"block\"><span class=\"deprecatedLabel\">" +
                 "Deprecated.</span>&nbsp;<span class=\"deprecationComment\">As of JDK version 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\"><code>" +
                 "setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "</li>"},
        {BUG_ID + "/serialized-form.html", "<span class=\"deprecatedLabel\">" +
                 "Deprecated.</span>&nbsp;<span class=\"deprecationComment\">As of JDK version" +
                 " 1.5, replaced by\n" +
                 " <a href=\"pkg1/C1.html#setUndecorated-boolean-\">" +
                 "<code>setUndecorated(boolean)</code></a>.</span></div>\n" +
                 "</li>"}};

    // Test for valid HTML generation which should not comprise of empty
    // definition list tags.
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + "/pkg1/package-summary.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/package-summary.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C1.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C1.ModalExclusionType.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C1.ModalExclusionType.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C2.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C2.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C2.ModalType.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C2.ModalType.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C3.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C3.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C4.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C4.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/pkg1/C5.html", "<dl></dl>"},
        {BUG_ID + "/pkg1/C5.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/overview-tree.html", "<dl></dl>"},
        {BUG_ID + "/overview-tree.html", "<dl>\n" +
        "</dl>"},
        {BUG_ID + "/serialized-form.html", "<dl></dl>"},
        {BUG_ID + "/serialized-form.html", "<dl>\n" +
        "</dl>"}};

    private static final String[] ARGS1 =
        new String[] {
            "-Xdoclint:none", "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};

    private static final String[] ARGS2 =
        new String[] {
            "-Xdoclint:none", "-d", BUG_ID, "-nocomment", "-sourcepath",
            SRC_DIR, "pkg1"};

    private static final String[] ARGS3 =
        new String[] {
            "-Xdoclint:none", "-d", BUG_ID, "-nodeprecated", "-sourcepath",
            SRC_DIR, "pkg1"};

    private static final String[] ARGS4 =
        new String[] {
            "-Xdoclint:none", "-d", BUG_ID, "-nocomment", "-nodeprecated",
            "-sourcepath", SRC_DIR, "pkg1"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlDefinitionListTag tester = new TestHtmlDefinitionListTag();
        run(tester, ARGS1, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS1, TEST_CMNT_DEPR, NEGATED_TEST);
        run(tester, ARGS2, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS2, NO_TEST, TEST_CMNT_DEPR);
        run(tester, ARGS3, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS3, TEST_NODEPR, TEST_NOCMNT_NODEPR);
        run(tester, ARGS4, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS4, TEST_NOCMNT_NODEPR, TEST_CMNT_DEPR);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
