/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=g1
 * @requires vm.gc.G1
 * @summary Sanity test of AOTCodeCache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeTest G1
 */
/**
 * @test id=parallel
 * @requires vm.gc.Parallel
 * @summary Sanity test of AOTCodeCache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeTest Parallel
 */
/**
 * @test id=serial
 * @requires vm.gc.Serial
 * @summary Sanity test of AOTCodeCache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeTest Serial
 */
/**
 * @test id=shenandoah
 * @requires vm.gc.Shenandoah
 * @summary Sanity test of AOTCodeCache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeTest Shenandoah
 */
/**
 * @test id=Z
 * @requires vm.gc.Z
 * @summary Sanity test of AOTCodeCache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @requires vm.opt.VerifyOops == null | vm.opt.VerifyOops == false
 * @comment VerifyOops flag switch off AOT code generation. Skip it.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 AOTCodeTest Z
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeTest {
    private static String gcName = null;
    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("Expected GC name");
        }
        Tester t = new Tester(args[0]);
        t.run(new String[] {"AOT", "--two-step-training"});
    }

    static class Tester extends CDSAppTester {
        private String gcName;

        public Tester(String name) {
            super("AOTCodeTest");
            gcName = name;
        }

        public List<String> getGCArgs() {
            List<String> args = new ArrayList<String>();
            args.add("-Xmx100M");
            switch (gcName) {
            case "G1":
            case "Parallel":
            case "Serial":
            case "Shenandoah":
            case "Z":
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
            // Add flags for logs
            args.addAll(List.of("-Xlog:aot+codecache+init=debug",
                                "-Xlog:aot+codecache+exit=debug",
                                "-Xlog:aot+codecache+stubs=debug"));
            // Add diagnostic flags
            args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                "-XX:+AbortVMOnAOTCodeFailure"));
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { "JavacBenchApp", "10" };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch("aot,codecache,exit.*\\s+AOT code cache size: [1-9]\\d+ bytes");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("aot,codecache,init.*\\s+Loaded [1-9]\\d+ AOT code entries from AOT Code Cache");
                out.shouldMatch("aot,codecache,stubs.*\\s+Read blob.*kind=Adapter.*");
                out.shouldMatch("aot,codecache,stubs.*\\s+Read blob.*kind=SharedBlob.*");
                out.shouldMatch("aot,codecache,stubs.*\\s+Read blob.*kind=C1Blob.*");
            }
        }
    }
}
