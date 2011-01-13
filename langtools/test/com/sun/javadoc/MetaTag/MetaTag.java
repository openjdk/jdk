/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.text.SimpleDateFormat;
import java.util.Date;

import com.sun.tools.doclets.formats.html.ConfigurationImpl;
import com.sun.tools.doclets.internal.toolkit.Configuration;

/*
 * @test
 * @bug      4034096 4764726 6235799
 * @summary  Add support for HTML keywords via META tag for
 *           class and member names to improve API search
 * @author   dkramer
 * @library  ../lib/
 * @build    JavadocTester
 * @build    MetaTag
 * @run main MetaTag
 */

public class MetaTag extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4034096-4764726-6235799";
    private static final String OUTPUT_DIR = "docs-" + BUG_ID;
    private static final SimpleDateFormat m_dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR,
        "-sourcepath", SRC_DIR,
        "-keywords",
        "-doctitle", "Sample Packages",
        "p1", "p2"
    };

    private static final String[] ARGS_NO_TIMESTAMP_NO_KEYWORDS = new String[] {
        "-d", OUTPUT_DIR + "-2",
        "-sourcepath", SRC_DIR,
        "-notimestamp",
        "-doctitle", "Sample Packages",
        "p1", "p2"
    };

    //Input for string search tests.
    private static final String[][] TEST = {

        { OUTPUT_DIR + FS + "p1" + FS + "C1.html",
           "<meta name=\"keywords\" content=\"p1.C1 class\">" },

        { OUTPUT_DIR + FS + "p1" + FS + "C1.html",
           "<meta name=\"keywords\" content=\"field1\">" },

        { OUTPUT_DIR + FS + "p1" + FS + "C1.html",
           "<meta name=\"keywords\" content=\"field2\">" },

        { OUTPUT_DIR + FS + "p1" + FS + "C1.html",
           "<meta name=\"keywords\" content=\"method1()\">" },

        { OUTPUT_DIR + FS + "p1" + FS + "C1.html",
           "<meta name=\"keywords\" content=\"method2()\">" },

        { OUTPUT_DIR + FS + "p1" + FS + "package-summary.html",
           "<meta name=\"keywords\" content=\"p1 package\">" },

        { OUTPUT_DIR + FS + "overview-summary.html",
           "<meta name=\"keywords\" content=\"Overview, Sample Packages\">" },

        //NOTE: Hopefully, this regression test is not run at midnight.  If the output
        //was generated yesterday and this test is run today, the test will fail.
        {OUTPUT_DIR + FS + "overview-summary.html",
           "<meta name=\"date\" "
                            + "content=\"" + m_dateFormat.format(new Date()) + "\">"},
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    private static final String[][] TEST2 = NO_TEST;
    private static final String[][] NEGATED_TEST2 = {
        //No keywords when -keywords is not used.
        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "C1.html",
           "<META NAME=\"keywords\" CONTENT=\"p1.C1 class\">" },

        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "C1.html",
           "<META NAME=\"keywords\" CONTENT=\"field1\">" },

        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "C1.html",
           "<META NAME=\"keywords\" CONTENT=\"field2\">" },

        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "C1.html",
           "<META NAME=\"keywords\" CONTENT=\"method1()\">" },

        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "C1.html",
           "<META NAME=\"keywords\" CONTENT=\"method2()\">" },

        { OUTPUT_DIR + "-2" + FS + "p1" + FS + "package-summary.html",
           "<META NAME=\"keywords\" CONTENT=\"p1 package\">" },

        { OUTPUT_DIR + "-2" + FS + "overview-summary.html",
           "<META NAME=\"keywords\" CONTENT=\"Overview Summary, Sample Packages\">" },

        //The date metatag should not show up when -notimestamp is used.

        //NOTE: Hopefully, this regression test is not run at midnight.  If the output
        //was generated yesterday and this test is run today, the test will fail.
        {OUTPUT_DIR + "-2" + FS + "overview-summary.html",
           "<META NAME=\"date\" "
                            + "CONTENT=\"" + m_dateFormat.format(new Date()) + "\">"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        MetaTag tester = new MetaTag();
        Configuration config = ConfigurationImpl.getInstance();
        boolean defaultKeywordsSetting = config.keywords;
        boolean defaultTimestampSetting = config.notimestamp;
        run(tester, ARGS, TEST, NEGATED_TEST);
        //Variable needs to be reset because Configuration is a singleton.
        config.keywords = defaultKeywordsSetting;
        config.notimestamp = defaultTimestampSetting;
        run(tester, ARGS_NO_TIMESTAMP_NO_KEYWORDS, TEST2, NEGATED_TEST2);
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
