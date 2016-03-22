/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4263768 4785453
 * @summary Verify that the compiler does not crash when java.lang is not
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build ToolBox
 * @run main NoJavaLangTest
 */

import java.nio.file.*;

public class NoJavaLangTest {

    private static final String noJavaLangSrc =
        "public class NoJavaLang {\n" +
        "    private String s;\n" +
        "\n" +
        "    public String s() {\n" +
        "        return s;\n" +
        "    }\n" +
        "}";

    private static final String compilerErrorMessage =
        "Fatal Error: Unable to find package java.lang in classpath or bootclasspath";

    public static void main(String[] args) throws Exception {
        new NoJavaLangTest().run();
    }

    final ToolBox tb = new ToolBox();

    void run() throws Exception {
        testStandard();
        testBootClassPath();
        testModulePath();
    }

    // sanity check, with java.lang available
    void testStandard() {
        tb.new JavacTask()
                .sources(noJavaLangSrc)
                .run();
    }


    // test with bootclasspath, for as long as its around
    void testBootClassPath() {
        String[] bcpOpts = { "-Xlint:-options", "-source", "8", "-bootclasspath", "." };
        test(bcpOpts, compilerErrorMessage);
    }

    // test with module path
    void testModulePath() throws Exception {
        // need to ensure there is an empty java.base to avoid different error message
        Files.createDirectories(Paths.get("modules/java.base"));
        tb.new JavacTask()
                .sources("module java.base { }")
                .outdir("modules/java.base")
                .run();

        // ideally we'd have a better message for this case
        String[] mpOpts = { "-system", "none", "-modulepath", "modules" };
        test(mpOpts, compilerErrorMessage);
    }

    private void test(String[] options, String expect) {
        System.err.println("Testing " + java.util.Arrays.toString(options));

        String out = tb.new JavacTask()
                .options(options)
                .sources(noJavaLangSrc)
                .run(ToolBox.Expect.FAIL, 3)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!out.trim().equals(expect)) {
            throw new AssertionError("javac generated error output is not correct");
        }

        System.err.println("OK");
    }

}
