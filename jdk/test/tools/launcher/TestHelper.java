
/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;

/**
 * This class provides some common utilites for the launcher tests.
 */
public enum TestHelper {
    INSTANCE;
    static final String JAVAHOME = System.getProperty("java.home", ".");
    static final boolean isSDK = JAVAHOME.endsWith("jre");
    static final String javaCmd;
    static final String javacCmd;
    static final JavaCompiler compiler;

    static final boolean debug = Boolean.getBoolean("Arrrghs.Debug");
    static final boolean isWindows =
            System.getProperty("os.name", "unknown").startsWith("Windows");
    static int testExitValue = 0;

    static {
        compiler = ToolProvider.getSystemJavaCompiler();
        File binDir = (isSDK) ? new File((new File(JAVAHOME)).getParentFile(), "bin")
            : new File(JAVAHOME, "bin");
        File javaCmdFile = (isWindows)
                ? new File(binDir, "java.exe")
                : new File(binDir, "java");
        javaCmd = javaCmdFile.getAbsolutePath();
        if (!javaCmdFile.canExecute()) {
            throw new RuntimeException("java <" + TestHelper.javaCmd + "> must exist");
        }

        File javacCmdFile = (isWindows)
                ? new File(binDir, "javac.exe")
                : new File(binDir, "javac");
        javacCmd = javacCmdFile.getAbsolutePath();
        if (!javacCmdFile.canExecute()) {
            throw new RuntimeException("java <" + javacCmd + "> must exist");
        }
    }

    /*
     * A convenience method to create a java file, compile and jar it up, using
     * the sole class file name in the jar, as the Main-Class attribute value.
     */
    static void createJar(File jarName, File mainClass, String... mainDefs)
            throws FileNotFoundException {
            createJar(null, jarName, mainClass, mainDefs);
    }

    /*
     * A generic jar file creator to create a java file, compile it
     * and jar it up, a specific Main-Class entry name in the
     * manifest can be specified or a null to use the sole class file name
     * as the Main-Class attribute value.
     */
    static void createJar(String mEntry, File jarName, File mainClass,
            String... mainDefs) throws FileNotFoundException {
        if (jarName.exists()) {
            jarName.delete();
        }
        PrintStream ps = new PrintStream(new FileOutputStream(mainClass + ".java"));
        ps.println("public class Foo {");
        if (mainDefs != null) {
            for (String x : mainDefs) {
                ps.println(x);
            }
        }
        ps.println("}");
        ps.close();

        String compileArgs[] = {
            mainClass + ".java"
        };
        if (compiler.run(null, null, null, compileArgs) != 0) {
            throw new RuntimeException("compilation failed " + mainClass + ".java");
        }
        if (mEntry == null) {
            mEntry = mainClass.getName();
        }
        String jarArgs[] = {
            (debug) ? "cvfe" : "cfe",
            jarName.getAbsolutePath(),
            mEntry,
            mainClass.getName() + ".class"
        };
        sun.tools.jar.Main jarTool =
                new sun.tools.jar.Main(System.out, System.err, "JarCreator");
        if (!jarTool.run(jarArgs)) {
            throw new RuntimeException("jar creation failed " + jarName);
        }
    }

    /*
     * A method which executes a java cmd and returns the results in a container
     */
    static TestResult doExec(String...cmds) {
        String cmdStr = "";
        for (String x : cmds) {
            cmdStr = cmdStr.concat(x + " ");
        }
        ProcessBuilder pb = new ProcessBuilder(cmds);
        Map<String, String> env = pb.environment();
        BufferedReader rdr = null;
        try {
            List<String> outputList = new ArrayList<String>();
            pb.redirectErrorStream(true);
            Process p = pb.start();
            rdr = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String in = rdr.readLine();
            while (in != null) {
                outputList.add(in);
                in = rdr.readLine();
            }
            p.waitFor();
            p.destroy();
            return new TestHelper.TestResult(cmdStr, p.exitValue(), outputList);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
    }

    /*
     * A class to encapsulate the test results and stuff, with some ease
     * of use methods to check the test results.
     */
    static class TestResult {
        StringBuilder status;
        int exitValue;
        List<String> testOutput;

        public TestResult(String str, int rv, List<String> oList) {
            status = new StringBuilder(str);
            exitValue = rv;
            testOutput = oList;
        }

        void checkNegative() {
            if (exitValue == 0) {
                status = status.append("  Error: test must not return 0 exit value");
                testExitValue++;
            }
        }

        void checkPositive() {
            if (exitValue != 0) {
                status = status.append("  Error: test did not return 0 exit value");
                testExitValue++;
            }
        }

        boolean isOK() {
            return exitValue == 0;
        }

        boolean isZeroOutput() {
            if (!testOutput.isEmpty()) {
                status = status.append("  Error: No message from cmd please");
                testExitValue++;
                return false;
            }
            return true;
        }

        boolean isNotZeroOutput() {
            if (testOutput.isEmpty()) {
                status = status.append("  Error: Missing message");
                testExitValue++;
                return false;
            }
            return true;
        }

        public String toString() {
            if (debug) {
                for (String x : testOutput) {
                    status = status.append(x + "\n");
                }
            }
            return status.toString();
        }

        boolean contains(String str) {
            for (String x : testOutput) {
                if (x.contains(str)) {
                    return true;
                }
            }
            status = status.append("   Error: string <" + str + "> not found ");
            testExitValue++;
            return false;
        }
    }
}
