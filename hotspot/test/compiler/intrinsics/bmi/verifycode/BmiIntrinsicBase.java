/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.java.testlibrary.Asserts;
import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.Utils;
import sun.hotspot.code.NMethod;
import sun.hotspot.cpuinfo.CPUInfo;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class BmiIntrinsicBase extends CompilerWhiteBoxTest {

    protected BmiIntrinsicBase(BmiTestCase testCase) {
        super(testCase);
    }

    public static void verifyTestCase(Function<Method, BmiTestCase> constructor, Method... methods) throws Exception {
        for (Method method : methods) {
            new BmiIntrinsicBase(constructor.apply(method)).test();
        }
    }

    @Override
    protected void test() throws Exception {
        BmiTestCase bmiTestCase = (BmiTestCase) testCase;

        if (!(Platform.isX86() || Platform.isX64())) {
            System.out.println("Unsupported platform, test SKIPPED");
            return;
        }

        if (!Platform.isServer()) {
            System.out.println("Not server VM, test SKIPPED");
            return;
        }

        if (!CPUInfo.hasFeature(bmiTestCase.getCpuFlag())) {
            System.out.println("Unsupported hardware, no required CPU flag " + bmiTestCase.getCpuFlag() + " , test SKIPPED");
            return;
        }

        if (!Boolean.valueOf(getVMOption(bmiTestCase.getVMFlag()))) {
            System.out.println("VM flag " + bmiTestCase.getVMFlag() + " disabled, test SKIPPED");
            return;
        }

        System.out.println(testCase.name());

        switch (MODE) {
            case "compiled mode":
            case "mixed mode":
                if (TIERED_COMPILATION && TIERED_STOP_AT_LEVEL != CompilerWhiteBoxTest.COMP_LEVEL_MAX) {
                    System.out.println("TieredStopAtLevel value (" + TIERED_STOP_AT_LEVEL + ") is too low, test SKIPPED");
                    return;
                }
                deoptimize();
                compileAtLevelAndCheck(CompilerWhiteBoxTest.COMP_LEVEL_MAX);
                break;
            case "interpreted mode": // test is not applicable in this mode;
                System.err.println("Warning: This test is not applicable in mode: " + MODE);
                break;
            default:
                throw new AssertionError("Test bug, unknown VM mode: " + MODE);
        }
    }

    protected void compileAtLevelAndCheck(int level) {
        WHITE_BOX.enqueueMethodForCompilation(method, level);
        waitBackgroundCompilation();
        checkCompilation(method, level);
        checkEmittedCode(method);
    }

    protected void checkCompilation(Executable executable, int level) {
        if (!WHITE_BOX.isMethodCompiled(executable)) {
            throw new AssertionError("Test bug, expected compilation (level): " + level + ", but not compiled" + WHITE_BOX.isMethodCompilable(executable, level));
        }
        final int compilationLevel = WHITE_BOX.getMethodCompilationLevel(executable);
        if (compilationLevel != level) {
            throw new AssertionError("Test bug, expected compilation (level): " + level + ", but level: " + compilationLevel);
        }
    }

    protected void checkEmittedCode(Executable executable) {
        final byte[] nativeCode = NMethod.get(executable, false).insts;
        if (!((BmiTestCase) testCase).verifyPositive(nativeCode)) {
            throw new AssertionError(testCase.name() + "CPU instructions expected not found: " + Utils.toHexString(nativeCode));
        } else {
            System.out.println("CPU instructions found, PASSED");
        }
    }

    abstract static class BmiTestCase implements CompilerWhiteBoxTest.TestCase {
        private final Method method;
        protected byte[] instrMask;
        protected byte[] instrPattern;
        protected boolean isLongOperation;

        public BmiTestCase(Method method) {
            this.method = method;
        }

        @Override
        public String name() {
            return method.toGenericString();
        }

        @Override
        public Executable getExecutable() {
            return method;
        }

        @Override
        public Callable<Integer> getCallable() {
            return null;
        }

        @Override
        public boolean isOsr() {
            return false;
        }

        protected int countCpuInstructions(byte[] nativeCode) {
            int count = 0;
            int patternSize = Math.min(instrMask.length, instrPattern.length);
            boolean found;
            Asserts.assertGreaterThan(patternSize, 0);
            for (int i = 0, n = nativeCode.length - patternSize; i < n; i++) {
                found = true;
                for (int j = 0; j < patternSize; j++) {
                    if ((nativeCode[i + j] & instrMask[j]) != instrPattern[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    ++count;
                    i += patternSize - 1;
                }
            }
            return count;
        }

        public boolean verifyPositive(byte[] nativeCode) {
            final int cnt = countCpuInstructions(nativeCode);
            if (Platform.isX86()) {
                return cnt >= (isLongOperation ? 2 : 1);
            } else {
                return Platform.isX64() && cnt >= 1;
            }
        }

        protected String getCpuFlag() {
            return "bmi1";
        }

        protected String getVMFlag() {
            return "UseBMI1Instructions";
        }
    }
}
