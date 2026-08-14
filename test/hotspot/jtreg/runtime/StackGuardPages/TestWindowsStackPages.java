/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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
 * @bug 8067946 8390002
 * @summary Verifies that the Windows thread stack guarantee covers HotSpot's
 *          recoverable stack zones specified using the number of yellow pages
 * @requires os.family == "windows"
 * @library /test/lib
 * @run main/native TestWindowsStackPages
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

public class TestWindowsStackPages {
    private static final String RED_PAGES_FLAG = "StackRedPages";
    private static final String YELLOW_PAGES_FLAG = "StackYellowPages";

    static {
        System.loadLibrary("TestWindowsStackPages");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            checkStackGuarantee(Integer.parseInt(args[0]));
            return;
        }

        // Check the default value.
        ProcessTools.executeTestJava("-XX:+PrintFlagsFinal", "-version")
                    .shouldMatch(YELLOW_PAGES_FLAG + "[ ]+=[ ]+3")
                    .shouldHaveExitValue(0);

        // Check to make sure that the minimum value is three.
        ProcessTools.executeTestJava("-XX:" + YELLOW_PAGES_FLAG + "=2", "-version")
                    .shouldContain(YELLOW_PAGES_FLAG + "=2 is outside the allowed range")
                    .shouldNotHaveExitValue(0);

        // Check that the thread stack guarantee includes the yellow pages, excluding
        // the stack-growth guard page, with the default number of red pages.
        ProcessTools.executeTestJava("-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                "-XX:" + YELLOW_PAGES_FLAG + "=8", "-XX:" + RED_PAGES_FLAG + "=1",
                TestWindowsStackPages.class.getName(), "8")
                .shouldHaveExitValue(0);

        // Check that the thread stack guarantee does not include the fatal red pages.
        ProcessTools.executeTestJava("-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                "-XX:" + YELLOW_PAGES_FLAG + "=8", "-XX:" + RED_PAGES_FLAG + "=3",
                TestWindowsStackPages.class.getName(), "8")
                .shouldHaveExitValue(0);
    }

    private static void checkStackGuarantee(int yellowPages) throws Exception {
        long startupValue = getStackGuarantee();
        long expectedValue = (yellowPages - /* stack growth guard page */ 1) * 4096;

        if (startupValue != expectedValue) {
            throw new RuntimeException("invariant failure for startup value: " +
                startupValue + " v/s " + expectedValue);
        }

        ProbeThread thread = new ProbeThread();
        thread.start();
        thread.join();

        if (thread.guarantee() != expectedValue) {
            throw new RuntimeException("invariant failure for thread value: " +
                thread.guarantee() + " v/s " + expectedValue);
        }
    }

    private static final class ProbeThread extends Thread {
        private long stackGuarantee;

        @Override
        public void run() {
            stackGuarantee = getStackGuarantee();
        }

        long guarantee() {
            return stackGuarantee;
        }
    }

    private static native long getStackGuarantee();
}