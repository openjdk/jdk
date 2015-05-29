/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4846262
 * @summary check that javac operates correctly in EBCDIC locale
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 * @ignore 8081538 test CheckEBCDICLocaleTest is failing
 * @build ToolBox
 * @run main CheckEBCDICLocaleTest
 */

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class CheckEBCDICLocaleTest {

    private static final String TestSrc =
        "public class Test {\n" +
        "    public void test() {\n" +
        "        abcdefg\n" +
        "    }\n" +
        "}";

    private static final String TestOutTemplate =
        "output%1$sTest.java:3: error: not a statement\n" +
        "        abcdefg\n" +
        "        ^\n" +
        "output%1$sTest.java:3: error: ';' expected\n" +
        "        abcdefg\n" +
        "               ^\n" +
        "2 errors\n";

    public static void main(String[] args) throws Exception {
        new CheckEBCDICLocaleTest().test();
    }

    public void test() throws Exception {
        ToolBox tb = new ToolBox();
        tb.writeFile("Test.java", TestSrc);
        tb.createDirectories("output");

        Native2Ascii n2a = new Native2Ascii(Charset.forName("IBM1047"));
        n2a.asciiToNative(Paths.get("Test.java"), Paths.get("output", "Test.java"));

        tb.new JavacTask(ToolBox.Mode.EXEC)
                .redirect(ToolBox.OutputKind.STDERR, "Test.tmp")
                .options("-J-Duser.language=en",
                        "-J-Duser.region=US",
                        "-J-Dfile.encoding=IBM1047")
                .files("output/Test.java")
                .run(ToolBox.Expect.FAIL);

        n2a.nativeToAscii(Paths.get("Test.tmp"), Paths.get("Test.out"));

        List<String> expectLines = Arrays.asList(
                String.format(TestOutTemplate, File.separator).split("\n"));
        List<String> actualLines = Files.readAllLines(Paths.get("Test.out"));
        try {
            tb.checkEqual(expectLines, actualLines);
        } catch (Throwable tt) {
            PrintStream out = tb.out;
            out.println("Output mismatch:");

            out.println("Expected output:");
            for (String s: expectLines) {
                out.println(s);
            }
            out.println();

            out.println("Actual output:");
            for (String s : actualLines) {
                out.println(s);
            }
            out.println();

            throw tt;
        }
    }
}
