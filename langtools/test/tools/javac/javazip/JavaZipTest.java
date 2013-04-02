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
 * @bug 4098712 6304984 6388453
 * @summary check that source files inside zip files on the class path are ignored
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main JavaZipTest
 */

import java.io.File;
import java.nio.file.Paths;

//original test: test/tools/javac/javazip/Test.sh
public class JavaZipTest {

    private static final String ASrc =
        "class A {\n" +
        "    B b;\n" +
        "}";

    private static final String BGoodSrc =
        "public class B {}";

    private static final String BBadSrc =
        "class B";

    private static final String[][] jarArgs = {
        {"cf", "good.jar", "-C", "good", "B.java"},
        {"cf", "good.zip", "-C", "good", "B.java"},
        {"cf", "bad.jar", "-C", "bad", "B.java"},
        {"cf", "bad.zip", "-C", "bad", "B.java"},
    };

    private static final String[][] successfulCompilationArgs = {
        {"-d", "output", "A.java", "good/B.java"},
        {"-d", "output", "-cp", "good", "A.java"},
        {"-d", "output", "-sourcepath", "good", "A.java"},
        {"-d", "output", "-cp", "good.zip", "A.java"},
        {"-d", "output", "-cp", "good.jar", "A.java"},
    };

    private static final String[][] unSuccessfulCompilationArgs = {
        {"-d", "output", "A.java", "bad/B.java"},
        {"-d", "output", "-cp", "bad", "A.java"},
        {"-d", "output", "-sourcepath", "bad", "A.java"},
        {"-d", "output", "-sourcepath", "bad.zip", "A.java"},
        {"-d", "output", "-sourcepath", "bad.jar", "A.java"},
    };

    public static void main(String[] args) throws Exception {
        new JavaZipTest().test();
    }

    public void test() throws Exception {
        createOutputDirAndSourceFiles();
        createZipsAndJars();
        check(ToolBox.Expect.SUCCESS, successfulCompilationArgs);
        check(ToolBox.Expect.FAIL, unSuccessfulCompilationArgs);
    }

    void createOutputDirAndSourceFiles() throws Exception {
        //create output dir
        new File("output").mkdir();

        //source file creation
        ToolBox.createJavaFileFromSource(Paths.get("good"), BGoodSrc);
        ToolBox.createJavaFileFromSource(Paths.get("bad"), BBadSrc);
        ToolBox.createJavaFileFromSource(ASrc);
    }

    void createZipsAndJars() throws Exception {
        //jar and zip creation
//        check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}good.jar" -C "${TESTSRC}${FS}good" B.java
//        check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}good.zip" -C "${TESTSRC}${FS}good" B.java
//        check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}bad.jar"  -C "${TESTSRC}${FS}bad" B.java
//        check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}bad.zip"  -C "${TESTSRC}${FS}bad" B.java
        for (String[] args: jarArgs) {
            ToolBox.jar(args);
        }
    }

    void check(ToolBox.Expect whatToExpect, String[][] theArgs) throws Exception {
//        check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} "${TESTSRC}${FS}A.java" "${TESTSRC}${FS}good${FS}B.java"
//        check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${TESTSRC}${FS}good"   "${TESTSRC}${FS}A.java"
//        check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${TESTSRC}${FS}good"  "${TESTSRC}${FS}A.java"
//        check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${SCR}${FS}good.zip"   "${TESTSRC}${FS}A.java"
//        check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${SCR}${FS}good.jar"   "${TESTSRC}${FS}A.java"

//        check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} "${TESTSRC}${FS}A.java" "${TESTSRC}${FS}bad${FS}B.java"
//        check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${TESTSRC}${FS}bad"    "${TESTSRC}${FS}A.java"
//        check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${TESTSRC}${FS}bad"   "${TESTSRC}${FS}A.java"
//        check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${SCR}${FS}bad.zip"   "${TESTSRC}${FS}A.java"
//        check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${SCR}${FS}bad.jar"   "${TESTSRC}${FS}A.java"
        ToolBox.JavaToolArgs args =
                new ToolBox.JavaToolArgs(whatToExpect);

        for (String[] allArgs: theArgs) {
            args.setAllArgs(allArgs);
            ToolBox.javac(args);
        }
    }

}
