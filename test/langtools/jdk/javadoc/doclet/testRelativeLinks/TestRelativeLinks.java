/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4460354 8014636 8043186 8195805 8182765 8196202
 * @summary  Test to make sure that relative paths are redirected in the
 *           output so that they are not broken.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestRelativeLinks
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javadoc.tester.JavadocTester;

public class TestRelativeLinks extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestRelativeLinks tester = new TestRelativeLinks();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-use",
                "-sourcepath", testSrc,
                "pkg", "pkg2");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "attribute not supported in HTML5: name");

        // These relative paths should stay relative because they appear
        // in the right places.
        checkOutput("pkg/C.html", true,
            """
                <a href="relative-class-link.html">relative class link</a>""",
            """
                <a href="relative-field-link.html">relative field link</a>""",
            """
                <a href="relative-method-link.html">relative method link</a>""",
            """
                \s<a href="relative-multi-line-link.html">relative-multi-line-link</a>.""");
        checkOutput("pkg/package-summary.html", true,
            """
                <a href="relative-package-link.html">relative package link</a>""");

        // These relative paths should be redirected because they are in different
        // places.

        // INDEX PAGE
        checkOutput("index-all.html", true,
            """
                <a href="./pkg/relative-class-link.html">relative class link</a>""",
            """
                <a href="./pkg/relative-field-link.html">relative field link</a>""",
            """
                <a href="./pkg/relative-method-link.html">relative method link</a>""",
            """
                <a href="./pkg/relative-package-link.html">relative package link</a>""",
            """
                \s<a href="./pkg/relative-multi-line-link.html">relative-multi-line-link</a>.""");

        // This is not a relative path and should not be redirected.
        checkOutput("index-all.html", true,
            """
                <div class="block"><a name="masters"></a>""");
        checkOutput("index-all.html", false,
            """
                <div class="block"><a name="./pkg/masters"></a>""");

        // PACKAGE USE
        checkOutput("pkg/package-use.html", true,
            """
                <a href="../pkg/relative-package-link.html">relative package link</a>.""",
            """
                <a href="../pkg/relative-class-link.html">relative class link</a>""");

        // CLASS_USE
        checkOutput("pkg/class-use/C.html", true,
            """
                <a href="../../pkg/relative-field-link.html">relative field link</a>""",
            """
                <a href="../../pkg/relative-method-link.html">relative method link</a>""",
            """
                <a href="../../pkg/relative-package-link.html">relative package link</a>""",
            """
                \s<a href="../../pkg/relative-multi-line-link.html">relative-multi-line-link</a>.""");

        // PACKAGE OVERVIEW
        checkOutput("index.html", true,
            """
                <a href="./pkg/relative-package-link.html">relative package link</a>""");
    }

    private void touch(String file) {
        File f = new File(outputDir, file);
        out.println("touch " + f);
        try (FileOutputStream fos = new FileOutputStream(f)) {
        } catch (IOException e) {
            checking("Touch file");
            failed("Error creating file: " + e);
        }
    }
}
