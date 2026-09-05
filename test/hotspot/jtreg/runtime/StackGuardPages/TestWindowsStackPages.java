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
 * @summary Verifies that Windows stack guard-zone protection follows the
 *          configured number of red and yellow pages
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
            checkStackGuardPages(Integer.parseInt(args[0]));
            return;
        }

        // Check the default value.
        ProcessTools.executeTestJava("-XX:+PrintFlagsFinal", "-version")
                    .shouldMatch(YELLOW_PAGES_FLAG + "[ ]+=[ ]+3")
                    .shouldHaveExitValue(0);

        // Check that the minimum value is three.
        ProcessTools.executeTestJava("-XX:" + YELLOW_PAGES_FLAG + "=2", "-version")
                    .shouldContain(YELLOW_PAGES_FLAG + "=2 is outside the allowed range")
                    .shouldNotHaveExitValue(0);

        // Check the minimum yellow zone with the default red zone.
        ProcessTools.executeTestJava("-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                "-XX:" + YELLOW_PAGES_FLAG + "=3", "-XX:" + RED_PAGES_FLAG + "=1",
                TestWindowsStackPages.class.getName(), "4")
                .shouldHaveExitValue(0);

        // Check that increasing the yellow zone increases the protected region.
        ProcessTools.executeTestJava("-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                "-XX:" + YELLOW_PAGES_FLAG + "=8", "-XX:" + RED_PAGES_FLAG + "=1",
                TestWindowsStackPages.class.getName(), "9")
                .shouldHaveExitValue(0);

        // Check that increasing the red zone also increases the protected region.
        ProcessTools.executeTestJava("-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                "-XX:" + YELLOW_PAGES_FLAG + "=8", "-XX:" + RED_PAGES_FLAG + "=3",
                TestWindowsStackPages.class.getName(), "11")
                .shouldHaveExitValue(0);
    }

    private static void checkStackGuardPages(int expectedPages) throws Exception {
        long startupValue = getStackGuardPages();
        if (startupValue != expectedPages) {
            throw new RuntimeException("invariant failure for startup thread: " +
                startupValue + " pages v/s " + expectedPages);
        }

        ProbeThread thread = new ProbeThread();
        thread.start();
        thread.join();

        if (thread.guardPages() != expectedPages) {
            throw new RuntimeException("invariant failure for Java thread: " +
                thread.guardPages() + " pages v/s " + expectedPages);
        }
    }

    private static final class ProbeThread extends Thread {
        private long stackGuardPages;

        @Override
        public void run() {
            stackGuardPages = getStackGuardPages();
        }

        long guardPages() {
            return stackGuardPages;
        }
    }

    private static native long getStackGuardPages();
}
