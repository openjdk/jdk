/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7124089 7131021 8042469 8066185
 * @summary Checks for Launcher special flags, such as MacOSX specific flags,
 *          and JVM NativeMemoryTracking flags.
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
        String launcherPidString = "launcher.pid=";
        String envVarPidString = "TRACER_MARKER: NativeMemoryTracking: env var is NMT_LEVEL_";
        String NMT_Option_Value = "off";
        String myClassName = "helloworld";
        boolean haveLauncherPid = false;

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

        /*
         * On Linux, Launcher Tracking will print the PID.  Use this info
         * to validate what we got as the PID in the Launcher itself.
         * Linux is the only one that prints this, and trying to get it
         * here for win is awful.  So let the linux test make sure we get
         * the valid pid, and for non-linux, just make sure pid string is
         * non-zero.
         */
        if (isLinux) {
            // get what the test says is the launcher pid
            String launcherPid = null;
            for (String line : tr.testOutput) {
                int index = line.indexOf(launcherPidString);
                if (index >= 0) {
                    int sindex = index + launcherPidString.length();
                    int tindex = sindex + line.substring(sindex).indexOf("'");
                    System.out.println("DEBUG INFO: sindex = " + sindex);
                    System.out.println("DEBUG INFO: searching substring: " + line.substring(sindex));
                    System.out.println("DEBUG INFO: tindex = " + tindex);
                    // DEBUG INFO
                    System.out.println(tr);
                    launcherPid = line.substring(sindex, tindex);
                    break;
                }
            }
            if (launcherPid == null) {
                System.out.println(tr);
                throw new RuntimeException("Error: failed to find launcher Pid in launcher tracking info");
            }

            // did we create the env var with the correct pid?
            if (!launcherPid.equals(envVarPid)) {
                System.out.println(tr);
                System.out.println("Error: wrong pid in creating env var");
                System.out.println("Error Info: launcherPid = " + launcherPid);
                System.out.println("Error Info: envVarPid   = " + envVarPid);
                throw new RuntimeException("Error: wrong pid in creating env var");
            }
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
        TestResult tr = null;
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

        // make sure a missing class is handled correctly, because the class
        // resolution is performed by the JVM.
        tr = doExec(javaCmd, "AbsentClass", "-XX:NativeMemoryTracking=summary");
        if (!tr.contains("Error: Could not find or load main class AbsentClass")) {
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
