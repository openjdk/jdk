/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.aot
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @build compiler.aot.verification.ClassAndLibraryNotMatchTest
 * @run driver compiler.aot.verification.ClassAndLibraryNotMatchTest
 * @summary check if class and aot library are properly bound to each other
 */

package compiler.aot.verification;

import compiler.aot.AotCompiler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ClassAndLibraryNotMatchTest {
    private static final String HELLO_WORLD_CLASS_NAME = "HelloWorld";
    private static final String LIB_NAME = "lib" + HELLO_WORLD_CLASS_NAME + ".so";
    private static final String HELLO_WORLD_MSG1 = "HelloWorld1";
    private static final String HELLO_WORLD_MSG2 = "HelloWorld2";
    private static final String HELLO_WORLD_FILE = "./" + HELLO_WORLD_CLASS_NAME + ".java";
    private static final String HELLO_WORLD_PRE = "public class "
            + HELLO_WORLD_CLASS_NAME + " {\n"
            + "    public static void main(String args[]) {\n"
            + "        System.out.println(\"";
    private static final String HELLO_WORLD_POST = "\");\n"
            + "    }\n"
            + "}\n";

    public static void main(String args[]) {
        new ClassAndLibraryNotMatchTest().runTest();
    }

    private void writeHelloWorld(String message) {
        String src = HELLO_WORLD_PRE + message + HELLO_WORLD_POST;
        try{
            Files.write(Paths.get(HELLO_WORLD_FILE), src.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new Error("Can't write HelloWorld " + e, e);
        }
    }

    private void compileHelloWorld() {
        String javac = JDKToolFinder.getCompileJDKTool("javac");
        ProcessBuilder pb = new ProcessBuilder(javac, HELLO_WORLD_FILE);
        OutputAnalyzer oa;
        try {
            oa = ProcessTools.executeProcess(pb);
        } catch (Exception e) {
            throw new Error("Can't compile class " + e, e);
        }
        oa.shouldHaveExitValue(0);
    }

    private void compileAotLibrary() {
        AotCompiler.launchCompiler(LIB_NAME, HELLO_WORLD_CLASS_NAME,
                Arrays.asList("-classpath", Utils.TEST_CLASS_PATH + File.pathSeparator + "."), null);
    }

    private void runAndCheckHelloWorld(String checkString) {
        ProcessBuilder pb;
        try {
            pb = ProcessTools.createJavaProcessBuilder(true, "-cp", ".",
                    "-XX:+UnlockExperimentalVMOptions", "-XX:+UseAOT",
                    "-XX:AOTLibrary=./" + LIB_NAME, HELLO_WORLD_CLASS_NAME);
        } catch (Exception e) {
            throw new Error("Can't create ProcessBuilder to run "
                    + HELLO_WORLD_CLASS_NAME + " " + e, e);
        }
        OutputAnalyzer oa;
        try {
            oa = ProcessTools.executeProcess(pb);
        } catch (Exception e) {
            throw new Error("Can't execute " + HELLO_WORLD_CLASS_NAME + " " + e, e);
        }
        oa.shouldHaveExitValue(0);
        oa.shouldContain(checkString);
    }

    private void createHelloWorld(String msg) {
        writeHelloWorld(msg);
        compileHelloWorld();
    }

    private void runTest() {
        createHelloWorld(HELLO_WORLD_MSG1);
        compileAotLibrary();
        runAndCheckHelloWorld(HELLO_WORLD_MSG1);
        createHelloWorld(HELLO_WORLD_MSG2);
        runAndCheckHelloWorld(HELLO_WORLD_MSG2);
    }
}
