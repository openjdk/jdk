/*
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6572160
 * @summary stress getObjectSize() API
 * @author Daniel D. Daugherty as modified from the code of fischman@google.com
 * @key intermittent
 * @library /test/lib
 * @build StressGetObjectSizeApp
 *        InstrumentationHandoff
 *        ASimpleInstrumentationTestCase
 *        AInstrumentationTestCase
 *        ATestCaseScaffold
 * @run driver StressGetObjectSizeTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class StressGetObjectSizeTest {
    public static void main(String[] args) throws Exception {
        String manifest = "Manifest-Version: 1.0\nPremain-Class: InstrumentationHandoff\n";
        ClassFileInstaller.writeJar("basicAgent.jar",
                ClassFileInstaller.Manifest.fromString(manifest),
                "InstrumentationHandoff");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-javaagent:basicAgent.jar",
                "StressGetObjectSizeApp",
                "StressGetObjectSizeApp");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldNotContain("ASSERTION FAILED");
        output.shouldHaveExitValue(0);
    }
}
