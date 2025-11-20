/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MainTest extends JUnitAdapter.TestSrcInitializer {

    @ParameterizedTest
    @MethodSource
    public void testOutput(TestSpec test) throws IOException {
        test.run();
    }

    private static Collection<TestSpec> testOutput() {
        return Stream.of(
                // Print the tool version
                build().expectShortHelp(),
                // Print the tool version
                build().args("--version").expectVersion(),
                // Print the tool version
                build().args("foo", "bar").expectErrors(I18N.format("error.non-option-arguments", 2)),
                // Valid command line requesting to print the full help.
                build().args("-h").expectFullHelp(),
                // Valid command line requesting to build a package and print the full help.
                build().args("--main-jar", "hello.jar", "-?", "--main-class", "foo").expectFullHelp(),
                // Valid command line requesting to build a package and print the full help and the version of the tool.
                build().args("--main-jar", "hello.jar", "-?", "--main-class", "foo", "--version").expectVersionWithHelp(),
                // Valid command line requesting to print the full help and the version of the tool.
                build().args("--help", "--version").expectVersionWithHelp(),
                // Invalid command line requesting to print the version of the tool.
                build().args("foo", "--version").expectErrors(I18N.format("error.non-option-arguments", 1))
        ).map(TestSpec.Builder::create).toList();
    }


    record TestSpec(List<String> args, int expectedExitCode, List<String> expectedStdout, List<String> expectedStderr) {

        TestSpec {
            Objects.requireNonNull(args);
            Objects.requireNonNull(expectedStdout);
            Objects.requireNonNull(expectedStderr);
        }

        void run() {
            var result = ExecutionResult.create(args.toArray(String[]::new));
            assertEquals(expectedExitCode, result.exitCode());
            assertEquals(expectedStdout, result.stdout());
            assertEquals(expectedStderr, result.stderr());
        }


        static final class Builder {

            TestSpec create() {
                return new TestSpec(args, expectedExitCode, expectedStdout, expectedStderr);
            }

            Builder args(String... v) {
                return args(List.of(v));
            }

            Builder args(Collection<String> v) {
                args.addAll(v);
                return this;
            }

            Builder expectStdout(String... lines) {
                return expectStdout(List.of(lines));
            }

            Builder expectStdout(Collection<String> lines) {
                return append(expectedStdout, lines);
            }

            Builder expectStderr(String... lines) {
                return expectStderr(List.of(lines));
            }

            Builder expectStderr(Collection<String> lines) {
                return append(expectedStderr, lines);
            }

            Builder expectShortHelp() {
                var sb = new StringBuilder();
                new StandardHelpFormatter(OperatingSystem.current()).formatNoArgsHelp(sb::append);
                return expectStdout(lines(sb.toString()));
            }

            Builder expectFullHelp() {
                try {
                    return expectStdout(Files.readAllLines(goldenHelpOutputFile(OperatingSystem.current())));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }

            Builder expectVersion() {
                return expectStdout(System.getProperty("java.version"));
            }

            Builder expectVersionWithHelp() {
                return expectVersion().expectStdout("").expectFullHelp();
            }

            Builder expectErrors(String... msg) {
                return expectErrorExitCode().expectStderr(Stream.of(msg).map(v -> {
                    return I18N.format("message.error-header", v);
                }).toList());
            }

            Builder expectExitCode(int v) {
                expectedExitCode = v;
                return this;
            }

            Builder expectErrorExitCode() {
                return expectExitCode(1);
            }

            private Builder append(List<String> sink, Collection<String> lines) {
                lines.forEach(sink::add);
                return this;
            }

            private List<String> args = new ArrayList<>();
            private int expectedExitCode;
            private List<String> expectedStdout = new ArrayList<>();
            private List<String> expectedStderr = new ArrayList<>();
        }
    }


    private record ExecutionResult(List<String> stdout, List<String> stderr, int exitCode) {

        ExecutionResult {
            Objects.requireNonNull(stdout);
            Objects.requireNonNull(stderr);
        }

        static ExecutionResult create(String... args) {
            var stdout = new StringWriter();
            var stderr = new StringWriter();

            var exitCode = Main.run(new PrintWriter(stdout), new PrintWriter(stderr), args);

            return new ExecutionResult(lines(stdout.toString()), lines(stderr.toString()), exitCode);
        }
    }


    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }

    private static List<String> lines(String str) {
        return new BufferedReader(new StringReader(str)).lines().toList();
    }

    private static Path goldenHelpOutputFile(OperatingSystem os) {
        String fname = String.format("help-%s.txt", os.name().toLowerCase());
        return TKit.TEST_SRC_ROOT.resolve("junit/share/jdk.jpackage/jdk/jpackage/internal/cli", fname);
    }
}
