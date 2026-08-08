/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Yunbo Zhang. All rights reserved.
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
 * @bug 8390024
 * @summary Verify that WB_ParseCommandLine releases parser-owned C-heap memory
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=120 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=summary ParseCommandLineMemoryTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.parser.DiagnosticCommand;
import jdk.test.whitebox.parser.DiagnosticCommand.DiagnosticArgumentType;

public class ParseCommandLineMemoryTest {
    private static final int INVOCATION_COUNT = 10_000;
    private static final long MAX_INTERNAL_MEMORY_INCREASE_KB = 1024;
    private static final Pattern INTERNAL_MEMORY_DIFF = Pattern.compile(
            "(?m)^.*Internal \\(reserved=\\d+KB(?: [+-]\\d+KB)?, committed=\\d+KB(?: ([+-]\\d+)KB)?\\).*");

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final PidJcmdExecutor JCMD = new PidJcmdExecutor();
    private static final DiagnosticCommand[] COMMANDS = {
            new DiagnosticCommand("name", "desc", DiagnosticArgumentType.STRING, false, null)
    };
    private static final String COMMAND = "name=" + "x".repeat(4096);

    public static void main(String[] args) throws Exception {
        verifyNoLeak(false);
        verifyNoLeak(true);
    }

    private static void verifyNoLeak(boolean failParsing) throws Exception {
        takeBaseline();

        for (int i = 0; i < INVOCATION_COUNT; i++) {
            if (failParsing) {
                try {
                    WB.parseCommandLine(COMMAND + ",unknown", ',', COMMANDS);
                    throw new RuntimeException("Expected parsing to fail");
                } catch (IllegalArgumentException expected) {
                    // Expected: the value has been allocated before the unknown argument is rejected.
                }
            } else {
                WB.parseCommandLine(COMMAND, ',', COMMANDS);
            }
        }

        long increase = getInternalMemoryIncrease(executeJcmd("summary.diff", "scale=KB"));
        if (increase > MAX_INTERNAL_MEMORY_INCREASE_KB) {
            throw new RuntimeException("Internal memory increased by " + increase + "KB after "
                    + INVOCATION_COUNT + (failParsing ? " failed" : " successful")
                    + " parse attempts");
        }
    }

    private static void takeBaseline() throws Exception {
        OutputAnalyzer output = executeJcmd("baseline=true");
        output.shouldContain("Baseline taken");
    }

    private static OutputAnalyzer executeJcmd(String... arguments) throws Exception {
        OutputAnalyzer output = JCMD.execute("VM.native_memory " + String.join(" ", arguments), true);
        output.shouldHaveExitValue(0);
        return output;
    }

    private static long getInternalMemoryIncrease(OutputAnalyzer output) {
        Matcher matcher = INTERNAL_MEMORY_DIFF.matcher(output.getStdout());
        if (!matcher.find()) {
            if (output.getStdout().contains("Internal (")) {
                throw new RuntimeException("Could not parse Internal memory diff:\\n" + output.getStdout());
            }
            return 0;
        }
        if (matcher.group(1) == null) {
            return 0;
        }
        return Long.parseLong(matcher.group(1));
    }
}
