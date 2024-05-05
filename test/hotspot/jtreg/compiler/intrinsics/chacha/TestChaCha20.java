/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics.chacha;

import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.cpuinfo.CPUInfo;

/**
 * @test
 * @bug 8247645
 * @summary ChaCha20 Intrinsics
 * @library /test/lib
 * @requires (vm.cpu.features ~= ".*avx512.*" | vm.cpu.features ~= ".*avx2.*" | vm.cpu.features ~= ".*avx.*") |
 *           (os.arch=="aarch64" & vm.cpu.features ~= ".*simd.*") |
 *           (os.arch == "riscv64" & vm.cpu.features ~= ".*v,.*")
 * @build   compiler.intrinsics.chacha.ExerciseChaCha20
 *          jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=7200
 *      -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.intrinsics.chacha.TestChaCha20
 */
public class TestChaCha20 {

    // Default to 1/4 of the CPUs, and allow users to override.
    static final int MAX_PARALLELISM = Integer.getInteger("maxParallelism",
        Math.max(1, Runtime.getRuntime().availableProcessors() / 4));

    private static List<String> mix(List<String> o, String... mix) {
        List<String> n = new ArrayList<>(o);
        for (String m : mix) {
            n.add(m);
        }
        return n;
    }

    private static boolean containsFuzzy(List<String> list, String sub, Boolean matchExactly) {
        for (String s : list) {
            if (matchExactly) {
                if (s.equals(sub)) return true;
            } else {
                if (s.contains(sub)) return true;
            }
        }
        return false;
    }

    public static void main(String... args) throws Exception {
        List<List<String>> configs = new ArrayList<>();
        List<String> cpuFeatures = CPUInfo.getFeatures();

        System.out.print("CPU Features: ");
        cpuFeatures.forEach(f -> System.out.print(f + " "));
        System.out.println();

        if (Platform.isX64()) {
            // If CPU features were not found, provide a default config.
            if (cpuFeatures.isEmpty()) {
                configs.add(new ArrayList());
            }

            // Otherwise, select the tests that make sense on current platform.
            if (containsFuzzy(cpuFeatures, "avx512", false)) {
                System.out.println("Setting up AVX512 worker");
                configs.add(List.of("-XX:UseAVX=3"));
            }
            if (containsFuzzy(cpuFeatures, "avx2", false)) {
                System.out.println("Setting up AVX2 worker");
                configs.add(List.of("-XX:UseAVX=2"));
            }
            if (containsFuzzy(cpuFeatures, "avx", false)) {
                System.out.println("Setting up AVX worker");
                configs.add(List.of("-XX:UseAVX=1"));
            }
        } else if (Platform.isAArch64()) {
            // AArch64 intrinsics require the advanced simd instructions
            if (containsFuzzy(cpuFeatures, "simd", false)) {
                System.out.println("Setting up ASIMD worker");
                configs.add(new ArrayList());
            }
        } else if (Platform.isRISCV64()) {
            // Riscv64 intrinsics require the vector instructions
            if (containsFuzzy(cpuFeatures, "v", true)) {
                System.out.println("Setting up vector worker");
                configs.add(List.of("-XX:+UseRVV"));
            }
        } else {
            // We only have ChaCha20 intrinsics on x64, aarch64 and riscv64
            // currently.  If the platform is neither of these then
            // the ChaCha20 known answer tests in
            // com/sun/crypto/provider/Cipher are sufficient.
            return;
        }

        // If by this point we have no configs, it means we are running
        // on a platform that intrinsics have been written for, but does
        // not possess the necessary instruction sets for that processor.
        // We can exit out if that is the case.
        if (configs.isEmpty()) {
            System.out.println("No intrinsics-capable configurations found");
            return;
        }

        // We can expand this array later to include other tests if new
        // ChaCha20 intrinsics are developed.
        String[] classNames = {
            "compiler.intrinsics.chacha.ExerciseChaCha20"
        };

        ArrayList<Fork> forks = new ArrayList<>();
        int jobs = 0;

        for (List<String> c : configs) {
            for (String className : classNames) {
                // Start a new job
                {
                    ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                            mix(c, "-Xmx256m", className));
                    Process p = pb.start();
                    OutputAnalyzer oa = new OutputAnalyzer(p);
                    forks.add(new Fork(p, oa));
                    jobs++;
                }

                // Wait for the completion of other jobs
                while (jobs >= MAX_PARALLELISM) {
                    Fork f = findDone(forks);
                    if (f != null) {
                        OutputAnalyzer oa = f.oa();
                        oa.shouldHaveExitValue(0);
                        forks.remove(f);
                        jobs--;
                    } else {
                        // Nothing is done, wait a little.
                        Thread.sleep(200);
                    }
                }
            }
        }

        // Drain the rest
        for (Fork f : forks) {
            OutputAnalyzer oa = f.oa();
            oa.shouldHaveExitValue(0);
        }
    }

    private static Fork findDone(List<Fork> forks) {
        for (Fork f : forks) {
            if (!f.p().isAlive()) {
                return f;
            }
        }
        return null;
    }

    private static record Fork(Process p, OutputAnalyzer oa) {};
}
