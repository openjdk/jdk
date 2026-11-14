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
 * @bug 8390002
 * @summary Verifies the Windows logical yellow-zone default
 * @requires os.family == "windows"
 * @library /test/lib
 * @run driver TestWindowsStackPages
 */

import jdk.test.lib.process.ProcessTools;

public class TestWindowsStackPages {
    private static final String RED_FLAG = "StackRedPages";
    private static final String YELLOW_FLAG = "StackYellowPages";

    public static void main(String[] args) throws Exception {
        ProcessTools.executeTestJava("-XX:+PrintFlagsFinal", "-version")
                    .shouldMatch(RED_FLAG + "[ ]+=[ ]+1")
                    .shouldMatch(YELLOW_FLAG + "[ ]+=[ ]+2")
                    .shouldHaveExitValue(0);

        ProcessTools.executeTestJava("-XX:" + RED_FLAG + "=0", "-version")
                    .shouldContain(RED_FLAG + "=0 is outside the allowed range")
                    .shouldNotHaveExitValue(0);

        ProcessTools.executeTestJava("-XX:" + YELLOW_FLAG + "=1", "-version")
                    .shouldContain(YELLOW_FLAG + "=1 is outside the allowed range")
                    .shouldNotHaveExitValue(0);

        ProcessTools.executeTestJava("-XX:" + RED_FLAG + "=3",
                                     "-XX:" + YELLOW_FLAG + "=5",
                                     "-XX:+PrintFlagsFinal", "-version")
                    .shouldMatch(RED_FLAG + "[ ]+=[ ]+3")
                    .shouldMatch(YELLOW_FLAG + "[ ]+=[ ]+5")
                    .shouldHaveExitValue(0);
    }
}
