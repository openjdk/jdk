/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules jdk.vm.ci/jdk.vm.ci.hotspot
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.DebugOutputTest
 * @run main/othervm compiler.jvmci.compilerToVM.DebugOutputTest
 */

 // as soon as CODETOOLS-7901589 fixed, '@run main/othervm' should be replaced w/ '@run driver'

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.ProcessTools;
import java.util.Arrays;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Utils;

public class DebugOutputTest {
    public static void main(String[] args) {
        new DebugOutputTest().test();
    }

    private void test() {
        for (TestCaseData testCase : TestCaseData.values()) {
            System.out.println(testCase);
            OutputAnalyzer oa;
            try {
                oa = ProcessTools.executeTestJvmAllArgs(
                        "-XX:+UnlockExperimentalVMOptions",
                        "-XX:+EnableJVMCI",
                        "-Xbootclasspath/a:.",
                        DebugOutputTest.Worker.class.getName(),
                        testCase.name());
               } catch (Throwable e) {
                e.printStackTrace();
                throw new Error("Problems running child process", e);
            }
            if (testCase.expectedException != null) {
                oa.shouldHaveExitValue(1);
                oa.shouldContain(testCase.expectedException.getName());
            } else {
                oa.shouldHaveExitValue(0);
                oa.shouldContain(new String(testCase.getExpected()));
            }
        }
    }

    /**
     * A list of test cases that are executed in forked VM
     */
    private enum TestCaseData {
        PART_ARRAY(100, 50),
        FULL_ARRAY(0, 255),
        EMPTY(0, 0),
        NEGATIVE_LENGTH(0, Integer.MIN_VALUE,
                ArrayIndexOutOfBoundsException.class),
        NEGATIVE_OFFSET(-1, 255,
                ArrayIndexOutOfBoundsException.class),
        LEFT_BOUND(Integer.MIN_VALUE, 100,
                ArrayIndexOutOfBoundsException.class),
        RIGHT_BOUND(Integer.MAX_VALUE, 100,
                ArrayIndexOutOfBoundsException.class),
        BIG_LENGTH(0, Integer.MAX_VALUE,
                ArrayIndexOutOfBoundsException.class),
        NULL_POINTER(0, 0,
                NullPointerException.class),
        ;

        private static final int SIZE = 255;
        private static final byte[] DATA = generate();
        public final int offset;
        public final int length;
        public final Class<? extends Throwable> expectedException;

        private TestCaseData(int offset, int length,
                Class<? extends Throwable> expectedException) {
            this.offset = offset;
            this.length = length;
            this.expectedException = expectedException;
        }

        private TestCaseData(int offset, int length) {
            this(offset, length, null);
        }

        private static byte[] generate() {
            byte[] byteArray = new byte[SIZE];
            for (int i = 0; i < SIZE; i++) {
                byteArray[i] = (byte) (i + 1);
            }
            return byteArray;
        }

        public byte[] getExpected() {
            if (expectedException != null) {
                return new byte[0];
            }
            return Arrays.copyOfRange(TestCaseData.DATA, offset,
                    offset + length);
        }

        @Override
        public String toString() {
            return "CASE: " + this.name();
        }

        public byte[] getData() {
            if (equals(NULL_POINTER)) {
                return null;
            } else {
                return DATA;
            }
        }
    }

    public static class Worker {
        public static void main(String[] args) {
            for (String arg : args) {
                TestCaseData tcase = TestCaseData.valueOf(arg);
                CompilerToVMHelper.writeDebugOutput(tcase.getData(),
                        tcase.offset, tcase.length);
                CompilerToVMHelper.flushDebugOutput();
            }
        }
    }
}
