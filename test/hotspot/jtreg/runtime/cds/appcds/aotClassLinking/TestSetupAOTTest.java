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
 *
 */

/**
 * @test
 * @summary This is a test case for creating an AOT cache using the setup_aot/TestSetupAOT.java program, which
 *          is used for running HotSpot tests in the "AOT mode"
 *          (E.g., make test JTREG=AOT_JDK=true TEST=open/test/hotspot/jtreg/runtime/invokedynamic)
 * @requires vm.cds
 * @library /test/lib /test/setup_aot
 * @build TestSetupAOTTest JavacBenchApp TestSetupAOT
 * @run driver jdk.test.lib.helpers.ClassFileInstaller
 *                 TestSetupAOT
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestSetupAOT
 *                 TestSetupAOT$ToolOutput
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver TestSetupAOTTest
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class TestSetupAOTTest {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("TestSetupAOT")
            .classpath("app.jar")
            .appCommandLine("TestSetupAOT", ".")
            .runAOTWorkflow();
    }
}
