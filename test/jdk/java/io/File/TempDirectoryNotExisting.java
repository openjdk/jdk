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

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class TempDirectoryNotExisting {
    final static String ioWarningMsg = "WARNING: java.io.tmpdir location does not exist";
    final static String ioExceptionMsgNonWindows = "java.io.IOException: No such file or directory";
    final static String ioExceptionMsgWindows = "java.io.IOException: The system cannot find the path specified";
    final static String nioExceptionMsg = "java.nio.file.NoSuchFileException";

    public static void main(String... args) throws Exception {

        String userDir = System.getProperty("user.home");
        String timeStamp = System.currentTimeMillis() + "";
        String tempDir = Path.of(userDir,"non-existing-", timeStamp).toString();

        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("io")) {
                try {
                    File.createTempFile("prefix", ".suffix");
                }catch(Exception e){
                    e.printStackTrace();
                }
            } else {

                if (args[0].equalsIgnoreCase("nio")) {
                    try {
                        Files.createTempFile("prefix", ".suffix");
                    }catch(Exception e){
                        e.printStackTrace();;
                    }
                } else {
                    try {
                        File.createTempFile("prefix", ".suffix");
                        Files.createTempFile("prefix", ".suffix");
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        } else {


            //standard test with default setting for java.io.tmpdir
            testMessageNotExist(0, ioWarningMsg, "TempDirectoryNotExisting", "io");
            testMessageNotExist(0, ioWarningMsg, "TempDirectoryNotExisting", "nio");

            //set java.io.tmpdir to be empty
            testMessageAbnormalExist(0, "-Djava.io.tmpdir=", "TempDirectoryNotExisting", "io");
            testMessageAbnormalExist(0, "-Djava.io.tmpdir=", "TempDirectoryNotExisting", "nio");

            String msg = Platform.isWindows()? ioExceptionMsgWindows: ioExceptionMsgNonWindows;
            //invalid custom java.io.tmpdir
            testMessageExist(0, ioWarningMsg, msg, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirectoryNotExisting", "io");
            testMessageExist(0, ioWarningMsg, nioExceptionMsg, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirectoryNotExisting", "nio");

            //valid custom java.io.tmpdir
            testMessageNotExist(0, ioWarningMsg,"-Djava.io.tmpdir=" + userDir,
                    "TempDirectoryNotExisting", "io");
            testMessageNotExist(0, ioWarningMsg,"-Djava.io.tmpdir=" + userDir,
                    "TempDirectoryNotExisting", "nio");

            //test with security manager
            testMessageExist(0, ioWarningMsg, msg, "-Djava.io.tmpdir=" + tempDir
                            + " -Djava.security.manager",
                    "TempDirectoryNotExisting", "io");

            testMessageExist(0, ioWarningMsg, nioExceptionMsg, "-Djava.io.tmpdir=" + tempDir
                            + " -Djava.security.manager",
                    "TempDirectoryNotExisting", "nio");

            //error message to be printed only once
            testMessageCounter(0, "-Djava.io.tmpdir=" + tempDir,
                    "TempDirectoryNotExisting", "io-nio");
        }
    }

    private static void testMessageExist(int exitValue, String errorMsg1, String errorMsg2, String... options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldContain(errorMsg1)
                .shouldContain(errorMsg2)
                .shouldHaveExitValue(exitValue);
    }

    private static void testMessageAbnormalExist(int exitValue, String... options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldHaveExitValue(exitValue);
    }

    private static void testMessageNotExist(int exitValue, String errorMsg,String... options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldNotContain(errorMsg).shouldHaveExitValue(exitValue);
    }

    private static void testMessageCounter(int exitValue,String... options) throws Exception {
        List<String> list = ProcessTools.executeTestJvm(options).shouldHaveExitValue(exitValue)
                .asLines().stream()
                .filter(line -> line.equalsIgnoreCase(ioWarningMsg))
                .collect(Collectors.toList());
        if (list.size() != 1) throw new Exception("counter of messages is not one, but " + list.size());
    }
}