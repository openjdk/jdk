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
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flavor != "zero"
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @requires vm.flagless
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver AOTCodeFlags
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeFlags {
    public static void main(String... args) throws Exception {
        Tester t = new Tester();
        for (int mode = 0; mode < 3; mode++) {
            t.setTestMode(mode);
            t.run(new String[] {"AOT"});
        }
    }
    static class Tester extends CDSAppTester {
        private int testMode;

        public Tester() {
            super("AOTCodeFlags");
            testMode = 0;
        }

        boolean isAdapterCachingOn() {
            return (testMode & 0x1) != 0;
        }

        boolean isStubCachingOn() {
            return (testMode & 0x2) != 0;
        }

        public void setTestMode(int mode) {
            testMode = mode;
        }

        public List<String> getVMArgsForTestMode() {
            List<String> list = new ArrayList<String>();
            list.add("-XX:+UnlockDiagnosticVMOptions");
            list.add(isAdapterCachingOn() ? "-XX:+AOTAdapterCaching" : "-XX:-AOTAdapterCaching");
            list.add(isStubCachingOn() ? "-XX:+AOTStubCaching" : "-XX:-AOTStubCaching");
            return list;
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            switch (runMode) {
            case RunMode.ASSEMBLY:
            case RunMode.PRODUCTION: {
                    List<String> args = getVMArgsForTestMode();
                    args.addAll(List.of("-Xlog:aot+codecache+init=debug",
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
            if (!isAdapterCachingOn() && !isStubCachingOn()) { // this is equivalent to completely disable AOT code cache
                switch (runMode) {
                case RunMode.ASSEMBLY:
                case RunMode.PRODUCTION:
                    out.shouldNotMatch("Adapters:\\s+total");
                    out.shouldNotMatch("Shared Blobs:\\s+total");
                    out.shouldNotMatch("C1 Blobs:\\s+total");
                    out.shouldNotMatch("C2 Blobs:\\s+total");
                    break;
                }
            } else {
                if (isAdapterCachingOn()) {
                    switch (runMode) {
                    case RunMode.ASSEMBLY:
                    case RunMode.PRODUCTION:
                        // AOTAdapterCaching is on, non-zero adapters should be stored/loaded
                        out.shouldMatch("Adapters:\\s+total=[1-9][0-9]+");
                        break;
                    }
                } else {
                    switch (runMode) {
                    case RunMode.ASSEMBLY:
                    case RunMode.PRODUCTION:
                        // AOTAdapterCaching is off, no adapters should be stored/loaded
                        out.shouldMatch("Adapters:\\s+total=0");
                        break;
                    }
                }
                if (isStubCachingOn()) {
                    switch (runMode) {
                    case RunMode.ASSEMBLY:
                    case RunMode.PRODUCTION:
                        // AOTStubCaching is on, non-zero stubs should be stored/loaded
                        out.shouldMatch("Shared Blobs:\\s+total=[1-9][0-9]+");
                        out.shouldMatch("C1 Blobs:\\s+total=[1-9][0-9]+");
                        out.shouldMatch("C2 Blobs:\\s+total=[1-9][0-9]+");
                        break;
                    }
                } else {
                    switch (runMode) {
                    case RunMode.ASSEMBLY:
                    case RunMode.PRODUCTION:
                        // AOTStubCaching is off, no stubs should be stored/loaded
                        out.shouldMatch("Shared Blobs:\\s+total=0");
                        out.shouldMatch("C1 Blobs:\\s+total=0");
                        out.shouldMatch("C2 Blobs:\\s+total=0");
                        break;
                    }
                }
            }
        }
    }
}
