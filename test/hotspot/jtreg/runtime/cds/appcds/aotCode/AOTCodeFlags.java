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
 * @test id=default_gc
 * @requires vm.gc != "Z"
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeFlags
 */
/**
 * @test id=Z
 * @requires vm.gc.Z
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeFlags Z
 */
/**
 * @test id=shenandoah
 * @requires vm.gc.Shenandoah
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeFlags Shenandoah
 */
/**
 * @test id=parallel
 * @requires vm.gc.Parallel
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeFlags Parallel
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeFlags {
    private static String gcName = null;
    public static void main(String... args) throws Exception {
        Tester t = new Tester(args.length == 0 ? null : args[0]);
        // mode bits 0 and 1 encode AOTAdapterCaching and AOTStubCaching settings
        // aMode is used for assembly run, pMode for production run
        for (int aMode = 0; aMode < 4; aMode++) {
            for (int pMode = 0; pMode < 4; pMode++) {
                t.setTestMode(aMode, pMode);
                t.run(new String[] {"AOT", "--two-step-training"});
            }
        }
    }
    static class Tester extends CDSAppTester {
        private int aMode, pMode;
        private String gcName;

        public Tester(String name) {
            super("AOTCodeFlags");
            aMode = 0;
            pMode = 0;
            gcName = name;
        }

        boolean isAdapterCachingOn(int mode) {
            return (mode & 0x1) != 0;
        }

        boolean isStubCachingOn(int mode) {
            return (mode & 0x2) != 0;
        }

        public void setTestMode(int aMode, int pMode) {
            this.aMode = aMode;
            this.pMode = pMode;
        }

        public List<String> getVMArgsForTestMode(int mode) {
            List<String> list = new ArrayList<String>();
            list.add("-XX:+UnlockDiagnosticVMOptions");
            list.add(isAdapterCachingOn(mode) ? "-XX:+AOTAdapterCaching" : "-XX:-AOTAdapterCaching");
            list.add(isStubCachingOn(mode) ? "-XX:+AOTStubCaching" : "-XX:-AOTStubCaching");
            return list;
        }

        public List<String> getGCArgs() {
            List<String> args = new ArrayList<String>();
            args.add("-Xmx100M");
            if (gcName == null) {
                return args;
            }
            switch (gcName) {
            case "G1":
            case "Z":
            case "Shenandoah":
            case "Parallel":
                args.add("-XX:+Use" + gcName + "GC");
            return args;
            default:
                throw new RuntimeException("Unexpected GC name " + gcName);
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            List<String> args = getGCArgs();
            args.addAll(List.of("-Xlog:aot+codecache+init=debug",
                                "-Xlog:aot+codecache+exit=debug",
                                "-Xlog:aot+codecache+stubs=debug"));
            switch (runMode) {
            case RunMode.ASSEMBLY:
                args.addAll(getVMArgsForTestMode(aMode));
                break;
            case RunMode.PRODUCTION:
                args.addAll(getVMArgsForTestMode(pMode));
                break;
            default:
                break;
            }
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { "JavacBenchApp", "10" };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                if (!isAdapterCachingOn(aMode) && !isStubCachingOn(aMode)) { // this is equivalent to completely disable AOT code cache
                    out.shouldNotMatch("Adapters:\\s+total");
                    out.shouldNotMatch("Shared Blobs:\\s+total");
                    out.shouldNotMatch("C1 Blobs:\\s+total");
                    out.shouldNotMatch("C2 Blobs:\\s+total");
                } else {
                    if (isAdapterCachingOn(aMode)) {
                        // AOTAdapterCaching is on, non-zero adapters should be stored
                        out.shouldMatch("Adapters:\\s+total=[1-9][0-9]+");
                    } else {
                        // AOTAdapterCaching is off, no adapters should be stored
                        out.shouldMatch("Adapters:\\s+total=0");
                    }
                    if (isStubCachingOn(aMode)) {
                        // AOTStubCaching is on, non-zero stubs should be stored
                        out.shouldMatch("Shared Blobs:\\s+total=[1-9][0-9]+");
                        out.shouldMatch("C1 Blobs:\\s+total=[1-9][0-9]+");
                        // we do not currently load or store C2 stubs
                        // because we are seeing weird memory errors
                        // when loading them -- see JDK-8357593
                        out.shouldMatch("C2 Blobs:\\s+total=0");
                    } else {
                        // AOTStubCaching is off, no stubs should be stored
                        out.shouldMatch("Shared Blobs:\\s+total=0");
                        out.shouldMatch("C1 Blobs:\\s+total=0");
                        out.shouldMatch("C2 Blobs:\\s+total=0");
                    }
                }
            } else if (runMode == RunMode.PRODUCTION) {
                // Irrespective of assembly run mode, if both adapter and stub caching is disabled
                // in production run, then it is equivalent to completely disabling AOT code cache
                if (!isAdapterCachingOn(pMode) && !isStubCachingOn(pMode)) {
                    out.shouldNotMatch("Adapters:\\s+total");
                    out.shouldNotMatch("Shared Blobs:\\s+total");
                    out.shouldNotMatch("C1 Blobs:\\s+total");
                    out.shouldNotMatch("C2 Blobs:\\s+total");
                } else {
                    // If AOT code cache is effectively disabled in the assembly run, then production run
                    // would emit empty code cache message.
                    if (!isAdapterCachingOn(aMode) && !isStubCachingOn(aMode)) {
                        if (isAdapterCachingOn(pMode) || isStubCachingOn(pMode)) {
                            out.shouldMatch("AOT Code Cache is empty");
                        }
                    } else {
                        if (isAdapterCachingOn(aMode)) {
                            if (isAdapterCachingOn(pMode)) {
                                out.shouldMatch("Read blob.*kind=Adapter.*");
                            } else {
                                out.shouldNotMatch("Read blob.*kind=Adapter.*");
                            }
                        }
                        if (isStubCachingOn(aMode)) {
                            if (isStubCachingOn(pMode)) {
                                out.shouldMatch("Read blob.*kind=SharedBlob.*");
                                out.shouldMatch("Read blob.*kind=C1Blob.*");
                            } else {
                                out.shouldNotMatch("Read blob.*kind=SharedBlob.*");
                                out.shouldNotMatch("Read blob.*kind=C1Blob.*");
                            }
                        }
                    }
                }
            }
        }
    }
}
