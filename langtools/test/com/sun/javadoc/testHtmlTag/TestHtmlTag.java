/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6786682
 * @summary This test verifies the use of lang attribute by <HTML>.
 * @author Bhavesh Patel
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestHtmlTag
 */

import java.util.Locale;

public class TestHtmlTag extends JavadocTester {
    private static final String defaultLanguage = Locale.getDefault().getLanguage();
    public static void main(String... args) throws Exception {
        TestHtmlTag tester = new TestHtmlTag();
        tester.runTests();
    }
    @Test
    void test_default() {
        javadoc("-locale", defaultLanguage,
                "-d", "out-default",
                "-sourcepath", testSrc,
                "pkg1");

        checkExit(Exit.OK);

        checkOutput("pkg1/C1.html", true,
            "<html lang=\"" + defaultLanguage + "\">");

        checkOutput("pkg1/package-summary.html", true,
            "<html lang=\"" + defaultLanguage + "\">");

        checkOutput("pkg1/C1.html", false,
                "<html>");
    }

    @Test
    void test_ja() {
        // TODO: why does this test need/use pkg2; why can't it use pkg1
        // like the other two tests, so that we can share the check methods?
        javadoc("-locale", "ja",
                "-d", "out-ja",
                "-sourcepath", testSrc,
                "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg2/C2.html", true,
                "<html lang=\"ja\">");

        checkOutput("pkg2/package-summary.html", true,
                "<html lang=\"ja\">");

        checkOutput("pkg2/C2.html", false,
                "<html>");
    }

    @Test
    void test_en_US() {
        javadoc("-locale", "en_US",
                "-d", "out-en_US",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/C1.html", true,
                "<html lang=\"en\">");

        checkOutput("pkg1/package-summary.html", true,
                "<html lang=\"en\">");

        checkOutput("pkg1/C1.html", false,
                "<html>");
    }
}
