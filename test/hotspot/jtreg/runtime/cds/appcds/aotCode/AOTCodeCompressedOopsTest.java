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
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compMode != "Xcomp"
 * @comment The test verifies AOT checks during VM startup and not code generation.
 *          No need to run it with -Xcomp. It takes a lot of time to complete all
 *          subtests with this flag.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeCompressedOopsTest {
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
            // Note the VM options used are best-effort to get the desired base and shift,
            // but it is OS dependent, so we may not get the desired configuration.
            if (isBaseZero && isShiftZero) {
                list.add("-Xmx128m"); // Set max heap < 4G;
            } else if (isBaseZero && !isShiftZero) {
                list.add("-Xmx6g"); // Set max heap > 4G for shift to be non-zero
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
                                        "-Xlog:aot=info",
                                        "-Xlog:aot+codecache+init=debug",
                                        "-Xlog:aot+codecache+exit=debug"));
                    return args.toArray(new String[0]);
                }
            case RunMode.PRODUCTION: {
                    List<String> args = getVMArgsForHeapConfig(zeroBaseInProdPhase, zeroShiftInProdPhase);
                    args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                        "-Xlog:aot=info", // we need this to parse CompressedOops settings
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
                 int aotCacheShift = -1, currentShift = -1;
                 long aotCacheBase = -1, currentBase = -1;
                 List<String> list = out.asLines();
                 /* We tried to have CompressedOops settings as per the test requirement,
                  * but it ultimately depends on OS and is not guaranteed that we have got the desired settings.
                  * So we parse the log output from the production run to get the real settings.
                  *
                  * Parse the following Xlog:cds output to get the values of CompressedOops::base and CompressedOops::shift
                  * used during the AOTCache assembly and production run:
                  *
                  *    [0.022s][info][cds] CDS archive was created with max heap size = 1024M, and the following configuration:
                  *    [0.022s][info][cds]     narrow_klass_base at mapping start address, narrow_klass_pointer_bits = 32, narrow_klass_shift = 0
                  *    [0.022s][info][cds]     narrow_oop_mode = 1, narrow_oop_base = 0x0000000000000000, narrow_oop_shift = 3
                  *    [0.022s][info][cds] The current max heap size = 31744M, G1HeapRegion::GrainBytes = 16777216
                  *    [0.022s][info][cds]     narrow_klass_base = 0x000007fc00000000, arrow_klass_pointer_bits = 32, narrow_klass_shift = 0
                  *    [0.022s][info][cds]     narrow_oop_mode = 3, narrow_oop_base = 0x0000000300000000, narrow_oop_shift = 3
                  *    [0.022s][info][cds]     heap range = [0x0000000301000000 - 0x0000000ac1000000]
                  */
                 Pattern p = Pattern.compile("narrow_oop_base = 0x([0-9a-fA-F]+), narrow_oop_shift = (\\d)");
                 for (int i = 0; i < list.size(); i++) {
                     String line = list.get(i);
                     if (line.indexOf("CDS archive was created with max heap size") != -1) {
                         // Parse AOT Cache CompressedOops settings
                         line = list.get(i+2);
                         Matcher m = p.matcher(line);
                         if (!m.find()) {
                             throw new RuntimeException("Pattern \"" + p + "\" not found in the output");
                         }
                         aotCacheBase = Long.valueOf(m.group(1), 16);
                         aotCacheShift = Integer.valueOf(m.group(2));
                         // Parse current CompressedOops settings
                         line = list.get(i+5);
                         m = p.matcher(line);
                         if (!m.find()) {
                             throw new RuntimeException("Pattern \"" + p + "\" not found in the output");
                         }
                         currentBase = Long.valueOf(m.group(1), 16);
                         currentShift = Integer.valueOf(m.group(2));
                         break;
                     }
                 }
                 if (aotCacheShift == -1 || currentShift == -1 || aotCacheBase == -1 || currentBase == -1) {
                     throw new RuntimeException("Failed to find CompressedOops settings");
                 }
                 if (aotCacheShift != currentShift) {
                     out.shouldContain("AOT Code Cache disabled: it was created with different CompressedOops::shift()");
                 } else if ((aotCacheBase == 0 || currentBase == 0) && (aotCacheBase != currentBase)) {
                     out.shouldContain("AOTStubCaching is disabled: incompatible CompressedOops::base()");
                 } else {
                     out.shouldMatch("Read \\d+ entries table at offset \\d+ from AOT Code Cache");
                 }
            }
        }
    }
}
