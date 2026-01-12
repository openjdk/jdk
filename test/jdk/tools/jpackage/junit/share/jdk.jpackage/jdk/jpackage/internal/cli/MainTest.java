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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MainTest extends JUnitAdapter.TestSrcInitializer {

    @ParameterizedTest
    @MethodSource
    public void testOutput(TestSpec test) throws IOException {
        test.run();
    }

    @ParameterizedTest
    @MethodSource
    public void test_ErrorReporter(ErrorReporterTestSpec test) {
        test.run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"message.error-header", "message.advice-header"})
    public void test_ErrorReporter_format(String key) {
        var str = "Hello!";
        var msg = I18N.format(key, str);
        assertTrue(msg.contains(str));
        assertNotEquals(str, msg);
    }

    private static Collection<TestSpec> testOutput() {
        return Stream.of(
                // Print the tool version
                build().expectShortHelp(),
                // Print the tool version
                build().args("--version").expectVersion(),
                // Print the tool version
                // Additional error messages may be printed if the default bundling operation
                // can not be identified; don't verify these errors in the output.
                build().args("foo", "bar").stderrMatchType(OutputMatchType.STARTS_WITH).expectErrors(I18N.format("error.non-option-arguments", 2)),
                // Valid command line requesting to print the full help.
                build().args("-h").expectFullHelp(),
                // Valid command line requesting to build a package and print the full help.
                build().args("--main-jar", "hello.jar", "-?", "--main-class", "foo").expectFullHelp(),
                // Valid command line requesting to build a package and print the full help and the version of the tool.
                build().args("--main-jar", "hello.jar", "-?", "--main-class", "foo", "--version").expectVersionWithHelp(),
                // Valid command line requesting to print the full help and the version of the tool.
                build().args("--help", "--version").expectVersionWithHelp(),
                // Invalid command line requesting to print the version of the tool.
                // Additional error messages may be printed if the default bundling operation
                // can not be identified; don't verify these errors in the output.
                build().args("foo", "--version").stderrMatchType(OutputMatchType.STARTS_WITH).expectErrors(I18N.format("error.non-option-arguments", 1))
        ).map(TestSpec.Builder::create).toList();
    }


    private static List<ErrorReporterTestSpec> test_ErrorReporter() {
        var data = new ArrayList<ErrorReporterTestSpec>();

        for (var verbose : List.of(true, false)) {
            for (var makeCause : List.<UnaryOperator<Exception>>of(
                    ex -> ex,
                    // UncheckedIOException
                    ex -> {
                        if (ex instanceof IOException ioex) {
                            return new UncheckedIOException(ioex);
                        } else {
                            return null;
                        }
                    },
                    // ExceptionBox
                    ex -> {
                        var rex = ExceptionBox.toUnchecked(ex);
                        if (rex != ex) {
                            return rex;
                        } else {
                            return null;
                        }
                    }
            )) {
                for (var expect : List.of(
                        Map.entry(new IOException("I/O error"), true),
                        Map.entry(new NullPointerException(), true),
                        Map.entry(new JPackageException("Kaput!"), false),
                        Map.entry(new ConfigException("It is broken", "Fix it!"), false),
                        Map.entry(new ConfigException("It is broken. No advice how to fix it", (String)null), false),
                        Map.entry(new Utils.ParseException("Malformed command line"), false),
                        Map.entry(new StandardOption.AddLauncherIllegalArgumentException("Malformed value of --add-launcher option"), false)
                )) {
                    var cause = makeCause.apply(expect.getKey());
                    if (cause == null) {
                        continue;
                    }

                    var expectedOutput = new ArrayList<ExceptionFormatter>();
                    if (expect.getValue()) {
                        // An alien exception.
                        expectedOutput.add(ExceptionFormatter.STACK_TRACE);
                        expectedOutput.add(ExceptionFormatter.TO_STRING);
                    } else {
                        if (verbose) {
                            expectedOutput.add(ExceptionFormatter.STACK_TRACE);
                        }
                        if (expect.getKey() instanceof ConfigException cex) {
                            if (cex.getAdvice() != null) {
                                expectedOutput.add(ExceptionFormatter.MESSAGE_WITH_ADVICE);
                            } else {
                                expectedOutput.add(ExceptionFormatter.GET_MESSAGE);
                            }
                        } else {
                            expectedOutput.add(ExceptionFormatter.GET_MESSAGE);
                        }
                    }

                    data.add(new ErrorReporterTestSpec(cause, expect.getKey(), verbose, expectedOutput));
                }
            }
        }

        return data;
    }


    record TestSpec(List<String> args, int expectedExitCode, ExpectedOutput expectedStdout, ExpectedOutput expectedStderr) {

        TestSpec {
            Objects.requireNonNull(args);
            Objects.requireNonNull(expectedStdout);
            Objects.requireNonNull(expectedStderr);
        }

        void run() {
            var result = ExecutionResult.create(args.toArray(String[]::new));
            assertEquals(expectedExitCode, result.exitCode());
            expectedStdout.test(result.stdout());
            expectedStderr.test(result.stderr());
        }


        static final class Builder {

            TestSpec create() {
                return new TestSpec(
                        args,
                        expectedExitCode,
                        new ExpectedOutput(expectedStdout, Optional.ofNullable(stdoutMatchType).orElse(OutputMatchType.EQUALS)),
                        new ExpectedOutput(expectedStderr, Optional.ofNullable(stderrMatchType).orElse(OutputMatchType.EQUALS)));
            }

            Builder args(String... v) {
                return args(List.of(v));
            }

            Builder args(Collection<String> v) {
                args.addAll(v);
                return this;
            }

            Builder stdoutMatchType(OutputMatchType v) {
                stdoutMatchType = v;
                return this;
            }

            Builder stderrMatchType(OutputMatchType v) {
                stderrMatchType = v;
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
            private OutputMatchType stdoutMatchType;
            private OutputMatchType stderrMatchType;
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


    private enum OutputMatchType {
        EQUALS((_, actual) -> actual),
        STARTS_WITH((expected, actual) -> {
            if (expected.size() < actual.size()) {
                return actual.subList(0, expected.size());
            }
            return actual;
        }),
        ;

        OutputMatchType(BiFunction<List<String>, List<String>, List<String>> mapper) {
            this.mapper = Objects.requireNonNull(mapper);
        }

        private final BiFunction<List<String>, List<String>, List<String>> mapper;
    }


    private record ExpectedOutput(List<String> content, OutputMatchType type) {
        ExpectedOutput {
            Objects.requireNonNull(content);
            Objects.requireNonNull(type);
        }

        void test(List<String> lines) {
            var filteredLines = type.mapper.apply(content, lines);
            assertEquals(content, filteredLines);
        }
    }


    private enum ExceptionFormatter {
        GET_MESSAGE(errorMessage(Exception::getMessage)),
        MESSAGE_WITH_ADVICE(ex -> {
            var advice = Objects.requireNonNull(((ConfigException)ex).getAdvice());
            var sb = new StringBuilder();
            sb.append(GET_MESSAGE.format(ex));
            sb.append(I18N.format("message.advice-header", advice));
            sb.append(System.lineSeparator());
            return sb.toString();
        }),
        TO_STRING(errorMessage(Exception::toString)),
        STACK_TRACE(ex -> {
            var sink = new StringWriter();
            try (var pw = new PrintWriter(sink)) {
                ex.printStackTrace(pw);
            }
            return sink.toString();
        })
        ;

        ExceptionFormatter(Function<Exception, String> formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        String format(Exception v) {
            return formatter.apply(v);
        }

        private static Function<Exception, String> errorMessage(Function<Exception, String> formatter) {
            Objects.requireNonNull(formatter);
            return ex -> {
                var msg = formatter.apply(ex);
                return I18N.format("message.error-header", msg) + System.lineSeparator();
            };
        }

        private final Function<Exception, String> formatter;
    }


    record ErrorReporterTestSpec(Exception cause, Exception expect, boolean verbose, List<ExceptionFormatter> expectOutput) {

        ErrorReporterTestSpec {
            Objects.requireNonNull(cause);
            Objects.requireNonNull(expect);
            Objects.requireNonNull(expectOutput);
        }

        ErrorReporterTestSpec(Exception cause, boolean verbose, List<ExceptionFormatter> expectOutput) {
            this(cause, cause, verbose, expectOutput);
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();

            if (cause == expect) {
                tokens.add(cause.toString());
            } else {
                tokens.add(String.format("[%s] => [%s]", cause, expect));
            }

            tokens.add(expectOutput.stream().map(Enum::name).collect(Collectors.joining("+")));

            if (verbose) {
                tokens.add("verbose");
            }

            return tokens.stream().collect(Collectors.joining("; "));
        }

        void run() {
            var sink = new StringWriter();

            try (var pw = new PrintWriter(sink)) {
                new Main.ErrorReporter(t -> {
                    t.printStackTrace(pw);
                }, msg -> {
                    pw.println(msg);
                }, verbose).reportError(cause);
            }

            var expected = expectOutput.stream().map(formatter -> {
                return formatter.format(expect);
            }).collect(Collectors.joining(""));

            assertEquals(expected, sink.toString());
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
