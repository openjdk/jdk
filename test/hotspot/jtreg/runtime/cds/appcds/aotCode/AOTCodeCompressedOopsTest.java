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
 * @summary Sanity test of AOT Code Cache with compressed oops configurations
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @requires !vm.jvmci.enabled
 * @library /test/lib /test/setup_aot
 * @build AOTCodeCompressedOopsTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             JavacBenchApp
 *             JavacBenchApp$ClassFile
 *             JavacBenchApp$FileManager
 *             JavacBenchApp$SourceFile
 * @run driver AOTCodeCompressedOopsTest 
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeCompressedOopsTest {
    private enum Base {
        ZERO,
        NON_ZERO
    }
    private enum Shift {
        ZERO,
        NON_ZERO
    }
    public static void main(String... args) throws Exception {
        {
            Tester t = new Tester();
            t.setHeapConfig(Tester.RunMode.ASSEMBLY, true, true);
            t.runAOTAssemblyWorkflow();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, true);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, false);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, false, false);
            t.productionRun();
        }
        {
            Tester t = new Tester();
            t.setHeapConfig(Tester.RunMode.ASSEMBLY, true, false);
            t.runAOTAssemblyWorkflow();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, true);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, false);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, false, false);
            t.productionRun();
        }
        {
            Tester t = new Tester();
            t.setHeapConfig(Tester.RunMode.ASSEMBLY, false, false);
            t.runAOTAssemblyWorkflow();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, true);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, true, false);
            t.productionRun();
            t.setHeapConfig(Tester.RunMode.PRODUCTION, false, false);
            t.productionRun();
        }
    }
    static class Tester extends CDSAppTester {
        Base baseInAsmPhase, baseInProdPhase;
        Shift shiftInAsmPhase, shiftInProdPhase;
        boolean zeroBaseInAsmPhase, zeroBaseInProdPhase;
        boolean zeroShiftInAsmPhase, zeroShiftInProdPhase;

        public Tester() {
            super("AOTCodeCompressedOopsTest");
        }

        public void setHeapConfig(RunMode runMode, boolean isBaseZero, boolean isShiftZero) {
            if (runMode == RunMode.ASSEMBLY) {
                zeroBaseInAsmPhase = isBaseZero;
                zeroShiftInAsmPhase = isShiftZero;
            } else if (runMode == RunMode.PRODUCTION) {
                zeroBaseInProdPhase = isBaseZero;
                zeroShiftInProdPhase = isShiftZero;
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        List<String> getVMArgsForHeapConfig(boolean isBaseZero, boolean isShiftZero) {
            List<String> list = new ArrayList<String>();
            if (isBaseZero && isShiftZero) {
                list.add("-Xmx1g"); // Set max heap < 4G
            } else if (isBaseZero && !isShiftZero) {
                list.add("-Xmx6g"); // Set max heap > 4G
            } else if (!isBaseZero && !isShiftZero) {
                list.add("-Xmx6g");
                list.add("-XX:HeapBaseMinAddress=32g");
            }
            return list;
       }

        @Override
        public String[] vmArgs(RunMode runMode) {
            switch (runMode) {
            case RunMode.ASSEMBLY: {
                    List<String> args = getVMArgsForHeapConfig(zeroBaseInAsmPhase, zeroShiftInAsmPhase);
                    args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                        "-Xlog:cds=info",
                                        "-Xlog:aot+codecache+init=debug",
                                        "-Xlog:aot+codecache+exit=debug"));
                    return args.toArray(new String[0]);
                }
            case RunMode.PRODUCTION: {
                    List<String> args = getVMArgsForHeapConfig(zeroBaseInProdPhase, zeroShiftInProdPhase);
                    args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                        "-Xlog:cds=info",
                                        "-Xlog:aot+codecache+init=debug",
                                        "-Xlog:aot+codecache+exit=debug"));
                    return args.toArray(new String[0]);
                }
            }
            return new String[] {};
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                "JavacBenchApp", "10"
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                 if (zeroShiftInAsmPhase != zeroShiftInProdPhase) {
                     out.shouldContain("AOT Code Cache disabled: it was created with different CompressedOops::shift()");
                 } else if (zeroBaseInAsmPhase != zeroBaseInProdPhase) {
                     out.shouldContain("AOTStubCaching is disabled: incompatible CompressedOops::base()");
                 } else {
                     out.shouldMatch("Read \\d+ entries table at offset \\d+ from AOT Code Cache");
                 }
            }
        }
    }
}
