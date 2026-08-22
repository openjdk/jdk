/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package compiler.calls;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * Shared base for the static call stub sizing tests:
 *
 *   TestStaticCallStubSizingC1     -- C1: -XX:TieredStopAtLevel=1
 *   TestStaticCallStubSizingC2     -- C2: -XX:-TieredCompilation
 *   TestStaticCallStubSizingStress -- C2 + -XX:+StressCodeBuffers
 */
public class TestStaticCallStubSizingBase {

    static final int STATIC_CALLEES = 40;
    static final int VIRTUAL_CALLEES = 16;
    static final int EXPECTED =
        STATIC_CALLEES * (STATIC_CALLEES - 1) / 2
        + VIRTUAL_CALLEES * 3
        + VIRTUAL_CALLEES * (VIRTUAL_CALLEES - 1) / 2;

    static class Receiver {
        public int v00(int a, int b) { return a + b + 0; }
        public int v01(int a, int b) { return a + b + 1; }
        public int v02(int a, int b) { return a + b + 2; }
        public int v03(int a, int b) { return a + b + 3; }
        public int v04(int a, int b) { return a + b + 4; }
        public int v05(int a, int b) { return a + b + 5; }
        public int v06(int a, int b) { return a + b + 6; }
        public int v07(int a, int b) { return a + b + 7; }
        public int v08(int a, int b) { return a + b + 8; }
        public int v09(int a, int b) { return a + b + 9; }
        public int v10(int a, int b) { return a + b + 10; }
        public int v11(int a, int b) { return a + b + 11; }
        public int v12(int a, int b) { return a + b + 12; }
        public int v13(int a, int b) { return a + b + 13; }
        public int v14(int a, int b) { return a + b + 14; }
        public int v15(int a, int b) { return a + b + 15; }
    }

    private static int s00(int a) { return a; }
    private static int s01(int a) { return a; }
    private static int s02(int a) { return a; }
    private static int s03(int a) { return a; }
    private static int s04(int a) { return a; }
    private static int s05(int a) { return a; }
    private static int s06(int a) { return a; }
    private static int s07(int a) { return a; }
    private static int s08(int a) { return a; }
    private static int s09(int a) { return a; }
    private static int s10(int a) { return a; }
    private static int s11(int a) { return a; }
    private static int s12(int a) { return a; }
    private static int s13(int a) { return a; }
    private static int s14(int a) { return a; }
    private static int s15(int a) { return a; }
    private static int s16(int a) { return a; }
    private static int s17(int a) { return a; }
    private static int s18(int a) { return a; }
    private static int s19(int a) { return a; }
    private static int s20(int a) { return a; }
    private static int s21(int a) { return a; }
    private static int s22(int a) { return a; }
    private static int s23(int a) { return a; }
    private static int s24(int a) { return a; }
    private static int s25(int a) { return a; }
    private static int s26(int a) { return a; }
    private static int s27(int a) { return a; }
    private static int s28(int a) { return a; }
    private static int s29(int a) { return a; }
    private static int s30(int a) { return a; }
    private static int s31(int a) { return a; }
    private static int s32(int a) { return a; }
    private static int s33(int a) { return a; }
    private static int s34(int a) { return a; }
    private static int s35(int a) { return a; }
    private static int s36(int a) { return a; }
    private static int s37(int a) { return a; }
    private static int s38(int a) { return a; }
    private static int s39(int a) { return a; }

    static int work(Receiver recv) {
        int sum = 0;
        sum += s00(0);
        sum += s01(1);
        sum += s02(2);
        sum += s03(3);
        sum += s04(4);
        sum += s05(5);
        sum += s06(6);
        sum += s07(7);
        sum += s08(8);
        sum += s09(9);
        sum += s10(10);
        sum += s11(11);
        sum += s12(12);
        sum += s13(13);
        sum += s14(14);
        sum += s15(15);
        sum += s16(16);
        sum += s17(17);
        sum += s18(18);
        sum += s19(19);
        sum += s20(20);
        sum += s21(21);
        sum += s22(22);
        sum += s23(23);
        sum += s24(24);
        sum += s25(25);
        sum += s26(26);
        sum += s27(27);
        sum += s28(28);
        sum += s29(29);
        sum += s30(30);
        sum += s31(31);
        sum += s32(32);
        sum += s33(33);
        sum += s34(34);
        sum += s35(35);
        sum += s36(36);
        sum += s37(37);
        sum += s38(38);
        sum += s39(39);

        sum += recv.v00(1, 2);
        sum += recv.v01(1, 2);
        sum += recv.v02(1, 2);
        sum += recv.v03(1, 2);
        sum += recv.v04(1, 2);
        sum += recv.v05(1, 2);
        sum += recv.v06(1, 2);
        sum += recv.v07(1, 2);
        sum += recv.v08(1, 2);
        sum += recv.v09(1, 2);
        sum += recv.v10(1, 2);
        sum += recv.v11(1, 2);
        sum += recv.v12(1, 2);
        sum += recv.v13(1, 2);
        sum += recv.v14(1, 2);
        sum += recv.v15(1, 2);
        return sum;
    }

    static void run() {
        int result = work(new Receiver());
        if (result != EXPECTED) {
            throw new RuntimeException("Wrong result: got " + result
                                       + " expected " + EXPECTED);
        }
        System.out.println("workload OK");
    }

    static void runVM(String... extraFlags) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockDiagnosticVMOptions");
        for (String f : extraFlags) {
            args.add(f);
        }
        args.add("-Xcomp");
        args.add("-XX:-BackgroundCompilation");
        args.add("-XX:+PrintBailouts");

        String cls = TestStaticCallStubSizingBase.class.getName();
        args.add("-XX:CompileCommand=compileonly," + cls + "::work");
        for (int k = 0; k < STATIC_CALLEES; k++) {
            args.add("-XX:CompileCommand=dontinline," + cls
                     + String.format("::s%02d", k));
        }
        for (int k = 0; k < VIRTUAL_CALLEES; k++) {
            args.add("-XX:CompileCommand=dontinline," + cls + "$Receiver"
                     + String.format("::v%02d", k));
        }
        args.add(cls);
        args.add("run");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        out.shouldHaveExitValue(0);
        // C1 prints "compilation bailout: <msg>" under -XX:+PrintBailouts. Both
        // stubs-section bailout messages of LIR_Assembler::emit_static_call_stub
        // must be absent: neither the dispatch adapter nor the stub itself may
        // run out of reserved space.
        out.shouldNotContain("static call dispatch adapter overflow");
        out.shouldNotContain("static call stub overflow");
        out.shouldNotContain("stub too big");
        out.shouldContain("workload OK");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("run")) {
            run();
        }
    }
}
