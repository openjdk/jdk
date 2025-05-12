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
 * @summary Sanity test of combinations of the diagnostic flags [+-]AOTRecordTraining and [+-]AOTReplayTraining
 * @requires vm.cds
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @library /test/lib /test/setup_aot
 * @build AOTProfileFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver AOTProfileFlags
 */

import jdk.test.lib.cds.SimpleCDSAppTester;

public class AOTProfileFlags {
    public static void main(String... args) throws Exception {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j ++) {
                SimpleCDSAppTester.of("AOTProfileFlags" + i + "" + j)
                    .addVmArgs("-XX:+UnlockDiagnosticVMOptions",
                               "-XX:" + (i == 0 ? "-" : "+") + "AOTRecordTraining",
                               "-XX:" + (j == 0 ? "-" : "+") + "AOTReplayTraining")
                    .classpath("app.jar")
                    .appCommandLine("JavacBenchApp", "10")
                    .runAOTWorkflow();
            }
        }
    }
}
