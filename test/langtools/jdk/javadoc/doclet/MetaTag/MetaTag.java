/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4034096 4764726 6235799 8182765 8196202
 * @summary  Add support for HTML keywords via META tag for
 *           class and member names to improve API search
 * @author   dkramer
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
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
                "--frames",
                "-doctitle", "Sample Packages",
                "p1", "p2");

        checkExit(Exit.OK);

        checkMeta("dc.created", true);
    }

    @Test
    void testNoTimestamp() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "-notimestamp",
                "--frames",
                "-doctitle", "Sample Packages",
                "p1", "p2");
        checkExit(Exit.OK);

        // No keywords when -keywords is not used.
        checkMeta("dc.created", false);
    }

    @Test
    void testStandard_html4() {
        javadoc("-d", "out-1-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-keywords",
                "--frames",
                "-doctitle", "Sample Packages",
                "p1", "p2");

        checkExit(Exit.OK);

        checkMeta("date", true);
    }

    @Test
    void testNoTimestamp_html4() {
        javadoc("-d", "out-2-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-notimestamp",
                "--frames",
                "-doctitle", "Sample Packages",
                "p1", "p2");
        checkExit(Exit.OK);

        // No keywords when -keywords is not used.
        checkMeta("date", false);
    }

    void checkMeta(String metaNameDate, boolean found) {
        checkOutput("p1/C1.html", found,
                "<meta name=\"keywords\" content=\"p1.C1 class\">",
                "<meta name=\"keywords\" content=\"field1\">",
                "<meta name=\"keywords\" content=\"field2\">",
                "<meta name=\"keywords\" content=\"method1()\">",
                "<meta name=\"keywords\" content=\"method2()\">");

        checkOutput("p1/package-summary.html", found,
                "<meta name=\"keywords\" content=\"p1 package\">");

        checkOutput("overview-summary.html", found,
                "<meta name=\"keywords\" content=\"Overview, Sample Packages\">");

        // NOTE: Hopefully, this regression test is not run at midnight.  If the output
        // was generated yesterday and this test is run today, the test will fail.
        checkOutput("overview-summary.html", found,
                "<meta name=\"" + metaNameDate + "\" content=\"" + date() + "\">");
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    String date() {
        return dateFormat.format(new Date());
    }
}
