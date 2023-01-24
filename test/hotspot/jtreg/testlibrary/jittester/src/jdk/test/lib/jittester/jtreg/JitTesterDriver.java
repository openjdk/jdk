/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

import jdk.test.lib.jittester.ErrorTolerance;
import jdk.test.lib.jittester.Phase;
import jdk.test.lib.jittester.ProcessRunner;

public class JitTesterDriver {

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "[TESTBUG]: wrong number of argument : " + args.length
                    + ". Expected at least 1 argument -- jit-tester test name.");
        }

        String name = args[args.length - 1];
        Path testDir = Paths.get(Utils.TEST_SRC);

        try {
            ProcessBuilder pb = ProcessTools.createTestJvm(args);
            ProcessRunner.runProcess(pb, name, Phase.RUN);

            // Verification
            String goldExitValue = streamFile(testDir, name, Phase.GOLD_RUN, "exit").findFirst().get();
            if (!goldExitValue.equals("TIMEOUT")) {
                String anlzExitValue = streamFile(Path.of("."), name, Phase.RUN, "exit").findFirst().get();
                Asserts.assertEQ(anlzExitValue, goldExitValue);

                ErrorTolerance.assertIsAcceptable(
                    Paths.get(Utils.TEST_SRC).resolve(name + "." + Phase.GOLD_RUN.suffix + ".err"),
                    Paths.get(".").resolve(name + "." + Phase.RUN.suffix + ".err"));

                ErrorTolerance.assertIsAcceptable(
                    Paths.get(Utils.TEST_SRC).resolve(name + "." + Phase.GOLD_RUN.suffix + ".out"),
                    Paths.get(".").resolve(name + "." + Phase.RUN.suffix + ".out"));
            }
        } catch (Exception e) {
            throw new Error("Unexpected exception on test jvm start :" + e, e);
        }
    }

    private static Stream<String> streamFile(Path dir, String name, Phase phase, String kind) {
        String fullName = name + "." + phase.suffix + "." + kind;
        try {
            return Files.lines(dir.resolve(fullName), Charset.forName("UTF-8"));
        } catch (IOException e) {
            throw new Error(String.format("Can't read file: %s", fullName), e);
        }
    }

}
