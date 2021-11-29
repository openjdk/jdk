/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import jdk.test.whitebox.cpuinfo.CPUInfo;

public abstract class StressArrayCopyDriver {

    // These tests are remarkably memory bandwidth hungry. Running multiple
    // configs in parallel makes sense only when running a single test in
    // isolation, and only on machines with many memory channels. In common
    // testing, or even running all arraycopy stress tests at once, overloading
    // the system with many configs become counter-productive very quickly.
    //
    // The sweet-spot seems to be 2. Default to it, and allow users to override.
    static final int MAX_PARALLELISM = Integer.getInteger("maxParallelism", 2);

    private static List<String> mix(List<String> o, String... mix) {
        List<String> n = new ArrayList<>(o);
        for (String m : mix) {
            n.add(m);
        }
        return n;
    }

    private static List<List<String>> product(List<List<String>> list, String... mix) {
        List<List<String>> newList = new ArrayList<>();
        for (List<String> c : list) {
            for (String m : mix) {
                newList.add(mix(c, m));
            }
        }
        return newList;
    }

    private static List<List<String>> alternate(List<List<String>> list, String opt) {
        return product(list, "-XX:+" + opt, "-XX:-" + opt);
    }

    private static boolean containsFuzzy(List<String> list, String sub) {
        for (String s : list) {
            if (s.contains(sub)) return true;
        }
        return false;
    }

    public static void main(String... args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Should provide at least 1 argument");
        }

        String className = args[0];

        List<List<String>> configs = new ArrayList<>();
        List<String> cpuFeatures = CPUInfo.getFeatures();

        if (Platform.isX64() || Platform.isX86()) {
            // If CPU features were not found, provide a default config.
            if (cpuFeatures.isEmpty()) {
                configs.add(new ArrayList());
            }

            // Otherwise, select the tests that make sense on current platform.
            if (containsFuzzy(cpuFeatures, "avx512")) {
                configs.add(List.of("-XX:UseAVX=3"));
            }
            if (containsFuzzy(cpuFeatures, "avx2")) {
                configs.add(List.of("-XX:UseAVX=2"));
            }
            if (containsFuzzy(cpuFeatures, "avx")) {
                configs.add(List.of("-XX:UseAVX=1"));
            }
            if (containsFuzzy(cpuFeatures, "sse4")) {
                configs.add(List.of("-XX:UseAVX=0", "-XX:UseSSE=4"));
            }
            if (containsFuzzy(cpuFeatures, "sse3")) {
                configs.add(List.of("-XX:UseAVX=0", "-XX:UseSSE=3"));
            }
            if (containsFuzzy(cpuFeatures, "sse2")) {
                configs.add(List.of("-XX:UseAVX=0", "-XX:UseSSE=2"));
            }

            // x86_64 always has UseSSE >= 2. These lower configurations only
            // make sense for x86_32.
            if (Platform.isX86()) {
                if (containsFuzzy(cpuFeatures, "sse")) {
                    configs.add(List.of("-XX:UseAVX=0", "-XX:UseSSE=1"));
                }

                configs.add(List.of("-XX:UseAVX=0", "-XX:UseSSE=0"));
            }

            // Alternate configs with other flags
            if (Platform.isX64()) {
                configs = alternate(configs, "UseCompressedOops");
            }
            configs = alternate(configs, "UseUnalignedLoadStores");

        } else if (Platform.isAArch64()) {
            // AArch64.
            configs.add(new ArrayList());

            // Alternate configs with other flags
            configs = alternate(configs, "UseCompressedOops");
            configs = alternate(configs, "UseSIMDForMemoryOps");
        } else {
            // Generic config.
            configs.add(new ArrayList());
        }

        Deque<OutputAnalyzer> oas = new ArrayDeque<>();
        int jobs = 0;

        for (List<String> c : configs) {
            ProcessBuilder pb = ProcessTools.createTestJvm(mix(c, "-Xmx256m", className));
            OutputAnalyzer oa = new OutputAnalyzer(pb.start());
            oas.addLast(oa);
            if (++jobs >= MAX_PARALLELISM) {
                // Pop and block wait
                oa = oas.pollFirst();
                oa.shouldHaveExitValue(0);
                jobs--;
            }
        }

        // Drain the rest
        while (!oas.isEmpty()) {
            OutputAnalyzer oa = oas.pollFirst();
            oa.shouldHaveExitValue(0);
        }
    }

}
