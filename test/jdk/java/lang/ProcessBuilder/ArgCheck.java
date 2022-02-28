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
 * @requires (os.family == "windows")
 * @library /test/lib
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;

/**
 * Class to check invocation of java, .cmd, and vbs scripts with arguments and various quote cases.
 * Can be run standalone to compare results with other Java versions.
 */
public class ArgCheck {

    private static final Path SRC_DIR = Path.of(System.getProperty("test.src", "."));
    private static final Path WORK_DIR = Path.of(System.getProperty("user.dir", "."));
    private static final Path TEST_CLASSES = Path.of(System.getProperty("test.classes", "."));

    private static final String ECHO_CMD_PATH = WORK_DIR.resolve("EchoArguments.cmd").toString();
    private static final String ECHO_VBS_PATH = WORK_DIR.resolve("EchoArguments.vbs").toString();

    // Test argument containing both a space and a trailing backslash
    // Depending on the mode the final backslash may act as an escape that may turn an added quote to a literal quote
    private static final String SPACE_AND_BACKSLASH = "SPACE AND BACKSLASH\\";
    private static final char DOUBLE_QUOTE = '"';

    private static final String AMBIGUOUS_PROP_NAME = "jdk.lang.Process.allowAmbiguousCommands";
    private static final String AMBIGUOUS_PROP_VALUE = System.getProperty(AMBIGUOUS_PROP_NAME);
    private static final Boolean AMBIGUOUS_PROP_BOOLEAN = AMBIGUOUS_PROP_VALUE == null ? null :
                                                          Boolean.valueOf(!"false".equals(AMBIGUOUS_PROP_VALUE));

    private static final List<String> ECHO_JAVA_ARGS = List.of("java", "-classpath", TEST_CLASSES.toString(), "ArgCheck");
    private static final List<String> ECHO_CMD_ARGS = List.of(ECHO_CMD_PATH);
    private static final List<String> ECHO_VBS_ARGS = List.of("CScript", "/b", ECHO_VBS_PATH);

    /**
     * If zero arguments are supplied, run the test cases.
     * If there are arguments, echo them to Stdout.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            // Echo supplied arguments and exit
            for (String arg : args)
                System.out.println(arg);
            return;
        }

        System.out.println("Java Version: " + Runtime.getRuntime().version());

        int errors = 0;
        int success = 0;

        ArgCheck ac = new ArgCheck();
        ac.setup();
        for (CMD cmd : CASES) {
            // If System property jdk.lang.process.allowAmbiguousCommands matches the case, test it
            // If undefined, test them all
            if (AMBIGUOUS_PROP_BOOLEAN == null ||
                    AMBIGUOUS_PROP_BOOLEAN.booleanValue() == cmd.allowAmbiguous) {
                try {
                    ac.testQuoteCases(cmd);
                    success++;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    errors++;
                }
            } else {
                // skip unmatched cases
            }
        }
        System.out.println("\nSuccess: " + success + ", errors: " + errors);
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
         *
         * @param command list of command parameters to invoke the executable or script
         * @param arguments list of arguments (appended to the command)
         * @param allowAmbiguous  true/false to set property jdk.lang.Process.allowAmbiguousCommands
         * @param expected    expected lines of output from invoked command
         */
        CMD(List<String> command, List<String> arguments, boolean allowAmbiguous, String expected) {
            this.command = command;
            this.arguments = arguments;
            this.allowAmbiguous = allowAmbiguous;
            this.result = expected.indent(0);
        }

