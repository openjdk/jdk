/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4491755 4785453
 * @summary Prob w/static inner class with same name as a regular class
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main InnerClassFileTest
 */

import java.nio.file.Paths;

//original test: test/tools/javac/innerClassFile/Driver.sh
public class InnerClassFileTest {

    private static final String BSrc =
        "package x;\n" +
        "\n" +
        "import x.*;\n" +
        "\n" +
        "public class B {\n" +
        "    public static class C {}\n" +
        "}";

    private static final String CSrc =
        "package x;\n" +
        "\n" +
        "import x.*;\n" +
        "\n" +
        "public class C {}";

    private static final String MainSrc =
        "package y;\n" +
        "\n" +
        "class Main {\n" +
        "        private R1 a;\n" +
        "        private R2 b;\n" +
        "        private R3 c;\n" +
        "}";

    private static final String R1Src =
        "package y;\n" +
        "\n" +
        "public final class R1 {\n" +
        "    x.B.C a = null;\n" +
        "    x.C b = null;\n" +
        "    R2 c = new R2();\n" +
        "}";

    private static final String R2Src =
        "package y;\n" +
        "\n" +
        "public final class R2 {\n" +
        "    x.B.C a = null;\n" +
        "    x.C b = null;\n" +
        "}";

    private static final String R3Src =
        "package y;\n" +
        "\n" +
        "public final class R3 {\n" +
        "    x.B.C a = null;\n" +
        "    x.C b = null;\n" +
        "    R1 c = new R1();\n" +
        "}";

    public static void main(String args[]) throws Exception {
        new InnerClassFileTest().run();
    }

    void run() throws Exception {
        createFiles();
        compileFiles();
    }

    void createFiles() throws Exception {
//        mkdir src
//        cp -r ${TESTSRC}${FS}* src
        ToolBox.createJavaFileFromSource(Paths.get("src"), BSrc);
        ToolBox.createJavaFileFromSource(Paths.get("src"), CSrc);
        ToolBox.createJavaFileFromSource(Paths.get("src"), MainSrc);
        ToolBox.createJavaFileFromSource(Paths.get("src"), R1Src);
        ToolBox.createJavaFileFromSource(Paths.get("src"), R2Src);
        ToolBox.createJavaFileFromSource(Paths.get("src"), R3Src);
    }

    void compileFiles() throws Exception {
//        "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . -classpath .
//              -sourcepath src src/x/B.java src/x/C.java src/y/Main.java
        ToolBox.JavaToolArgs args =
                new ToolBox.JavaToolArgs()
                .setAllArgs("-d", ".", "-cp" , ".", "-sourcepath", "src",
                "src/x/B.java", "src/x/C.java", "src/y/Main.java");
        ToolBox.javac(args);

//        rm y/R3.class
        ToolBox.rm(Paths.get("y", "R3.class"));

//        "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . -classpath .
//                -sourcepath src src/y/Main.java
        args.setAllArgs("-d", ".", "-cp", ".", "-sourcepath", "src", "src/y/Main.java");
        ToolBox.javac(args);
    }

}
