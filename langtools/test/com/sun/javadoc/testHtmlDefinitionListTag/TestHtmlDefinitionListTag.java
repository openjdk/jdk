/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6786690 6820360
 * @summary This test verifies the nesting of definition list tags.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestHtmlDefinitionListTag
 * @run main TestHtmlDefinitionListTag
 */

public class TestHtmlDefinitionListTag extends JavadocTester {

    private static final String BUG_ID = "6786690-6820360";

    // Test common to all runs of javadoc. The class signature should print
    // properly enclosed definition list tags and the Annotation Type
    // Optional Element should print properly nested definition list tags
    // for default value.
    private static final String[][] TEST_ALL = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<PRE>public class " +
                 "<STRONG>C1</STRONG>" + NL + "extends " +
                 "java.lang.Object" + NL + "implements " +
                 "java.io.Serializable</PRE>"},
        {BUG_ID + FS + "pkg1" + FS + "C4.html", "<DL>" + NL + "<DD><DL>" + NL +
                 "<DT><STRONG>Default:</STRONG></DT><DD>true</DD>" + NL +
                 "</DL>" + NL + "</DD>" + NL + "</DL>"}};

    // Test for normal run of javadoc in which various ClassDocs and
    // serialized form should have properly nested definition list tags
    // enclosing comments, tags and deprecated information.
    private static final String[][] TEST_CMNT_DEPR = {
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<DL>" + NL +
                 "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>JDK1.0</DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>JDK1.0</DD>" + NL + "<DT><STRONG>See Also:</STRONG></DT><DD>" +
                 "<A HREF=\"../pkg1/C2.html\" title=\"class in pkg1\">" +
                 "<CODE>C2</CODE></A>, " + NL +
                 "<A HREF=\"../serialized-form.html#pkg1.C1\">" +
                 "Serialized Form</A></DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I></DD>" +
                 "<DD>This field indicates whether the C1 is undecorated." + NL +
                 "<P>" + NL + "</DD>" + NL + "<DD><DL>" + NL + "<DT><STRONG>" +
                 "Since:</STRONG></DT>" + NL + "  <DD>1.4</DD>" + NL + "<DT>" +
                 "<STRONG>See Also:</STRONG></DT><DD>" +
                 "<A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\"><CODE>" +
                 "setUndecorated(boolean)</CODE></A></DD></DL>" + NL +"</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DD>Constructor." + NL + "<P>" + NL + "</DD>" + NL +
                 "<DD><DL>" + NL + "<DT><STRONG>Parameters:</STRONG></DT><DD>" +
                 "<CODE>title</CODE> - the title</DD><DD><CODE>test</CODE>" +
                 " - boolean value</DD>" + NL + "<DT><STRONG>Throws:</STRONG></DT>" + NL +
                 "<DD><CODE>java.lang.IllegalArgumentException</CODE>" +
                 " - if the <code>owner</code>'s" + NL + "     <code>GraphicsConfiguration" +
                 "</code> is not from a screen device</DD>" + NL +"<DD><CODE>" +
                 "HeadlessException</CODE></DD></DL>" + NL + "</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DD>Method comments." + NL + "<P>" + NL +
                 "</DD>" + NL + "<DD><DL>" + NL + "<DT><STRONG>Parameters:" +
                 "</STRONG></DT><DD><CODE>undecorated</CODE> - <code>true</code>" +
                 " if no decorations are" + NL + "         to be enabled;" + NL +
                 "         <code>false</code> if decorations are to be enabled." +
                 "</DD><DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>1.4</DD>" + NL + "<DT><STRONG>See Also:</STRONG></DT>" +
                 "<DD><A HREF=\"../pkg1/C1.html#readObject()\"><CODE>" +
                 "readObject()</CODE></A></DD></DL>" + NL + "</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL + "<DD><DL>" + NL +
                 "<DT><STRONG>Throws:</STRONG></DT>" + NL + "<DD><CODE>" +
                 "java.io.IOException</CODE></DD><DT><STRONG>See Also:" +
                 "</STRONG></DT><DD>" +
                 "<A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.ModalExclusionType.html", "<DL>" + NL +
                 "<DD>No modal exclusion." + NL + "<P>" + NL +"</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL>" + NL + "<DD>Constructor." + NL +
                 "<P>" + NL +"</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version 1.5, replaced " +
                 "by" + NL + " <A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD><DD>Set visible." + NL + "<P>" + NL + "</DD>" +NL +
                 "<DD><DL>" + NL + "<DT><STRONG>Parameters:</STRONG></DT><DD>" +
                 "<CODE>set</CODE> - boolean</DD><DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>1.4</DD></DL>" + NL + "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C3.html", "<DL>" + NL + "<DD>Comment." + NL +
                 "<P>" + NL + "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL + "<DD><DL>" + NL +
                 "<DT><STRONG>Throws:</STRONG></DT>" + NL + "<DD><CODE>" +
                 "java.io.IOException</CODE></DD><DT><STRONG>See Also:</STRONG>" +
                 "</DT><DD><A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>C1.setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version " +
                 "1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I></DD>" +
                 "<DD>This field indicates whether the C1 is undecorated." + NL +
                 "<P>" + NL + "</DD>" + NL + "<DD>&nbsp;</DD>" + NL +
                 "<DD><DL>" + NL + "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>1.4</DD>" + NL + "<DT><STRONG>See Also:</STRONG>" +
                 "</DT><DD><A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>C1.setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD><DD>Reads the object stream." + NL + "<P>" + NL +
                 "</DD>" + NL + "<DD><DL>" + NL + "<DT><STRONG>Throws:" +
                 "</STRONG></DT>" + NL + "<DD><CODE><code>" +
                 "IOException</code></CODE></DD>" + NL +
                 "<DD><CODE>java.io.IOException</CODE></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;</DD><DD>" +
                 "The name for this class." + NL + "<P>" + NL + "</DD>" + NL +
                 "<DD>&nbsp;</DD>" + NL + "</DL>"}};

    // Test with -nocomment option. The ClassDocs and serialized form should
    // have properly nested definition list tags enclosing deprecated
    // information and should not display definition lists for comments
    // and tags.
    private static final String[][] TEST_NOCMNT = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version 1.5, replaced by" + NL +
                 " <A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\"><CODE>" +
                 "setUndecorated(boolean)</CODE></A>.</I></DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C5.html", "<PRE>" + NL +
                 "protected <STRONG>C5</STRONG>()</PRE>" + NL + "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;</DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C5.html", "<PRE>" + NL +
                 "public void <STRONG>printInfo</STRONG>()</PRE>" + NL + "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;</DD></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<PRE>" + NL + "boolean <STRONG>" +
                 "undecorated</STRONG></PRE>" + NL + "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\"><CODE>" +
                 "setUndecorated(boolean)</CODE></A>.</I></DD></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<PRE>" + NL + "int <STRONG>" +
                 "publicKey</STRONG></PRE>" + NL + "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;</DD></DL>"}};

    // Test with -nodeprecated option. The ClassDocs should have properly nested
    // definition list tags enclosing comments and tags. The ClassDocs should not
    // display definition list for deprecated information. The serialized form
    // should display properly nested definition list tags for comments, tags
    // and deprecated information.
    private static final String[][] TEST_NODEPR = {
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<DL>" + NL +
                 "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>JDK1.0</DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>JDK1.0</DD>" + NL + "<DT><STRONG>See Also:</STRONG></DT><DD>" +
                 "<A HREF=\"../pkg1/C2.html\" title=\"class in pkg1\">" +
                 "<CODE>C2</CODE></A>, " + NL +
                 "<A HREF=\"../serialized-form.html#pkg1.C1\">" +
                 "Serialized Form</A></DD></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DD>Constructor." + NL + "<P>" + NL + "</DD>" + NL +
                 "<DD><DL>" + NL + "<DT><STRONG>Parameters:</STRONG></DT><DD>" +
                 "<CODE>title</CODE> - the title</DD><DD><CODE>test</CODE>" +
                 " - boolean value</DD>" + NL + "<DT><STRONG>Throws:</STRONG></DT>" + NL +
                 "<DD><CODE>java.lang.IllegalArgumentException</CODE>" +
                 " - if the <code>owner</code>'s" + NL + "     <code>GraphicsConfiguration" +
                 "</code> is not from a screen device</DD>" + NL +"<DD><CODE>" +
                 "HeadlessException</CODE></DD></DL>" + NL + "</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL +
                 "<DD>Method comments." + NL + "<P>" + NL +
                 "</DD>" + NL + "<DD><DL>" + NL + "<DT><STRONG>Parameters:" +
                 "</STRONG></DT><DD><CODE>undecorated</CODE> - <code>true</code>" +
                 " if no decorations are" + NL + "         to be enabled;" + NL +
                 "         <code>false</code> if decorations are to be enabled." +
                 "</DD><DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>1.4</DD>" + NL + "<DT><STRONG>See Also:</STRONG></DT>" +
                 "<DD><A HREF=\"../pkg1/C1.html#readObject()\"><CODE>" +
                 "readObject()</CODE></A></DD></DL>" + NL + "</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL + "<DD><DL>" + NL +
                 "<DT><STRONG>Throws:</STRONG></DT>" + NL + "<DD><CODE>" +
                 "java.io.IOException</CODE></DD><DT><STRONG>See Also:" +
                 "</STRONG></DT><DD>" +
                 "<A HREF=\"../pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.ModalExclusionType.html", "<DL>" + NL +
                 "<DD>No modal exclusion." + NL + "<P>" + NL +"</DD>" + NL +
                 "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL>" + NL + "<DD>Constructor." + NL +
                 "<P>" + NL +"</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C3.html", "<DL>" + NL + "<DD>Comment." + NL +
                 "<P>" + NL + "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL + "<DD><DL>" + NL +
                 "<DT><STRONG>Throws:</STRONG></DT>" + NL + "<DD><CODE>" +
                 "java.io.IOException</CODE></DD><DT><STRONG>See Also:</STRONG>" +
                 "</DT><DD><A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>C1.setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version " +
                 "1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I></DD>" +
                 "<DD>This field indicates whether the C1 is undecorated." + NL +
                 "<P>" + NL + "</DD>" + NL + "<DD>&nbsp;</DD>" + NL +
                 "<DD><DL>" + NL + "<DT><STRONG>Since:</STRONG></DT>" + NL +
                 "  <DD>1.4</DD>" + NL + "<DT><STRONG>See Also:</STRONG>" +
                 "</DT><DD><A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>C1.setUndecorated(boolean)</CODE></A></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD><DD>Reads the object stream." + NL + "<P>" + NL +
                 "</DD>" + NL + "<DD><DL>" + NL + "<DT><STRONG>Throws:" +
                 "</STRONG></DT>" + NL + "<DD><CODE><code>" +
                 "IOException</code></CODE></DD>" + NL +
                 "<DD><CODE>java.io.IOException</CODE></DD></DL>" + NL +
                 "</DD>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL +
                 "<DD><STRONG>Deprecated.</STRONG>&nbsp;</DD><DD>" +
                 "The name for this class." + NL + "<P>" + NL + "</DD>" + NL +
                 "<DD>&nbsp;</DD>" + NL + "</DL>"}};

    // Test with -nocomment and -nodeprecated options. The ClassDocs whould
    // not display definition lists for any member details. The serialized
    // form should display properly nested definition list tags for
    // deprecated information only.
    private static final String[][] TEST_NOCMNT_NODEPR = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<PRE>" + NL + "public void " +
                 "<STRONG>readObject</STRONG>()" + NL + "                throws" +
                 " java.io.IOException</PRE>" + NL + "<HR>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<PRE>" +NL + "public <STRONG>" +
                 "C2</STRONG>()</PRE>" + NL + "<HR>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.ModalExclusionType.html", "<PRE>" + NL +
                 "public static final " +
                 "<A HREF=\"../pkg1/C1.ModalExclusionType.html\" " +
                 "title=\"enum in pkg1\">C1.ModalExclusionType</A> <STRONG>" +
                 "APPLICATION_EXCLUDE</STRONG></PRE>" + NL + "<HR>"},
        {BUG_ID + FS + "serialized-form.html", "<PRE>" + NL + "boolean <STRONG>" +
                 "undecorated</STRONG></PRE>" + NL + "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\"><CODE>" +
                 "setUndecorated(boolean)</CODE></A>.</I></DD></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;<I>As of JDK version" +
                 " 1.5, replaced by" + NL +
                 " <A HREF=\"pkg1/C1.html#setUndecorated(boolean)\">" +
                 "<CODE>setUndecorated(boolean)</CODE></A>.</I>" + NL + "<P>" + NL +
                 "</DD></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<PRE>" + NL + "int <STRONG>" +
                 "publicKey</STRONG></PRE>" + NL + "<DL>" + NL + "<DD><STRONG>" +
                 "Deprecated.</STRONG>&nbsp;</DD></DL>"}};

    // Test for valid HTML generation which should not comprise of empty
    // definition list tags.
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.ModalExclusionType.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.ModalExclusionType.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.ModalType.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C2.ModalType.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C3.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C3.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C4.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C4.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C5.html", "<DL></DL>"},
        {BUG_ID + FS + "pkg1" + FS + "C5.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "overview-tree.html", "<DL></DL>"},
        {BUG_ID + FS + "overview-tree.html", "<DL>" + NL + "</DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL></DL>"},
        {BUG_ID + FS + "serialized-form.html", "<DL>" + NL + "</DL>"}};

    private static final String[] ARGS1 =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};

    private static final String[] ARGS2 =
        new String[] {
            "-d", BUG_ID, "-nocomment", "-sourcepath", SRC_DIR, "pkg1"};

    private static final String[] ARGS3 =
        new String[] {
            "-d", BUG_ID, "-nodeprecated", "-sourcepath", SRC_DIR, "pkg1"};

    private static final String[] ARGS4 =
        new String[] {
            "-d", BUG_ID, "-nocomment", "-nodeprecated", "-sourcepath", SRC_DIR, "pkg1"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlDefinitionListTag tester = new TestHtmlDefinitionListTag();
        run(tester, ARGS1, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS1, TEST_CMNT_DEPR, NEGATED_TEST);
        run(tester, ARGS2, TEST_ALL, NEGATED_TEST);
        run(tester, ARGS2, TEST_NOCMNT, TEST_CMNT_DEPR);
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