        final List<String> command;
        final List<String> arguments;
        final boolean allowAmbiguous;
        final String result;
    }

    /**
     * List of cases with the command, arguments, allowAmbiguous setting, and the expected results
     */
    static final List<CMD> CASES = List.of(

            // allowAmbiguousCommands = false, without application supplied double-quotes.
            // The space in the argument requires it to be quoted, the final backslash
            // must not be allowed to turn the quote that is added into a literal
            // instead of closing the quote.
            new CMD(ECHO_JAVA_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_1"),
                    false,
                    "SPACE AND BACKSLASH\\\n" +
                            "ARG_1"),
            new CMD(ECHO_CMD_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_2"),
                    false,
                    "\"SPACE AND BACKSLASH\\\"\n" +
                            "ARG_2"),
            new CMD(ECHO_VBS_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_3"),
                    false,
                    "SPACE AND BACKSLASH\\\\\n" +
                            "ARG_3"),

            // allowAmbiguousCommands = false, WITH application supplied double-quotes around the argument
            // The argument has surrounding quotes so does not need further quoting.
            // However, for exe commands, the final backslash must not be allowed to turn the quote
            // into a literal instead of closing the quote.
            new CMD(ECHO_JAVA_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_11"),
                    false,
                    "SPACE AND BACKSLASH\\\n" +
                            "ARG_11"),
            new CMD(ECHO_CMD_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_12"),
                    false,
                    "\"SPACE AND BACKSLASH\\\"\n" +
                            "ARG_12"),
            new CMD(ECHO_VBS_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_13"),
                    false,
                    "SPACE AND BACKSLASH\\\\\n" +
                            "ARG_13"),

            // Legacy mode tests; allowAmbiguousCommands = true; no application supplied quotes
            // The space in the argument requires it to be quoted, the final backslash
            // must not be allowed to turn the quote that is added into a literal
            // instead of closing the quote.
            new CMD(ECHO_JAVA_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_21"),
                    true,
                    "SPACE AND BACKSLASH\\\n" +
                            "ARG_21"),
            new CMD(ECHO_CMD_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_22"),
                    true,
                    "\"SPACE AND BACKSLASH\\\\\"\n" +
                            "ARG_22"),
            new CMD(ECHO_VBS_ARGS,
                    List.of(SPACE_AND_BACKSLASH, "ARG_23"),
                    true,
                    "SPACE AND BACKSLASH\\\\\n" +
                            "ARG_23"),

            // allowAmbiguousCommands = true, WITH application supplied double-quotes around the argument
            // The argument has surrounding quotes so does not need further quoting.
            // The backslash before the final quote is ignored and is interpreted differently for each command.
            new CMD(ECHO_JAVA_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_31"),
                    true,
                    "SPACE AND BACKSLASH\" ARG_31"),
            new CMD(ECHO_CMD_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_32"),
                    true,
                    "\"SPACE AND BACKSLASH\\\"\n" +
                            "ARG_32"),
            new CMD(ECHO_VBS_ARGS,
                    List.of(DOUBLE_QUOTE + SPACE_AND_BACKSLASH + DOUBLE_QUOTE, "ARG_33"),
                    true,
                    "SPACE AND BACKSLASH\\\n" +
                            "ARG_33")
    );

    /**
     * Test various commands and arguments invoking non-exe (.cmd) scripts with lenient argument checking.
     *
     * @param commands A {@literal List<String>} of command arguents.
     * @param expected an expected result, either the class of an expected exception or an Integer exit value
     */
    void testQuoteCases(CMD cmd) throws Exception {
        List<String> args = new ArrayList<>(cmd.command);
        args.addAll(cmd.arguments);
        testCommand(() -> new ProcessBuilder(args).start(), args, cmd.allowAmbiguous, cmd.result);
    }

    /**
     * Common function to Invoke a process with the commands and check the result.
     *
     * @param callable a callable to create the Process
     * @param arguments a list of command strings
     * @param allowAmbiguous true/false to set the value of system property jdk.lang.Process.allowAmbiguousCommands
     * @param expected   expected stdout
     */
    private static void testCommand(Callable<Process> callable, List<String> arguments,
                                    boolean allowAmbiguous, String expected) throws Exception {
        System.setProperty(AMBIGUOUS_PROP_NAME, Boolean.toString(allowAmbiguous));
        String actual = "";
        try {
            // Launch the process and wait for termination
            Process process = callable.call();
            try (InputStream is = process.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                actual = new String(bytes, Charset.defaultCharset()).indent(0);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe.getMessage(), ioe);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("exitCode: " + exitCode);
            }
        } catch (IOException ioe) {
            actual = ioe.getMessage();
            actual = actual.replace(arguments.get(0), "CMD");
            ioe.printStackTrace();
        } catch (Exception ex) {
            actual = ex.getMessage();       // Use exception message as output
        }
        if (expected != null) {
            if (!Objects.equals(actual, expected)) {
                System.out.println("Invoking(" + allowAmbiguous + "): " + arguments);
                System.out.print("Actual:   " + actual);
                System.out.print("Expected: " + expected);
                System.out.println();
                throw new RuntimeException("Unexpected output");
            }
        } else {
            System.out.println("out: " + actual);
        }
    }

    /**
     * Initialize .cmd and .vbs scripts.
     *
     * @throws Error if an exception occurs
     */
    private static void setup() {
        try {
            Files.writeString(Path.of(ECHO_CMD_PATH), EchoArgumentsCmd);
            Files.writeString(Path.of(ECHO_VBS_PATH), EchoArgumentsVbs);
        } catch (IOException e) {
            throw new Error(e.getMessage());
        }
    }

    /**
     * Self contained .cmd to echo each arg on a separate line.
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
     * Self contained .vbs to echo each arg on a separate line.
     */
    static final String EchoArgumentsVbs = "Option Explicit\n" +
            "Dim arg\n" +
            "for each arg in WScript.Arguments\n" +
            "  WScript.StdOut.WriteLine(arg)\n" +
            "Next\n";
}
