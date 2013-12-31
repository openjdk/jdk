/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.java.testlibrary.Platform;

import java.io.FileOutputStream;
import java.lang.reflect.Executable;
import java.util.Properties;

public abstract class IntrinsicBase extends CompilerWhiteBoxTest {
    protected String javaVmName;
    protected String useMathExactIntrinsics;

    protected IntrinsicBase(TestCase testCase) {
        super(testCase);
        javaVmName = System.getProperty("java.vm.name");
        useMathExactIntrinsics = getVMOption("UseMathExactIntrinsics");
    }

    @Override
    protected void test() throws Exception {
        //java.lang.Math should be loaded to allow a compilation of the methods that use Math's method
        System.out.println("class java.lang.Math should be loaded. Proof: " + Math.class);
        printEnvironmentInfo();

        int expectedIntrinsicCount = 0;

        switch (MODE) {
            case "compiled mode":
            case "mixed mode":
                if (isServerVM()) {
                    if (TIERED_COMPILATION) {
                        int max_level = TIERED_STOP_AT_LEVEL;
                        expectedIntrinsicCount = (max_level == COMP_LEVEL_MAX) ? 1 : 0;
                        for (int i = CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE; i <= max_level; ++i) {
                            deoptimize();
                            compileAtLevel(i);
                        }
                    } else {
                        expectedIntrinsicCount = 1;
                        deoptimize();
                        compileAtLevel(CompilerWhiteBoxTest.COMP_LEVEL_MAX);
                    }
                } else {
                    deoptimize();
                    compileAtLevel(CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
                }

                if (!isIntrinsicSupported()) {
                    expectedIntrinsicCount = 0;
                }
                break;
            case "interpreted mode": //test is not applicable in this mode;
                System.err.println("Warning: This test is not applicable in mode: " + MODE);
                break;
            default:
                throw new RuntimeException("Test bug, unknown VM mode: " + MODE);
        }

        System.out.println("Expected intrinsic count is " + expectedIntrinsicCount + " name " + getIntrinsicId());

        final FileOutputStream out = new FileOutputStream(getVMOption("LogFile") + ".verify.properties");
        Properties expectedProps = new Properties();
        expectedProps.setProperty("intrinsic.name", getIntrinsicId());
        expectedProps.setProperty("intrinsic.expectedCount", String.valueOf(expectedIntrinsicCount));
        expectedProps.store(out, null);

        out.close();
    }

    protected void printEnvironmentInfo() {
        System.out.println("java.vm.name=" + javaVmName);
        System.out.println("os.arch=" + Platform.getOsArch());
        System.out.println("java.vm.info=" + MODE);
        System.out.println("useMathExactIntrinsics=" + useMathExactIntrinsics);
    }

    protected void compileAtLevel(int level) {
        WHITE_BOX.enqueueMethodForCompilation(method, level);
        waitBackgroundCompilation();
        checkCompilation(method, level);
    }

    protected void checkCompilation(Executable executable, int level) {
        if (!WHITE_BOX.isMethodCompiled(executable)) {
            throw new RuntimeException("Test bug, expected compilation (level): " + level + ", but not compiled");
        }
        final int compilationLevel = WHITE_BOX.getMethodCompilationLevel(executable);
        if (compilationLevel != level) {
            if (!(TIERED_COMPILATION && level == COMP_LEVEL_FULL_PROFILE && compilationLevel == COMP_LEVEL_LIMITED_PROFILE)) { //possible case
                throw new RuntimeException("Test bug, expected compilation (level): " + level + ", but level: " + compilationLevel);
            }
        }
    }

    protected abstract boolean isIntrinsicSupported();

    protected abstract String getIntrinsicId();

    protected boolean isServerVM() {
        return javaVmName.toLowerCase().contains("server");
    }

    static class IntTest extends IntrinsicBase {
        protected IntTest(MathIntrinsic.IntIntrinsic testCase) {
            super(testCase);
        }

        @Override
        protected boolean isIntrinsicSupported() {
            return isServerVM() && Boolean.valueOf(useMathExactIntrinsics) && (Platform.isX86() || Platform.isX64());
        }

        @Override
        protected String getIntrinsicId() {
            return "_" + testCase.name().toLowerCase() + "ExactI";
        }
    }

    static class LongTest extends IntrinsicBase {
        protected LongTest(MathIntrinsic.LongIntrinsic testCase) {
            super(testCase);
        }

        @Override
        protected boolean isIntrinsicSupported() {
            return isServerVM() && Boolean.valueOf(useMathExactIntrinsics) && Platform.isX64();
        }

        @Override
        protected String getIntrinsicId() {
            return "_" + testCase.name().toLowerCase() + "ExactL";
        }
    }
}
