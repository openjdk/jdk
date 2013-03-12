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
 * @bug 4241229 4785453
 * @summary Test -classpath option and classpath defaults.
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main ClassPathTest
 */

import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import com.sun.tools.javac.util.ArrayUtils;

//original test: test/tools/javac/ClassPathTest/ClassPathTest.sh
public class ClassPathTest {

    private static final String ClassPathTest1Src =
        "import pkg.*;\n" +
        "public class ClassPathTest1 {\n" +
        "    ClassPathTestAux1 x;\n" +
        "}";

    private static final String ClassPathTest2Src =
        "import pkg.*;\n" +
        "public class ClassPathTest2 {\n" +
        "    ClassPathTestAux2 x;\n" +
        "}";

    private static final String ClassPathTest3Src =
        "import pkg.*;\n" +
        "public class ClassPathTest3 {\n" +
        "    ClassPathTestAux3 x;\n" +
        "}";

    private static final String fooPkgClassPathTestAux1Src =
        "package pkg;\n" +
        "public class ClassPathTestAux1 {}";

    private static final String barPkgClassPathTestAux2Src =
        "package pkg;\n" +
        "public class ClassPathTestAux2 {}";

    private static final String pkgClassPathTestAux3Src =
        "package pkg;\n" +
        "public class ClassPathTestAux3 {}";

    ProcessBuilder pb = null;

    public static void main(String[] args) throws Exception {
        new ClassPathTest().test();
    }

    public void test() throws Exception {
        createOutputDirAndSourceFiles();
        checkCompileCommands();
    }

    void createOutputDirAndSourceFiles() throws Exception {
        //dirs and files creation
        ToolBox.createJavaFileFromSource(ClassPathTest1Src);
        ToolBox.createJavaFileFromSource(ClassPathTest2Src);
        ToolBox.createJavaFileFromSource(ClassPathTest3Src);
        ToolBox.createJavaFileFromSource(Paths.get("foo"),
                fooPkgClassPathTestAux1Src);
        ToolBox.createJavaFileFromSource(Paths.get("bar"),
                barPkgClassPathTestAux2Src);
        ToolBox.createJavaFileFromSource(pkgClassPathTestAux3Src);
    }

    void checkCompileCommands() throws Exception {
        String[] mainArgs = ToolBox.getJavacBin();

//        Without the -cp . parameter the command will fail seems like when called
//        from the command line, the current dir is added to the classpath
//        automatically but this is not happening when called using ProcessBuilder

//        testJavac success ClassPathTest3.java
        String[] commonArgs = ArrayUtils.concatOpen(mainArgs, "-cp", ".");

        ToolBox.AnyToolArgs successParams =
                new ToolBox.AnyToolArgs()
                .setAllArgs(ArrayUtils.concatOpen(commonArgs, "ClassPathTest3.java"));
        ToolBox.executeCommand(successParams);

//        testJavac failure ClassPathTest1.java
        ToolBox.AnyToolArgs failParams =
                new ToolBox.AnyToolArgs(ToolBox.Expect.FAIL)
                .setAllArgs(ArrayUtils.concatOpen(commonArgs, "ClassPathTest1.java"));
        ToolBox.executeCommand(failParams);

//        This is done inside the executeCommand method
//        CLASSPATH=bar; export CLASSPATH

        Map<String, String> extVars = new TreeMap<>();
        extVars.put("CLASSPATH", "bar");

//        testJavac success ClassPathTest2.java
        successParams.setAllArgs(ArrayUtils.concatOpen(mainArgs, "ClassPathTest2.java")).set(extVars);
        ToolBox.executeCommand(successParams);

//        testJavac failure ClassPathTest1.java
        failParams.setAllArgs(ArrayUtils.concatOpen(mainArgs, "ClassPathTest1.java")).set(extVars);
        ToolBox.executeCommand(failParams);

//        testJavac failure ClassPathTest3.java
        failParams.setAllArgs(ArrayUtils.concatOpen(mainArgs, "ClassPathTest3.java"));
        ToolBox.executeCommand(failParams);

//        testJavac success -classpath foo ClassPathTest1.java

        commonArgs = ArrayUtils.concatOpen(mainArgs, "-cp", "foo");
        successParams.setAllArgs(ArrayUtils.concatOpen(commonArgs, "ClassPathTest1.java"));
        ToolBox.executeCommand(successParams);

//        testJavac failure -classpath foo ClassPathTest2.java
        failParams.setAllArgs(ArrayUtils.concatOpen(commonArgs, "ClassPathTest2.java"));
        ToolBox.executeCommand(failParams);

//        testJavac failure -classpath foo ClassPathTest3.java
        failParams.setAllArgs(ArrayUtils.concatOpen(commonArgs, "ClassPathTest3.java"));
        ToolBox.executeCommand(failParams);
    }

}
