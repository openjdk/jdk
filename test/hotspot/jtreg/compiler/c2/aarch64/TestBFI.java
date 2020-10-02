/*
 * Copyright (c) 2020, BELLSOFT. All rights reserved.
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

package compiler.c2.aarch64;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8249893
 * @summary C2 should use BFI instruction for (var1 & 0xFF) | ((var1 & 0xFF) << 8) expressions
 * @library /test/lib /
 *
 * @run main/othervm compiler.c2.aarch64.TestBFI
 *
 * @requires os.arch=="aarch64" & vm.debug == true & vm.flavor == "server" & !vm.graal.enabled
 */
public class TestBFI {

    static void runTest(String testName, int expectedCommandsNumber, boolean useBFI) throws Exception {
        String className = "compiler.c2.aarch64.TestBFI";
        String[] procArgs = {
            "-XX:+PrintOptoAssembly", "-XX:-TieredCompilation", "-Xbatch",
            "-XX:CompileCommand=compileonly,compiler.c2.aarch64." + testName,
            className, testName};

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        String bitManipulation = ".*(bfi|lsl|andsw|orrw).*";
        System.out.println(testName + " instructions:");
        output.asLines().stream().filter(it -> it.matches(bitManipulation)).forEach(System.out::println);

        long count = output.asLines().stream().filter(it -> it.matches(bitManipulation)).count();
        if (expectedCommandsNumber != count) {
            output.outputTo(System.out);
            throw new RuntimeException("unexpected instructions count: " + count);
        }

        if (useBFI) {
          output.shouldContain("bfi");
        }
        output.shouldHaveExitValue(0);
    }

    static final int ITER = 40_000; // ~ Tier4CompileThreshold + compilation time

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Fork VMs to check their debug compiler output
            runTest("Color::<init>",        Color.instrCount,        Color.useBFI);
            runTest("ColorReverse::<init>", ColorReverse.instrCount, ColorReverse.useBFI);
            runTest("BigDecimal::mulsub",   BigDecimal.instrCount,   BigDecimal.useBFI);
        }
        if (args.length > 0) {
            // We are in a forked VM to execute the named test
            String testName = args[0];
            switch (testName) {
            case "Color::<init>":
                for (int i = 0; i < ITER; i++) {
                    new Color(i, i, i, i);
                }
                break;
            case "ColorReverse::<init>":
                for (int i = 0; i < ITER; i++) {
                    new ColorReverse(i, i, i, i, 0);
                }
                break;
            case "BigDecimal::mulsub":
                for (int i = 0; i < ITER; i++) {
                    BigDecimal.mulsub(i, i, i, i, i);
                }
                break;
            default:
                throw new RuntimeException("unexpected test name " + testName);
            }
        }
    }
}

// java.awt.Color usecase
class Color {
    int value;
    public Color(int r, int g, int b, int a) {
        // 1. lsl   w11, w5, #24
        // 2. bfi   x11, x2, #16, #8
        // 3. bfi   x11, x3, #8, #8
        // 4. bfxil x11, x4, #0, #8
        value = ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8)  |
                ((b & 0xFF) << 0);
    }
    static int instrCount = 4;
    static boolean useBFI = true;
}

// reverse byte order
class ColorReverse {
    int value;
    public ColorReverse(int r, int g, int b, int a, int dummy) {
        // 1. and  w11, w4, #0xff
        // 2. bfi  x11, x3, #8, #8
        // 3. bfi  x11, x2, #16, #8
        // 4. orr  w10, w11, w5, lsl #24
        value = ((a & 0xFF) << 0)   |
                ((r & 0xFF) << 8)   |
                ((g & 0xFF) << 16)  |
                ((b & 0xFF) << 24);
    }
    static int instrCount = 4;
    static boolean useBFI = true;
}

class BigDecimal {
    static final long LONG_MASK = 0xffffffffL;
    static long mulsub(long u1, long u0, final long v1, final long v0, long q0) {
        long tmp = u0 - q0 * v0;
        long tmp1 = u1 + (tmp>>>32) - q0 * v1;
        // 1. lsl R0, R11, #32
        // 2. bfi R0, R10, ##0, ##32
        return (tmp1 << 32) | (tmp & LONG_MASK);
    }
    static int instrCount = 2;
    static boolean useBFI = true;
}

