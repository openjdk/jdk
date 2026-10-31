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
 */

package compiler.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test id=c1
 * @key stress randomness
 * @bug 8381751
 * @requires vm.compiler1.enabled & vm.flagless
 * @summary Tests that StressExcludeCompilation and StressExcludeInlining exclude
 *          methods from C1 and that the excluded set is reproducible for a given seed.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressExclude c1
 */

/*
 * @test id=c2
 * @key stress randomness
 * @bug 8381751
 * @requires vm.compiler2.enabled & vm.flagless
 * @summary Tests that StressExcludeCompilation and StressExcludeInlining exclude
 *          methods from C2 and that the excluded set is reproducible for a given seed.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressExclude c2
 */

public class TestStressExclude {

    static final String WORKLOAD = Workload.class.getName();
    static final Pattern METHOD = Pattern.compile("Workload::(m\\d+)");
    static final Set<String> ALL_METHODS = Set.of("m0", "m1", "m2", "m3", "m4", "m5", "m6", "m7");

    static Set<String> excluded(String compiler, String stressFlag, String printFlag,
                                String compileOnly, int mean, String seed) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(compiler.equals("c1") ? "-XX:TieredStopAtLevel=1" : "-XX:-TieredCompilation");
        args.addAll(List.of("-Xcomp", "-XX:+UnlockDiagnosticVMOptions",
                            "-XX:CompileOnly=" + WORKLOAD + "::" + compileOnly,
                            "-XX:+" + stressFlag,
                            "-XX:StressExcludeMean=" + mean,
                            "-XX:StressSeed=" + seed,
                            "-XX:+" + printFlag,
                            WORKLOAD));
        OutputAnalyzer out = ProcessTools.executeLimitedTestJava(args);
        out.shouldHaveExitValue(0);
        String marker = "excluded by " + stressFlag;
        Set<String> methods = new TreeSet<>();
        for (String line : out.asLines()) {
            if (line.contains(marker)) {
                Matcher m = METHOD.matcher(line);
                if (m.find()) {
                    methods.add(m.group(1));
                }
            }
        }
        return methods;
    }

    static Set<String> excludedFromCompilation(String compiler, int mean, String seed) throws Exception {
        return excluded(compiler, "StressExcludeCompilation", "PrintCompilation", "m*", mean, seed);
    }

    static Set<String> excludedFromInlining(String compiler, int mean, String seed) throws Exception {
        return excluded(compiler, "StressExcludeInlining", "PrintInlining", "caller", mean, seed);
    }

    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        String seed = Integer.toUnsignedString(Utils.getRandomInstance().nextInt());

        Asserts.assertEquals(ALL_METHODS, excludedFromCompilation(compiler, 1, seed));
        Asserts.assertEquals(ALL_METHODS, excludedFromInlining(compiler, 1, seed));

        Asserts.assertEquals(excludedFromCompilation(compiler, 2, seed),
                             excludedFromCompilation(compiler, 2, seed));
        Asserts.assertEquals(excludedFromInlining(compiler, 2, seed),
                             excludedFromInlining(compiler, 2, seed));
    }

    public static class Workload {
        static int m0(int x) { return x + 1; }
        static int m1(int x) { return x * 2; }
        static int m2(int x) { return x - 3; }
        static int m3(int x) { return x ^ 4; }
        static int m4(int x) { return x | 5; }
        static int m5(int x) { return x & 6; }
        static int m6(int x) { return x << 1; }
        static int m7(int x) { return x >> 1; }

        static int caller(int x) {
            return m0(x) + m1(x) + m2(x) + m3(x) + m4(x) + m5(x) + m6(x) + m7(x);
        }

        public static void main(String[] args) {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += m0(i) + m1(i) + m2(i) + m3(i) + m4(i) + m5(i) + m6(i) + m7(i);
                sum += caller(i);
            }
            System.out.println(sum);
        }
    }
}
