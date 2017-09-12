/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     7091528 8029145 8037484
 * @summary ensures javadoc parses unique source files and ignores all class files
 * @modules jdk.javadoc/com.sun.tools.doclets.standard
 * @library /tools/javadoc/lib
 * @build ToyDoclet
 * @compile p/C1.java p/q/C2.java
 * @run main T7091528
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

public class T7091528 {
    public static void main(String... args) {
        new T7091528().run();
    }
    void run() {
        File testSrc = new File(System.getProperty("test.src"));
        File testClasses = new File(System.getProperty("test.classes"));
        // 7091528, tests if class files are being ignored
        runTest(
            "-sourcepath", testClasses + File.pathSeparator + testSrc,
            "-subpackages",
            "p");
        // 8029145, tests if unique source files are parsed
        runTest(
            "-sourcepath", testSrc.getAbsolutePath(),
            "-subpackages",
            "p:p.q");
        File testPkgDir = new File(testSrc, "p");
        File testFile = new File(testPkgDir, "C3.java");
        runTest(
            "-sourcepath", testSrc.getAbsolutePath(),
            testFile.getAbsolutePath(),
            "p");
        runTest(
            "-classpath", testSrc.getAbsolutePath(),
            testFile.getAbsolutePath(),
            "p");

    }
    void runTest(String... args) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javadoc.Main.execute("example", pw, pw, pw,
                "ToyDoclet", getClass().getClassLoader(), args);
        pw.close();

        String out = sw.toString();
        if (!out.isEmpty()) {
            System.err.println(out);
        }

        if (rc != 0)
            throw new Error("javadoc failed: exit code = " + rc);

        if (out.matches("(?s).*p/[^ ]+\\.class.*"))
            throw new Error("reading .class files");
    }
}
