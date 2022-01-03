/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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


/**
 * @test
 * @bug 8012453 8016046
 * @requires (os.family == "windows")
 * @run testng/othervm -Djava.security.manager=allow ExecCommand
 * @summary workaround for legacy applications with Runtime.getRuntime().exec(String command)
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


@SuppressWarnings("removal")
public class ExecCommand {

    private static final String JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS =
            "jdk.lang.Process.allowAmbiguousCommands";

    static class SecurityMan extends SecurityManager {
        public static String unquote(String str)
        {
            int length = (str == null)
                ? 0
                : str.length();

            if (length > 1
                && str.charAt(0) == '\"'
                && str.charAt(length - 1) == '\"')
            {
               return str.substring(1, length - 1);
            }
            return str;
        }

        @Override public void checkExec(String cmd) {
            String ncmd = (new File(unquote(cmd))).getPath();
            if ( ncmd.equals(".\\Program")
              || ncmd.equals("\".\\Program")
              || ncmd.equals(".\\Program Files\\do.cmd")
              || ncmd.equals(".\\Program.cmd")
              || ncmd.equals("cmd"))
            {
                return;
            }
            super.checkExec(cmd);
        }

        public void checkPermission(Permission perm) {
            if (perm instanceof RuntimePermission ||
                    perm instanceof java.lang.reflect.ReflectPermission)
                return;
            super.checkPermission(perm);
        };
        @Override public void checkRead(String file) {}       // TestNG reads files
        @Override public void checkWrite(String file) {}      // TestNG writes files
        @Override public void checkDelete(String file) {}
        @Override public void checkPropertiesAccess() {}
        @Override public void checkPropertyAccess(String name) {}  // TestNG reads properties
    }

    private static final List<String> doCmdCopy = List.of(
        ".\\Program.cmd",
        ".\\Program Files\\doNot.cmd",
        ".\\Program Files\\do.cmd");

    @BeforeClass
    void setup() throws Exception {

        // Create files to be execute
        try {
            new File(".\\Program Files").mkdirs();
            for (String cmdFile : doCmdCopy) {
                try (BufferedWriter outCmd = new BufferedWriter(
                        new FileWriter(cmdFile))) {
                    outCmd.write("@echo %1");
                }
            }
        } catch (IOException e) {
            throw new Error(e.getMessage());
        }
    }

    /**
     * Sequence of tests and test results in the TEST_RTE_ARGS DataProvider below.
     * The ordinals are used as indices in the lists of expected results.
     */
    private enum Mode {
        UNSET_NO_SM, // 0) no SM and default allowAmbiguousCommands; equivalent to true
        EMPTY_NO_SM, // 1) no SM and allowAmbiguousCommand is empty; equivalent to true
        FALSE_NO_SM, // 2) no SM and allowAmbiguousCommands = false
        EMPTY_SM,    // 3) SM and default allowAmbiguousCommands is empty; equivalent to false
    };

    /**
     * The command for Runtime.exec calls to execute in each of 4 modes,
     * and the expected exception for each mode.
     * Modes above define the indices in the List of expected results for each mode.
     */
    @DataProvider(name = "TEST_RTE_ARGS")
    Object[][] TEST_RTE_ARGS() {
        return new Object[][]{
                {"cmd /C dir > dirOut.txt",
                        "dirOut.txt",
                        Arrays.asList(null,
                                null,
                                FileNotFoundException.class,
                                FileNotFoundException.class)
                },
                {"cmd /C dir > \".\\Program Files\\dirOut.txt\"",
                        "./Program Files/dirOut.txt",
                        Arrays.asList(null,
                                null,
                                FileNotFoundException.class,
                                FileNotFoundException.class)
                },
                {".\\Program Files\\do.cmd",
                        null,
                        Arrays.asList(null,
                                null,
                                IOException.class,
                                IOException.class)
                },
                {"\".\\Program Files\\doNot.cmd\" arg",
                        null,
                        Arrays.asList(null,
                                null,
                                null,
                                AccessControlException.class)
                },
                {"\".\\Program Files\\do.cmd\" arg",
                        null,
                        // AccessControlException
                        Arrays.asList(null, null, null, null)
                },
                {"\".\\Program.cmd\" arg",
                        null,
                        Arrays.asList(null, null, null, null)
                },
                {".\\Program.cmd arg",
                        null,
                        Arrays.asList(null, null, null, null)
                },
        };
    }

    /**
     * Test each command with no SM and default allowAmbiguousCommands.
     * @param command a command
     * @param perModeExpected an expected Exception class or null
     */
    @Test(dataProvider = "TEST_RTE_ARGS")
    void testCommandAmbiguousUnset(String command, String testFile, List<Class<Exception>> perModeExpected) {
        // the JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS is undefined
        // "true" by default with the legacy verification procedure
        Properties props = System.getProperties();
        props.remove(JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS);
        System.setSecurityManager(null);

        testCommandMode(command, "Ambiguous Unset", testFile,
                perModeExpected.get(Mode.UNSET_NO_SM.ordinal()));
    }

    /**
     * Test each command with no SM and allowAmbiguousCommand is empty.
     * @param command a command
     * @param perModeExpected an expected Exception class or null
     */
    @Test(dataProvider = "TEST_RTE_ARGS")
    void testCommandAmbiguousEmpty(String command, String testFile, List<Class<Exception>> perModeExpected) {
        Properties props = System.getProperties();
        props.setProperty(JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS, "");
        System.setSecurityManager(null);
        testCommandMode(command, "Ambiguous Empty", testFile,
                perModeExpected.get(Mode.EMPTY_NO_SM.ordinal()));
    }

    /**
     * Test each command with no SM and allowAmbiguousCommands = false.
     * @param command a command
     * @param perModeExpected an expected Exception class or null
     */
    @Test(dataProvider = "TEST_RTE_ARGS")
    void testCommandAmbiguousFalse(String command, String testFile, List<Class<Exception>> perModeExpected) {
        Properties props = System.getProperties();
        props.setProperty(JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS, "false");
        System.setSecurityManager(null);

        testCommandMode(command, "Ambiguous false", testFile,
                perModeExpected.get(Mode.FALSE_NO_SM.ordinal()));
    }

    /**
     * Test each command with SecurityManager and default allowAmbiguousCommands is empty.
     * @param command a command
     * @param perModeExpected an expected Exception class or null
     */
    @Test(dataProvider = "TEST_RTE_ARGS")
    void testCommandWithSM(String command, String testFile, List<Class<Exception>> perModeExpected) {
        Properties props = System.getProperties();
        props.setProperty(JDK_LANG_PROCESS_ALLOW_AMBIGUOUS_COMMANDS, "");
        System.setSecurityManager(new SecurityMan());

        testCommandMode(command, "SecurityManager and Ambiguous Empty", testFile,
                perModeExpected.get(Mode.EMPTY_SM.ordinal()));
    }

    private void testCommandMode(String command, String kind,
                                 String testFile,
                                 Class<Exception> perModeExpected) {
        try {
            // Ensure the file that will be created does not exist.
            if (testFile != null) {
                Files.deleteIfExists(Path.of(testFile));
            }

            Process exec = Runtime.getRuntime().exec(command);
            exec.waitFor();

            // extended check
            if (testFile != null) {
                if (Files.notExists(FileSystems.getDefault().getPath(testFile)))
                    throw new FileNotFoundException(testFile);
            }
            Assert.assertNull(perModeExpected, "Missing exception");
        } catch (Exception ex) {
            if (!ex.getClass().equals(perModeExpected)) {
                Assert.fail("Unexpected exception! Step " + kind + ":"
                        + "\nArgument: " + command
                        + "\nExpected: " + perModeExpected
                        + "\n  Output: " + ex, ex);
            }
        }
    }
}
