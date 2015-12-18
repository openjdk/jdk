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

package jdk.test.lib.jittester.jtreg;

import jdk.test.lib.Asserts;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JitTesterDriver {

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "[TESTBUG]: wrong number of argument : " + args.length
                    + ". Expected 1 argument -- jit-tester test name.");
        }
        OutputAnalyzer oa;
        try {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, args[0]);
            oa = new OutputAnalyzer(pb.start());
        } catch (Exception e) {
            throw new Error("Unexpected exception on test jvm start :" + e, e);
        }

        Path testDir = Paths.get(Utils.TEST_SRC);
        String goldOut = formatOutput(streamGoldFile(testDir, args[0], "out"), s -> true);
        Asserts.assertEQ(oa.getStdout(), goldOut, "Actual stdout isn't equal to golden one");

        // TODO: add a comment why we skip such lines
        Predicate<String> notStartWhitespaces = s -> !(s.startsWith("\t") || s.startsWith(" "));
        String goldErr = formatOutput(streamGoldFile(testDir, args[0], "err"), notStartWhitespaces);
        String anlzErr = formatOutput(Arrays.stream(oa.getStderr().split(Utils.NEW_LINE)),
                                      notStartWhitespaces);
        Asserts.assertEQ(anlzErr, goldErr, "Actual stderr isn't equal to golden one");

        int exitValue = Integer.parseInt(streamGoldFile(testDir, args[0], "exit").findFirst().get());
        oa.shouldHaveExitValue(exitValue);
    }

    private static String formatOutput(Stream<String> stream, Predicate<String> predicate) {
        String result = stream
                .filter(predicate)
                .collect(Collectors.joining(Utils.NEW_LINE));
        if (result.length() > 0) {
            result += Utils.NEW_LINE;
        }
        return result;
    }

    private static Stream<String> streamGoldFile(Path dir, String name, String suffix) {
        try {
            return Files.lines(dir.resolve(name + ".gold." + suffix));
        } catch (IOException e) {
            throw new Error(String.format("Can't read golden %s for %s : %s", suffix, name, e), e);
        }
    }
}
