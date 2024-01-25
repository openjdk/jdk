/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test PrintIdealPhaseTest
 * @summary Checks that -XX:CompileCommand=PrintIdealPhase,... works
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @requires vm.debug == true & vm.compiler2.enabled & vm.compMode != "Xcomp"
 * @run driver compiler.oracle.PrintIdealPhaseTest
 */

package compiler.oracle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class PrintIdealPhaseTest {

    public static void main(String[] args) throws Exception {
        new PrintIdealPhaseTest();
    }

    PrintIdealPhaseTest() throws Exception {
        // The Phases specified here will be exchanged for the enum Phase in compiler.lib.ir_framework when it's done

        // Test -XX:CompileCommand=PrintIdealPhase,*::test,CCP1
        List<String> expectedPhases = new ArrayList<String>();
        expectedPhases.add("CCP1");
        runTest("CCP1", expectedPhases, "hotspot_log_1.log", true);
        runTest("FISH", expectedPhases, "hotspot_log_1f.log", false);

        // Test -XX:CompileCommand=PrintIdealPhase,*::test,MATCHING
        expectedPhases.clear();
        expectedPhases.add("MATCHING");
        runTest("MATCHING", expectedPhases, "hotspot_log_2.log", true);

        // Test -XX:CompileCommand=PrintIdealPhase,*::test,CCP_1,AFTER_MATCHING
        expectedPhases.add("CCP1");
        runTest("MATCHING,CCP1", expectedPhases, "hotspot_log_3.log", true);
    }

    private void runTest(String cmdPhases, List<String> expectedPhases, String logFile, boolean valid) throws Exception {
        List<String> options = new ArrayList<String>();
        options.add("-Xbatch");
        options.add("-XX:+PrintCompilation");
        options.add("-XX:LogFile="+logFile);
        options.add("-XX:+IgnoreUnrecognizedVMOptions");
        options.add("-XX:CompileCommand=dontinline," + getTestClass() + "::test");
        options.add("-XX:CompileCommand=PrintIdealPhase," + getTestClass() + "::test," + cmdPhases);
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        if (valid) {
            oa.shouldHaveExitValue(0)
            .shouldContain("CompileCommand: PrintIdealPhase compiler/oracle/PrintIdealPhaseTest$TestMain.test const char* PrintIdealPhase = '"+cmdPhases.replace(',', ' ')+"'")
            .shouldNotContain("CompileCommand: An error occurred during parsing")
            .shouldNotContain("Error: Unrecognized phase name in PrintIdealPhase:")
            .shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

             // Check that all the expected phases matches what can be found in the compilation log file
             HashSet<String> loggedPhases = parseLogFile(logFile);
             System.out.println("Logged phases:");
             for (String loggedPhase : loggedPhases) {
                 System.out.println("loggedPhase: "+ loggedPhase);
             }
             for (String expectedPhase : expectedPhases) {
                 System.out.println("Looking for phase: " + expectedPhase);

                 Asserts.assertTrue(loggedPhases.contains(expectedPhase), "Must find specified phase: " + expectedPhase);
                 loggedPhases.remove(expectedPhase);
             }
             Asserts.assertTrue(loggedPhases.isEmpty(), "Expect no other phases");
        } else {
            // Check that we don't pass even though bad phase names where given
            oa.shouldHaveExitValue(1)
            .shouldContain("CompileCommand: An error occurred during parsing")
            .shouldContain("Error: Unrecognized phase name in PrintIdealPhase:");
        }
    }

    private HashSet<String> parseLogFile(String logFile) {
        String printIdealTag = "<ideal";
        Pattern compilePhasePattern = Pattern.compile("compile_phase='([a-zA-Z0-9 ]+)'");
        HashSet<String> phasesFound = new HashSet<>();

        try (var br = Files.newBufferedReader(Paths.get(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(printIdealTag)) {
                    Matcher matcher = compilePhasePattern.matcher(line);
                    if (matcher.find()) {
                        phasesFound.add(matcher.group(1));
                    } else {
                        throw new Error("Failed to match compile_phase in file: " + logFile);
                    }
                }
            }
        } catch (IOException e) {
            throw new Error("Failed to read " + logFile + " data: " + e, e);
        }
        return phasesFound;
    }

    // Test class that is invoked by the sub process
    public String getTestClass() {
        return TestMain.class.getName();
    }

    public static class TestMain {
        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                test(i);
            }
        }

        static void test(int i) {
            if ((i % 1000) == 0) {
                System.out.println("Hello World!");
            }
        }
    }
}
