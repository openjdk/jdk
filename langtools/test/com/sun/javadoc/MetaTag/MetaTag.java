/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4034096 4764726 6235799
 * @summary  Add support for HTML keywords via META tag for
 *           class and member names to improve API search
 * @author   dkramer
 * @library ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main MetaTag
 */

import java.text.SimpleDateFormat;
import java.util.Date;

public class MetaTag extends JavadocTester {

    /**
     * The entry point of the test.
     * @param args the array of command line arguments
     * @throws Exception if the test fails
     */
    public static void main(String... args) throws Exception {
        MetaTag tester = new MetaTag();
        tester.runTests();
    }

    @Test
    void testStandard() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "-keywords",
                "-doctitle", "Sample Packages",
                "p1", "p2");

        checkExit(Exit.OK);

        checkOutput("p1/C1.html", true,
                "<meta name=\"keywords\" content=\"p1.C1 class\">",
                "<meta name=\"keywords\" content=\"field1\">",
                "<meta name=\"keywords\" content=\"field2\">",
                "<meta name=\"keywords\" content=\"method1()\">",
                "<meta name=\"keywords\" content=\"method2()\">");

        checkOutput("p1/package-summary.html", true,
                "<meta name=\"keywords\" content=\"p1 package\">");

        checkOutput("overview-summary.html", true,
                "<meta name=\"keywords\" content=\"Overview, Sample Packages\">");

        // NOTE: Hopefully, this regression test is not run at midnight.  If the output
        // was generated yesterday and this test is run today, the test will fail.
        checkOutput("overview-summary.html", true,
                "<meta name=\"date\" content=\"" + date() + "\">");
    }

    @Test
    void testNoTimestamp() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "-notimestamp",
                "-doctitle", "Sample Packages",
                "p1", "p2");
        checkExit(Exit.OK);

        // No keywords when -keywords is not used.
        checkOutput("p1/C1.html", false,
                "<META NAME=\"keywords\" CONTENT=\"p1.C1 class\">",
                "<META NAME=\"keywords\" CONTENT=\"field1\">",
                "<META NAME=\"keywords\" CONTENT=\"field2\">",
                "<META NAME=\"keywords\" CONTENT=\"method1()\">",
                "<META NAME=\"keywords\" CONTENT=\"method2()\">");

        checkOutput("p1/package-summary.html", false,
                "<META NAME=\"keywords\" CONTENT=\"p1 package\">");

        checkOutput("overview-summary.html", false,
                "<META NAME=\"keywords\" CONTENT=\"Overview Summary, Sample Packages\">");

        // The date metatag should not show up when -notimestamp is used.
        // NOTE: Hopefully, this regression test is not run at midnight.  If the output
        // was generated yesterday and this test is run today, the test will fail.
        checkOutput("overview-summary.html", false,
                "<META NAME=\"date\" CONTENT=\"" + date() + "\">");
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    String date() {
        return dateFormat.format(new Date());
    }
}
