/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325203
 * @summary Test that Jpackage windows executable application kills the launched 3rd party application
 *          when System.exit(0) is invoked along with terminating java program.
 * @library ../helpers
 * @library /test/lib
 * @build jdk.test.lib.Utils
 * @requires os.family == "windows"
 * @build JpackageTest
 * @build jdk.jpackage.test.*
 * @build JpackageTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=JpackageTest
 */

import java.util.List;
import java.util.Optional;

import java.nio.file.Path;
import java.util.logging.Logger;

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.TKit;

public class JpackageTest {
    private static final Logger logger = Logger
            .getLogger(JpackageTest.class.getName());
    private static final Path TEST_APP_JAVA = TKit.TEST_SRC_ROOT
            .resolve("apps/ThirdPartyAppLauncher.java");

    @Test
    public static void test() throws Throwable {
        JpackageTest test = new JpackageTest();
        long regeditPid = 0;
        try {
            JPackageCommand cmd = JPackageCommand
                    .helloAppImage(TEST_APP_JAVA + "*Hello");

            // Create the image of the third party application launcher
            cmd.executeAndAssertImageCreated();

            /*
             * Start the third party application launcher and dump and save the
             * output of the application
             */
            List<String> output = new Executor().saveOutput().dumpOutput()
                    .setExecutable(cmd.appLauncherPath().toAbsolutePath())
                    .execute(0).getOutput();
            String pidStr = output.get(1);

            // parse to get regedit PID
            regeditPid = Long.parseLong(pidStr.split("=", 2)[1]);
            logger.info("Regedit PID is " + regeditPid);

            /*
             * Check whether the termination of third party application launcher
             * also terminating the launched third party application. If third
             * party application is not terminated the test is successful else
             * failure
             */
            Optional<ProcessHandle> processHandle = ProcessHandle
                    .of(regeditPid);
            boolean isAlive = processHandle.isPresent()
                    && processHandle.get().isAlive();
            if (isAlive) {
                logger.info("Test Successful");
            } else {
                logger.info("Test failed");
                throw new RuntimeException(
                        "Test failed: Third party software is terminated");
            }

        } finally {
            // Kill only a specific regedit instance
            Runtime.getRuntime().exec("taskkill /F /PID " + regeditPid);
        }
    }
}