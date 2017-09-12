/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug  8157349
 * @summary  test copy of doc-files
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestCopyFiles
 */

public class TestCopyFiles extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestCopyFiles tester = new TestCopyFiles();
        tester.runTests();
    }

    @Test
    void testDocFilesInModules() {
        javadoc("-d", "modules-out",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle");
        checkExit(Exit.OK);
        checkOutput("p/doc-files/inpackage.html", true,
                "In a named module and named package"
        );
    }

    @Test
    void testDocFilesInPackages() {
        javadoc("-d", "packages-out",
                "-sourcepath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);
        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );
    }

    @Test
    void testDocFilesInUnnamedPackages() {
        javadoc("-d", "unnamed-out",
                "-sourcepath", testSrc("unnamed"),
                testSrc("unnamed/Foo.java")
        );
        checkExit(Exit.OK);
        checkOutput("doc-files/inpackage.html", true,
                "In an unnamed package"
        );
    }

    @Test
    void testDocFilesInPackagesSource7() {
        javadoc("-d", "packages-out-src7",
                "-source", "7",
                "-sourcepath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);
        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );
    }

    @Test
    void testDocFilesInPackagesSource7UsingClassPath() {
        javadoc("-d", "packages-out-src7-cp",
                "-source", "7",
                "-classpath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);
        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );
    }
}
