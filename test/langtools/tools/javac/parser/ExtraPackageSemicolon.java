/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8305671
 * @summary Verify an extra semicolon is allowed after package decl with no imports
 * @modules jdk.compiler/com.sun.tools.javac.code
*/

import com.sun.tools.javac.Main;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExtraPackageSemicolon {

    public static void runTest(String filename, String source) throws Exception {
        final File sourceFile = new File(filename);
        System.err.println("writing: " + sourceFile);
        try (PrintStream output = new PrintStream(new FileOutputStream(sourceFile))) {
            output.println(source);
        }
        final StringWriter diags = new StringWriter();
        final String[] params = new String[] { sourceFile.toString() };
        System.err.println("compiling: " + sourceFile);
        int ret = Main.compile(params, new PrintWriter(diags, true));
        System.err.println("exit value: " + ret);
        String output = diags.toString().trim();
        if (!output.isEmpty())
            System.err.println("output:\n" + output);
        else
            System.err.println("no output");
        if (ret != 0)
            throw new AssertionError("compilation failed, but expected success");
    }

    public static void main(String... args) throws Exception {
        runTest("Test1.java", "package p;");
        runTest("Test2.java", "package p;;");
        runTest("Test3.java", "package p;; ;; ;; ;;; ;;; ;;; ;;");
    }
}
