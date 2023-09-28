/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.testlibrary_tests.ir_framework.tests;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.parser.TestClassParser;
import jdk.test.lib.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/*
 * @test
 * @bug 8300273
 * @requires vm.debug == true & vm.flagless
 * @summary Test TestClassParser such that it correctly parses the hotspot_pid* files with safepoint interruption messages
 * @library /test/lib /testlibrary_tests /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm -Xbootclasspath/a:. -DSkipWhiteBoxInstall=true -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                    -XX:+WhiteBoxAPI compiler.testlibrary_tests.ir_framework.tests.TestSafepointWhilePrinting
 */
public class TestSafepointWhilePrinting {
    static int iFld;

    @org.junit.Test
    public void test() throws IOException {
        String hotspotPidFileName = "safepoint_while_printing_hotspot_pid.log";
        Path hotspotPidFilePath = Paths.get(Utils.TEST_SRC).resolve(hotspotPidFileName);
        // Copy file to current workdir
        Files.copy(hotspotPidFilePath, Paths.get("").resolve(hotspotPidFileName),
                   StandardCopyOption.REPLACE_EXISTING);

        String irEncoding =
                """
                ##### IRMatchRulesEncoding - used by TestFramework #####
                <method>,{comma separated applied @IR rule ids}
                test1,1
                test2,1
                testSafepointInBlock,1
                testQueueInBlock1,1
                testQueueInBlock2,1
                testDoubleInterruptOuter,1
                testDoubleInterruptMiddle,1
                testDoubleInterruptInner,1
                testCompilePhaseBackToBackFirst,1
                testCompilePhaseBackToBackLast,1
                ----- END -----
                ##### IRMatchingVMInfo - used by TestFramework #####
                <key>:<value>
                cpuFeatures:empty_cpu_info
                MaxVectorSize:64
                MaxVectorSizeIsDefault:1
                LoopMaxUnroll:64
                UseAVX:1
                UseAVXIsDefault:1
                ----- END VMInfo -----
                """;
        TestClassParser testClassParser = new TestClassParser(TestSafepointWhilePrinting.class);
        Matchable testClassMatchable = testClassParser.parse(hotspotPidFileName, irEncoding);
        IRMatcher matcher = new IRMatcher(testClassMatchable);
        matcher.match();
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public void test1() {
        iFld = 34;
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public void test2() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"testSafepointInBlock @ bci:-1", "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testSafepointInBlock() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"testQueueInBlock1 @ bci:-1", "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testQueueInBlock1() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"testQueueInBlock2 @ bci:-1", "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testQueueInBlock2() {
        iFld = 34;
    }
    @Test
    @IR(counts = {"!jvms: TestSafepointWhilePrinting::testDoubleInterruptOuter", "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testDoubleInterruptOuter() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"testDoubleInterruptMiddle @ bci:-1", "1", IRNode.CMP_UL3, "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testDoubleInterruptMiddle() {
        iFld = 34;
    }

    @Test
    @IR(counts = {IRNode.CON_L, "1"}, phase = CompilePhase.PRINT_IDEAL)
    public void testDoubleInterruptInner() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"(line 115)", "1", IRNode.CMP_UL3, "1"}, phase = {CompilePhase.AFTER_PARSING, CompilePhase.BEFORE_MATCHING})
    public void testCompilePhaseBackToBackFirst() {
        iFld = 34;
    }

    @Test
    @IR(counts = {"(line 115)", "1", IRNode.CMP_UL3, "1"}, phase = {CompilePhase.AFTER_PARSING, CompilePhase.BEFORE_MATCHING})
    public void testCompilePhaseBackToBackLast() {
        iFld = 34;
    }
}
