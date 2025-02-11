/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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


import java.io.File;
import java.util.ArrayList;

/**
 * @test
 * @bug 8329581
 * @summary verifies launcher prints stack trace when main class can't be loaded
 * @compile LauncherExceptionTest.java
 * @run main LauncherExceptionTest
 */

public class LauncherExceptionTest extends TestHelper {

    @Test
    void testLauncherReportsException() throws Exception {
        if (!isEnglishLocale()) {
            return;
        }

        File cwd = new File(".");
        File srcDir = new File(cwd, "src");
        if (srcDir.exists()) {
            recursiveDelete(srcDir);
        }
        srcDir.mkdirs();

        // Generate class Test.java
        ArrayList<String> scratchPad = new ArrayList<>();
        scratchPad.add("public class Test {");
        scratchPad.add("    static class SomeDependency {}");
        scratchPad.add("    private static final SomeDependency X = new SomeDependency();");
        scratchPad.add("    public static void main(String... args) {");
        scratchPad.add("        System.out.println(\"X=\" + X);");
        scratchPad.add("    }");
        scratchPad.add("}");
        createFile(new File(srcDir, "Test.java"), scratchPad);


        // Compile and execute Test should succeed
        TestResult trCompilation = doExec(javacCmd,
                "-d", "classes",
                new File(srcDir, "Test.java").toString());
        if (!trCompilation.isOK()) {
            System.err.println(trCompilation);
            throw new RuntimeException("Error: compiling");
        }

        TestResult trExecution = doExec(javaCmd, "-cp", "classes", "Test");
        if (!trExecution.isOK()) {
            System.err.println(trExecution);
            throw new RuntimeException("Error: executing");
        }

        // Delete dependency
        File dependency = new File("classes/Test$SomeDependency.class");
        recursiveDelete(dependency);

        // Executing Test should report exception description
        trExecution = doExec(javaCmd, "-cp", "classes", "Test");
        trExecution.contains("Exception in thread \"main\" java.lang.NoClassDefFoundError: " +
                "Test$SomeDependency");
        trExecution.contains("Caused by: java.lang.ClassNotFoundException: " +
                "Test$SomeDependency");
        if (!trExecution.testStatus)
            System.err.println(trExecution);
    }

    public static void main(String[] args) throws Exception {
        LauncherExceptionTest a = new LauncherExceptionTest();
        a.run(args);
        if (testExitValue > 0) {
            System.out.println("Total of " + testExitValue + " failed");
            throw new RuntimeException("Test failed");
        } else {
            System.out.println("Test passed");
        }
    }
}
