/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282008
 * @requires (os.family == "windows")
 * @run main/othervm ArgCheck
 * @summary Check invocation of exe and non-exe programs using ProcessBuilder
 *      and arguments with spaces, backslashes, and simple quoting.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to check invocation of java, .cmd, and vbs scripts with arguments and various quote cases.
 * Can be run standalone to compare results with other Java versions.
 */
public class ArgCheck {

    private static final Path SRC_DIR = Paths.get(System.getProperty("test.src", "."));
    private static final Path WORK_DIR = Paths.get(System.getProperty("user.dir", "."));
    private static final Path TEST_CLASSES = Paths.get(System.getProperty("test.classes", "."));

    private static final String ECHO_CMD_PATH = WORK_DIR.resolve("EchoArguments.cmd").toString();
    private static final String ECHO_VBS_PATH = WORK_DIR.resolve("EchoArguments.vbs").toString();

    // Test argument containing both a space and a trailing backslash
    // Depending on the mode the final backslash may act as an escape that may turn an added quote to a literal quote
    private static final String SPACE_AND_BACKSLASH = "SPACE AND BACKSLASH\\";
    private static final char DOUBLE_QUOTE = '"';
    private static final char BACKSLASH = '\\';

    private static final String AMBIGUOUS_PROP_NAME = "jdk.lang.Process.allowAmbiguousCommands";
    private static final String AMBIGUOUS_PROP_VALUE = System.getProperty(AMBIGUOUS_PROP_NAME);
    private static final Boolean AMBIGUOUS_PROP_BOOLEAN = AMBIGUOUS_PROP_VALUE == null ? null :
                                                          Boolean.valueOf(!"false".equals(AMBIGUOUS_PROP_VALUE));

    private static final List<String> ECHO_JAVA_ARGS = Arrays.asList("java", "-classpath", TEST_CLASSES.toString(), "ArgCheck");
    private static final List<String> ECHO_CMD_ARGS = Arrays.asList(ECHO_CMD_PATH);
    private static final List<String> ECHO_VBS_ARGS = Arrays.asList("CScript", "/b", ECHO_VBS_PATH);

