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
 * @bug 6802694
 * @summary This test verifies deprecation info in serialized-form.html.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestSerializedFormDeprecationInfo
 * @run main TestSerializedFormDeprecationInfo
 */

public class TestSerializedFormDeprecationInfo extends JavadocTester {

    private static final String BUG_ID = "6802694";

    // Test for normal run of javadoc. The serialized-form.html should
    // display the inline comments, tags and deprecation information if any.
    private static final String[][] TEST_CMNT_DEPR = {
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

    // Test with -nocomment option. The serialized-form.html should
    // not display the inline comments and tags but should display deprecation
    // information if any.
    private static final String[][] TEST_NOCMNT = {
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

    // Test with -nodeprecated option. The serialized-form.html should
    // ignore the -nodeprecated tag and display the deprecation info. This
    // test is similar to the normal run of javadoc in which inline comment, tags
    // and deprecation information will be displayed.
    private static final String[][] TEST_NODEPR = TEST_CMNT_DEPR;

    // Test with -nodeprecated and -nocomment options. The serialized-form.html should
    // ignore the -nodeprecated tag and display the deprecation info but should not
    // display the inline comments and tags. This test is similar to the test with
    // -nocomment option.
    private static final String[][] TEST_NOCMNT_NODEPR = TEST_NOCMNT;

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
        TestSerializedFormDeprecationInfo tester = new TestSerializedFormDeprecationInfo();
        tester.exactNewlineMatch = false;
        run(tester, ARGS1, TEST_CMNT_DEPR, TEST_NOCMNT);
        run(tester, ARGS2, TEST_NOCMNT, TEST_CMNT_DEPR);
        run(tester, ARGS3, TEST_NODEPR, TEST_NOCMNT_NODEPR);
        run(tester, ARGS4, TEST_NOCMNT_NODEPR, TEST_NODEPR);
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
