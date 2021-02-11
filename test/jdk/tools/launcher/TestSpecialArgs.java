/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7124089 7131021 8042469 8066185 8074373 8258917
 * @summary Checks for Launcher special flags, such as MacOSX specific flags,
 *          and JVM NativeMemoryTracking flags.
 * @modules jdk.compiler
 *          jdk.zipfs
 * @compile -XDignore.symbol.file TestSpecialArgs.java EnvironmentVariables.java
 * @run main TestSpecialArgs
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestSpecialArgs extends TestHelper {

    public static void main(String... args) throws Exception {
        new TestSpecialArgs().run(args);
    }

    @Test
    void testDocking() {
        final Map<String, String> envMap = new HashMap<>();
        envMap.put("_JAVA_LAUNCHER_DEBUG", "true");
        TestResult tr = doExec(envMap, javaCmd, "-XstartOnFirstThread", "-version");
        if (isMacOSX) {
            if (!tr.contains("In same thread")) {
                System.out.println(tr);
                throw new RuntimeException("Error: not running in the same thread ?");
            }
            if (!tr.isOK()) {
                System.out.println(tr);
                throw new RuntimeException("Error: arg was rejected ????");
            }
        } else {
            if (tr.isOK()) {
                System.out.println(tr);
                throw new RuntimeException("Error: argument was accepted ????");
            }
        }

        tr = doExec(javaCmd, "-Xdock:/tmp/not-available", "-version");
        if (isMacOSX) {
            if (!tr.isOK()) {
                System.out.println(tr);
                throw new RuntimeException("Error: arg was rejected ????");
            }
        } else {
            if (tr.isOK()) {
                System.out.println(tr);
                throw new RuntimeException("Error: argument was accepted ????");
            }
        }
        // MacOSX specific tests ensue......
        if (!isMacOSX) {
            return;
        }
        Set<String> envToRemove = new HashSet<>();
        Map<String, String> map = System.getenv();
        for (String s : map.keySet()) {
            if (s.startsWith("JAVA_MAIN_CLASS_")
                    || s.startsWith("APP_NAME_")
                    || s.startsWith("APP_ICON_")) {
                envToRemove.add(s);
            }
        }
        runTest(envToRemove, javaCmd, "-cp", TEST_CLASSES_DIR.getAbsolutePath(),
                "EnvironmentVariables", "JAVA_MAIN_CLASS_*",
                "EnvironmentVariables");

        runTest(envToRemove, javaCmd, "-cp", TEST_CLASSES_DIR.getAbsolutePath(),
                "-Xdock:name=TestAppName", "EnvironmentVariables",
                "APP_NAME_*", "TestAppName");

        runTest(envToRemove, javaCmd, "-cp", TEST_CLASSES_DIR.getAbsolutePath(),
                "-Xdock:icon=TestAppIcon", "EnvironmentVariables",
                "APP_ICON_*", "TestAppIcon");
    }

    void runTest(Set<String> envToRemove, String... args) {
        TestResult tr = doExec(null, envToRemove, args);
        if (!tr.isOK()) {
            System.err.println(tr.toString());
            throw new RuntimeException("Test Fails");
        }
    }

    @Test
    void testNativeMemoryTracking() {
        final Map<String, String> envMap = new HashMap<>();
        envMap.put("_JAVA_LAUNCHER_DEBUG", "true");
        TestResult tr;
        /*
         * test argument : -XX:NativeMemoryTracking=value
         * A JVM flag, comsumed by the JVM, but requiring launcher
         * to set an environmental variable if and only if value is supplied.
         * Test and order:
         * 1) execute with valid parameter: -XX:NativeMemoryTracking=MyValue
         *    a) check for correct env variable name: "NMT_LEVEL_" + pid
         *    b) check that "MyValue" was found in local env.
         * 2) execute with invalid parameter: -XX:NativeMemoryTracking=
         *    !) Won't find "NativeMemoryTracking:"
         *       Code to create env variable not executed.
         * 3) execute with invalid parameter: -XX:NativeMemoryTracking
         *    !) Won't find "NativeMemoryTracking:"
         *       Code to create env variable not executed.
         * 4) give and invalid value and check to make sure JVM commented
         */
        String envVarPidString = "TRACER_MARKER: NativeMemoryTracking: env var is NMT_LEVEL_";
        String NMT_Option_Value = "off";
        String myClassName = "helloworld";

        // === Run the tests ===
        // ---Test 1a
        tr = doExec(envMap, javaCmd, "-XX:NativeMemoryTracking=" + NMT_Option_Value,
                "-version");

        // get the PID from the env var we set for the JVM
        String envVarPid = null;
        for (String line : tr.testOutput) {
            if (line.contains(envVarPidString)) {
                int sindex = envVarPidString.length();
                envVarPid = line.substring(sindex);
                break;
            }
        }
        // did we find envVarPid?
        if (envVarPid == null) {
            System.out.println(tr);
            throw new RuntimeException("Error: failed to find env Var Pid in tracking info");
        }
        // we think we found the pid string.  min test, not "".
        if (envVarPid.length() < 1) {
            System.out.println(tr);
            throw new RuntimeException("Error: env Var Pid in tracking info is empty string");
        }

        // --- Test 1b
        if (!tr.contains("NativeMemoryTracking: got value " + NMT_Option_Value)) {
            System.out.println(tr);
            throw new RuntimeException("Error: Valid param failed to set env variable");
        }

        // --- Test 2
        tr = doExec(envMap, javaCmd, "-XX:NativeMemoryTracking=",
                "-version");
        if (tr.contains("NativeMemoryTracking:")) {
            System.out.println(tr);
            throw new RuntimeException("Error: invalid param caused env variable to be erroneously created");
        }
        if (!tr.contains("Syntax error, expecting -XX:NativeMemoryTracking=")) {
            System.out.println(tr);
            throw new RuntimeException("Error: invalid param not checked by JVM");
        }

        // --- Test 3
        tr = doExec(envMap, javaCmd, "-XX:NativeMemoryTracking",
                "-version");
        if (tr.contains("NativeMemoryTracking:")) {
            System.out.println(tr);
            throw new RuntimeException("Error: invalid param caused env variable to be erroneously created");
        }
        if (!tr.contains("Syntax error, expecting -XX:NativeMemoryTracking=")) {
            System.out.println(tr);
            throw new RuntimeException("Error: invalid param not checked by JVM");
        }
        // --- Test 4
        tr = doExec(envMap, javaCmd, "-XX:NativeMemoryTracking=BADVALUE",
                "-version");
        if (!tr.contains("expecting -XX:NativeMemoryTracking")) {
            System.out.println(tr);
            throw new RuntimeException("Error: invalid param did not get JVM Syntax error message");
        }
    }

    @Test
    void testNMArgumentProcessing() throws FileNotFoundException {
        TestResult tr;
        // the direct invokers of the VM
        String options[] = {
            "-version", "-fullversion", "-help", "-?", "-X"
        };
        for (String option : options) {
            tr = doExec(javaCmd, option, "-XX:NativeMemoryTracking=summary");
            checkTestResult(tr);
        }

        // create a test jar
        File jarFile = new File("test.jar");
        createJar(jarFile, "public static void main(String... args){}");

        // ones that involve main-class of some sort
        tr = doExec(javaCmd, "-jar", jarFile.getName(),
                "-XX:NativeMemoryTracking=summary");
        checkTestResult(tr);

        tr = doExec(javaCmd, "-cp", jarFile.getName(), "Foo",
                "-XX:NativeMemoryTracking=summary");
        checkTestResult(tr);

        final Map<String, String> envMap = new HashMap<>();
        // checkwith CLASSPATH set ie. no -cp or -classpath
        envMap.put("CLASSPATH", ".");
        tr = doExec(envMap, javaCmd, "Foo", "-XX:NativeMemoryTracking=summary");
        checkTestResult(tr);

        // should accept with no warnings
        tr = doExec(javaCmd, "-cp", jarFile.getName(),
                    "-XX:NativeMemoryTracking=summary", "Foo");
        ensureNoWarnings(tr);

        // should accept with no warnings
        tr = doExec(javaCmd, "-classpath", jarFile.getName(),
                    "-XX:NativeMemoryTracking=summary", "Foo");
        ensureNoWarnings(tr);

        // make sure a missing class is handled correctly, because the class
        // resolution is performed by the JVM.
        tr = doExec(javaCmd, "AbsentClass", "-XX:NativeMemoryTracking=summary");
        if (!tr.contains("Error: Could not find or load main class AbsentClass")) {
            throw new RuntimeException("Test Fails");
        }

        // Make sure we handle correctly the module long form (--module=)
        tr = doExec(javaCmd, "-XX:NativeMemoryTracking=summary", "--module=jdk.compiler/com.sun.tools.javac.Main", "--help");
        ensureNoWarnings(tr);
    }

    @Test
    void testNMTTools() throws FileNotFoundException {
        TestResult tr;
        // Tools (non-java launchers) should handle NTM (no "wrong launcher" warning).
        tr = doExec(jarCmd, "-J-XX:NativeMemoryTracking=summary", "--help");
        ensureNoWarnings(tr);

        // And java terminal args (like "--help") don't stop "-J" args parsing.
        tr = doExec(jarCmd, "--help", "-J-XX:NativeMemoryTracking=summary");
        ensureNoWarnings(tr);
    }

    void ensureNoWarnings(TestResult tr) {
        checkTestResult(tr);
        if (tr.contains("warning: Native Memory Tracking")) {
            System.err.println(tr.toString());
            throw new RuntimeException("Test Fails");
        }
    }

    void checkTestResult(TestResult tr) {
        if (!tr.isOK()) {
            System.err.println(tr.toString());
            throw new RuntimeException("Test Fails");
        }
    }
}
