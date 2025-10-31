/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests;

import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @requires vm.debug == true & vm.flagless
 * @summary Test -DForceSequentialScenarios property flag.
 * @library /test/lib /
 * @run driver ir_framework.tests.TestDForceSequentialScenarios
 */

public class TestDForceSequentialScenarios {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
                Scenario s1 = new Scenario(1);
                Scenario s2 = new Scenario(5);
                Scenario s3 = new Scenario(10);
                new TestFramework().addScenarios(s1, s2, s3).startParallel();
        } else {
            OutputAnalyzer oa;
            ProcessBuilder process = ProcessTools.createLimitedTestJavaProcessBuilder(
                    "-Dtest.jdk=" + Utils.TEST_JDK,
                    "-DForceSequentialScenarios=true",
                    "ir_framework.tests.TestDForceSequentialScenarios",
                    "test");
            oa = ProcessTools.executeProcess(process);
            oa.shouldHaveExitValue(0);
            System.out.println(oa.getOutput());
            Asserts.assertTrue(oa.getOutput().matches("(?s).*Scenario #1.*Scenario #5.*Scenario #10.*"));
        }
    }

    @Test
    public void test() {
    }
}
