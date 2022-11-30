/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8290313
 * @library /test/lib
 * @summary Produce warning when user specified java.io.tmpdir directory doesn't exist
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class TempDirDoesNotExist {
    final static String ioWarningMsg = "WARNING: java.io.tmpdir directory does not exist";

    public static void main(String... args) throws Exception {

        String userDir = System.getProperty("user.home");
        String timeStamp = java.time.Instant.now().toString();
        String tempDir = Path.of(userDir,"non-existing-", timeStamp).toString();

        for (String arg : args) {
            if (arg.equals("io")) {
                try {
                    File.createTempFile("prefix", ".suffix");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (arg.equals("nio")) {
                try {
                    Files.createTempFile("prefix", ".suffix");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                throw new Exception("unknown case: " + arg);
            }
        }

        if (args.length == 0) {
            // standard test with default setting for java.io.tmpdir
            testMessageNotExist(0, ioWarningMsg, "TempDirDoesNotExist", "io");
            testMessageNotExist(0, ioWarningMsg, "TempDirDoesNotExist", "nio");

            // valid custom java.io.tmpdir
            testMessageNotExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + userDir,
                    "TempDirDoesNotExist", "io");
            testMessageNotExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + userDir,
                    "TempDirDoesNotExist", "nio");

            // invalid custom java.io.tmpdir
            testMessageExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirDoesNotExist", "io");
            testMessageExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirDoesNotExist", "nio");

            // test with security manager
            testMessageExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + tempDir
                            + " -Djava.security.manager",
                    "TempDirDoesNotExist", "io");

            testMessageExist(0, ioWarningMsg, "-Djava.io.tmpdir=" + tempDir
                            + " -Djava.security.manager",
                    "TempDirDoesNotExist", "nio");

            // error message printed only once
            testMessageCounter(0, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirDoesNotExist", "io", "nio");
        }
    }

    private static void testMessageExist(int exitValue, String errorMsg, String... options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldContain(errorMsg)
                .shouldHaveExitValue(exitValue);
    }

    private static void testMessageNotExist(int exitValue, String errorMsg,String... options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldNotContain(errorMsg).shouldHaveExitValue(exitValue);
    }

    private static void testMessageCounter(int exitValue,String... options) throws Exception {
        OutputAnalyzer originalOutput = ProcessTools.executeTestJvm(options);
        List<String> list = originalOutput.asLines().stream().filter(line
                -> line.equalsIgnoreCase(ioWarningMsg)).collect(Collectors.toList());
        if (list.size() != 1 || originalOutput.getExitValue() != exitValue)
            throw new Exception("counter of messages is not one, but " + list.size()
                    + "\n" + originalOutput.asLines().toString() + "\n");
    }
}