    /**
     * If zero arguments are supplied, run the test cases, by launching each as a child process.
     * If there are arguments, then this is a child Java process that prints each argument to stdout.
     * The test can be run manually with -Djdk.lang.Process.allowAmbiguousCommands={"true", "false", ""}
     * to run a matching subset of the tests.
     */
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            // Echo supplied arguments and exit
            for (String arg : args)
                System.out.println(arg);
            return;
        }

        System.out.println("Java Version: " + System.getProperty("java.version"));

        createFiles();

        int errors = 0;
        int success = 0;
        int skipped = 0;

        for (CMD cmd : CASES) {
            // If System property jdk.lang.process.allowAmbiguousCommands matches the case, test it
            // If undefined, test them all
            if (AMBIGUOUS_PROP_BOOLEAN == null ||
                    AMBIGUOUS_PROP_BOOLEAN.booleanValue() == cmd.allowAmbiguous) {
                try {
                    testCommand(cmd);
                    success++;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    errors++;
                }
            } else {
                // skip unmatched cases
                skipped++;
            }
        }
        if (skipped > 0) {
            System.out.printf("%d cases skipped, they did not match the tests with jdk.lang.Process.allowAmbiguousCommands: %s%n",
                    skipped, AMBIGUOUS_PROP_BOOLEAN);
        }
        System.out.printf("\nSuccess: %d, errors: %d%n", success, errors);
        if (errors > 0) {
            throw new RuntimeException("Errors: " + errors);
        }
    }

    /**
     * A CMD holds the parameters and the expected result of invoking a process with the parameters.
     */
    static class CMD {
        /**
         * Construct a test case.
         * @param allowAmbiguous  true/false to set property jdk.lang.Process.allowAmbiguousCommands
         * @param command list of command parameters to invoke the executable or script
         * @param arguments list of arguments (appended to the command)
         * @param expected    expected lines of output from invoked command
         */
        CMD(boolean allowAmbiguous, List<String> command, List<String> arguments, List<String> expected) {
            this.allowAmbiguous = allowAmbiguous;
            this.command = command;
            this.arguments = arguments;
            this.expected = expected;
        }

        final boolean allowAmbiguous;
        final List<String> command;
        final List<String> arguments;
        final List<String> expected;
    }

    /**
     * List of cases with the command, arguments, allowAmbiguous setting, and the expected results
     */
    static final List<CMD> CASES = Arrays.asList(

            // allowAmbiguousCommands = false, without application supplied double-quotes.
            // The space in the argument requires it to be quoted, the final backslash
            // must not be allowed to turn the quote that is added into a literal
            // instead of closing the quote.
            new CMD(false,
                    ECHO_JAVA_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_1"),
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_1")),
            new CMD(false,
                    ECHO_CMD_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_2"),
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_2")),
            new CMD(false,
                    ECHO_VBS_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_3"),
                    Arrays.asList(SPACE_AND_BACKSLASH + BACKSLASH, "ARG_3")),

            // allowAmbiguousCommands = false, WITH application supplied double-quotes around the argument
            // The argument has surrounding quotes so does not need further quoting.
            // However, for exe commands, the final backslash must not be allowed to turn the quote
            // into a literal instead of closing the quote.
            new CMD(false,
                    ECHO_JAVA_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_11"),
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_11")),
            new CMD(false,
                    ECHO_CMD_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_12"),
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_12")),
            new CMD(false,
                    ECHO_VBS_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_13"),
                    Arrays.asList(SPACE_AND_BACKSLASH + BACKSLASH, "ARG_13")),

            // Legacy mode tests; allowAmbiguousCommands = true; no application supplied quotes
            // The space in the argument requires it to be quoted, the final backslash
            // must not be allowed to turn the quote that is added into a literal
            // instead of closing the quote.
            new CMD(true,
                    ECHO_JAVA_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_21"),
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_21")),
            new CMD(true,
                    ECHO_CMD_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_22"),
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + BACKSLASH + DOUBLE_QUOTE, "ARG_22")),
            new CMD(true,
                    ECHO_VBS_ARGS,
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_23"),
                    Arrays.asList(SPACE_AND_BACKSLASH + BACKSLASH, "ARG_23")),

            // allowAmbiguousCommands = true, WITH application supplied double-quotes around the argument
            // The argument has surrounding quotes so does not need further quoting.
            // The backslash before the final quote is ignored and is interpreted differently for each command.
            new CMD(true,
                    ECHO_JAVA_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_31"),
                    Arrays.asList("SPACE AND BACKSLASH\" ARG_31")),
            new CMD(true,
                    ECHO_CMD_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_32"),
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_32")),
            new CMD(true,
                    ECHO_VBS_ARGS,
                    Arrays.asList(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_33"),
                    Arrays.asList(SPACE_AND_BACKSLASH, "ARG_33"))
    );

    /**
     * Common function to Invoke a process with the commands and check the result.
     *
     * @param cmd a CMD test case with arguments, allowAmbiguousCommands mode, and expected output
     */
    private static void testCommand(CMD cmd) throws Exception {
        System.setProperty(AMBIGUOUS_PROP_NAME, Boolean.toString(cmd.allowAmbiguous));
        List<String> actual = null;
        List<String> arguments = new ArrayList<>(cmd.command);
        arguments.addAll(cmd.arguments);
        try {
            // Launch the process and wait for termination
            ProcessBuilder pb = new ProcessBuilder(arguments);
            Process process = pb.start();
            try (InputStream is = process.getInputStream()) {
                String str = readAllBytesAsString(is);
                str = str.replace("\r", "");
                actual = Arrays.asList(str.split("\n"));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe.getMessage(), ioe);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                actual = new ArrayList(actual);
                actual.add("Exit code: " + exitCode);
            }
        } catch (IOException ioe) {
            actual = Arrays.asList(ioe.getMessage().replace(arguments.get(0), "CMD"));
        } catch (Exception ex) {
            actual = Arrays.asList(ex.getMessage());       // Use exception message as output
        }
        if (!Objects.equals(actual, cmd.expected)) {
            System.out.println("Invoking(" + cmd.allowAmbiguous + "): " + arguments);
            if (actual.size() != cmd.expected.size()) {
                System.out.println("Args Length: actual: " + actual.size() + " expected: " + cmd.expected.size());
            }
            System.out.println("Actual:   " + actual);
            System.out.println("Expected: " + cmd.expected);
            System.out.println();
            throw new RuntimeException("Unexpected output");
        }
    }

    /**
     * Private method to readAllBytes as a String.
     * (InputStream.readAllBytes is not supported by the JDK until 9)
     * @param is an InputStream
     * @return a String with the contents
     * @throws IOException if an error occurs
     */
    private static String readAllBytesAsString(InputStream is) throws IOException {
        final int BUF_SIZE = 8192;
        byte[] bytes = new byte[BUF_SIZE];
        int off = 0;
        int len;
        while ((len = is.read(bytes, off, bytes.length - off)) > 0) {
            off += len;
            if (off >= bytes.length) {
                // no space in buffer, reallocate larger
                bytes = Arrays.copyOf(bytes, bytes.length + BUF_SIZE);
            }
        }
        return new String(bytes, 0, off, Charset.defaultCharset());
    }

    /**
     * Initialize .cmd and .vbs scripts.
     *
     * @throws Error if an exception occurs
     */
    private static void createFiles() throws IOException {
        Files.write(Paths.get(ECHO_CMD_PATH), EchoArgumentsCmd.getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(ECHO_VBS_PATH), EchoArgumentsVbs.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Self contained .cmd to echo each argument on a separate line.
     */
    static final String EchoArgumentsCmd = "@echo off\n" +
            "set p1=\n" +
            "set p2=\n" +
            "\n" +
            "if not [%1]==[] set p1=%1\n" +
            "if not [%2]==[] set p2=%2\n" +
            "if not [%3]==[] set p3=%3\n" +
            "if defined p1 echo %p1%\n" +
            "if defined p2 echo %p2%\n" +
            "if defined p3 echo %p3%\n" +
            "exit /b 0\n";


    /**
     * Self contained .vbs to echo each argument on a separate line.
     */
    static final String EchoArgumentsVbs = "Option Explicit\n" +
            "Dim arg\n" +
            "for each arg in WScript.Arguments\n" +
            "  WScript.StdOut.WriteLine(arg)\n" +
            "Next\n";
}
