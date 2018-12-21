/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8194955 8182765
 * @summary Warn when default HTML version is used.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestHtmlWarning
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javadoc.tester.JavadocTester;

public class TestHtmlWarning extends JavadocTester {

    public static void main(String... args) throws Exception {
        Files.write(testFile,
                List.of("/** Comment. */", "public class C { }"));

        TestHtmlWarning tester = new TestHtmlWarning();
        tester.runTests();
    }

    private static final Path testFile = Paths.get("C.java");
    private static final String warning
            = "javadoc: warning - You have specified the HTML version as HTML 4.01 by using the -html4 option.\n"
            + "The default is currently HTML5 and the support for HTML 4.01 will be removed\n"
            + "in a future release. To suppress this warning, please ensure that any HTML constructs\n"
            + "in your comments are valid in HTML5, and remove the -html4 option.";

    @Test
    public void testHtml4() {
        javadoc("-d", "out-4",
                "-html4",
                testFile.toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true, warning);
    }

    @Test
    public void testHtml5() {
        javadoc("-d", "out-5",
                "-html5",
                testFile.toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, false, warning);
    }

    @Test
    public void testDefault() {
        javadoc("-d", "out-default",
                testFile.toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, false, warning);
    }
}
