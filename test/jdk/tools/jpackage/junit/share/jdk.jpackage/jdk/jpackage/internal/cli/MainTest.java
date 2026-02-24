/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.model.ExecutableAttributesWithCapturedOutput.augmentResultWithOutput;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Globals;
import jdk.jpackage.internal.MockUtils;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExecutableAttributesWithCapturedOutput;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.SelfContainedException;
import jdk.jpackage.internal.util.CommandOutputControl;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedExitCodeException;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedResultException;
import jdk.jpackage.internal.util.IdentityWrapper;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.Annotations;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.mock.VerbatimCommandMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MainTest extends JUnitAdapter {

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

    /**
     * Run app image packaging and simulate jlink failure.
     * <p>
     * The test verifies that jpackage prints an error message with jlink exit code,
     * command line, and output.
     */
    @Annotations.Test
    public void testFailedCommandOutput() {

        var jlinkMockExitCode = 17;

        var jlinkArgs = new ArrayList<String>();

        var jlinkMock = CommandActionSpecs.build()
                .stdout("It").stderr("fell").stdout("apart")
                .argsListener(jlinkArgs::addAll)
                .exit(jlinkMockExitCode).create().toCommandMockBuilder().name("jlink-mock").create();

        var script = Script.build()
                // Replace jlink with the mock.
                .map(Script.cmdlineStartsWith("jlink"), jlinkMock)
                // Don't mock other external commands.
                .map(_ -> true, VerbatimCommandMock.INSTANCE)
                .createLoop();

        var jpackageExitCode = 1;

        var jpackageToolProviderMock = new ToolProvider() {
            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                var globalsMutator = MockUtils.buildJPackage().script(script).createGlobalsMutator();
                return Globals.main(() -> {
                    globalsMutator.accept(Globals.instance());

                    var result = ExecutionResult.create(args);

                    var jlinkMockAttrs = new CommandOutputControl.ToolProviderAttributes(jlinkMock.name(), jlinkArgs);

                    result.stdout().forEach(out::println);
                    result.stderr().forEach(err::println);

                    assertEquals(jpackageExitCode, result.exitCode());
                    assertEquals(List.of(), result.stdout());
                    assertEquals(List.of(
                            I18N.format("message.error-header", I18N.format("error.command-failed-unexpected-exit-code",
                                    jlinkMockExitCode, jlinkMockAttrs.printableCommandLine())),
                            I18N.format("message.failed-command-output-header"),
                            "It", "fell", "apart"), result.stderr());

                    return jpackageExitCode;
                });
            }

            @Override
            public String name() {
                return "jpackage-mock";
            }
        };

        JPackageCommand.helloAppImage()
                .ignoreDefaultVerbose(true)
                .useToolProvider(jpackageToolProviderMock)
                .execute(jpackageExitCode);
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
        var testCases = new ArrayList<ErrorReporterTestSpec>();
        for (var verbose : List.of(true, false)) {
            test_ErrorReporter_Exception(verbose, testCases::add);
            test_ErrorReporter_UnexpectedResultException(verbose, testCases::add);
            test_ErrorReporter_suppressedExceptions(verbose, testCases::add);
        }

        return testCases;
    }

    private static void test_ErrorReporter_Exception(boolean verbose, Consumer<ErrorReporterTestSpec> sink) {

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
                    new IOException("I/O error"),
                    new NullPointerException(),
                    new JPackageException("Kaput!"),
                    new ConfigException("It is broken", "Fix it!"),
                    new ConfigException("It is broken. No advice how to fix it", (String)null),
                    new Utils.ParseException("Malformed command line"),
                    new StandardOption.AddLauncherIllegalArgumentException("Malformed value of --add-launcher option")
            )) {
                var cause = makeCause.apply(expect);
                if (cause == null) {
                    continue;
                }

                var expectedOutput = new ArrayList<ExceptionFormatter>();
                ErrorReporterTestSpec.expectExceptionFormatters(expect, verbose, expectedOutput::add);
                sink.accept(ErrorReporterTestSpec.create(cause, expect, verbose, expectedOutput));
            }
        }
    }

    private static void test_ErrorReporter_UnexpectedResultException(boolean verbose, Consumer<ErrorReporterTestSpec> sink) {

        var execAttrs = new CommandOutputControl.ProcessAttributes(Optional.of(12345L), List.of("foo", "--bar"));

        for (var makeCause : List.<UnaryOperator<Exception>>of(
                ex -> ex,
                ExceptionBox::toUnchecked
        )) {

            for (var expect : List.of(
                    augmentResultWithOutput(
                            CommandOutputControl.Result.build().exitCode(135).execAttrs(execAttrs).create(),
                            "The quick brown fox\njumps over the lazy dog"
                    ).unexpected("Kaput!"),
                    new UnexpectedExitCodeException(augmentResultWithOutput(
                            CommandOutputControl.Result.build().exitCode(135).create(),
                            "The quick brown fox\njumps"
                    )),
                    augmentResultWithOutput(
                            CommandOutputControl.Result.build().create(),
                            "The quick brown fox\njumps"
                    ).unexpected("Timed out!")
            )) {
                var cause = makeCause.apply(expect);
                var expectedOutput = new ArrayList<ExceptionFormatter>();
                ErrorReporterTestSpec.expectExceptionFormatters(expect, verbose, expectedOutput::add);
                sink.accept(ErrorReporterTestSpec.create(cause, expect, verbose, expectedOutput));
            }
        }
    }

    private static Exception suppressException(Exception main, Exception suppressed) {
        Objects.requireNonNull(main);
        Objects.requireNonNull(suppressed);

        try (var autoCloseable = new AutoCloseable() {

            @Override
            public void close() throws Exception {
                throw suppressed;
            }}) {

            throw main;
        } catch (Exception ex) {
            return ex;
        }
    }

    private static void test_ErrorReporter_suppressedExceptions(boolean verbose, Consumer<ErrorReporterTestSpec> sink) {

        var execAttrs = new CommandOutputControl.ProcessAttributes(Optional.of(567L), List.of("foo", "--bar"));

        Supplier<Exception> createUnexpectedResultException = () -> {
            return augmentResultWithOutput(
                    CommandOutputControl.Result.build().exitCode(7).execAttrs(execAttrs).create(),
                    "The quick brown fox\njumps over the lazy dog"
            ).unexpected("Alas");
        };

        for (var makeCause : List.<UnaryOperator<Exception>>of(
                ex -> ex,
                ex -> {
                    var rex = ExceptionBox.toUnchecked(ex);
                    if (rex != ex) {
                        return rex;
                    } else {
                        return null;
                    }
                }
        )) {

            for (var exceptions : List.of(
                    List.<Exception>of(new JPackageException("Kaput!"), new JPackageException("Suppressed kaput")),
                    List.<Exception>of(new Exception("Kaput!"), new JPackageException("Suppressed kaput")),
                    List.<Exception>of(new Exception("Kaput!"), new Exception("Suppressed kaput")),
                    List.<Exception>of(new Exception("Kaput!"), ExceptionBox.toUnchecked(new Exception("Suppressed kaput"))),
                    List.<Exception>of(createUnexpectedResultException.get(), new Exception("Suppressed kaput")),
                    List.<Exception>of(new Exception("Alas!"), createUnexpectedResultException.get()),
                    List.<Exception>of(new JPackageException("Alas!"), createUnexpectedResultException.get())
            )) {
                var main = exceptions.getFirst();
                var suppressed = exceptions.getLast();

                var cause = makeCause.apply(suppressException(main, suppressed));

                if (cause == null) {
                    continue;
                }

                var expectedOutput = new ArrayList<FormattedException>();

                ErrorReporterTestSpec.expectOutputFragments(ExceptionBox.unbox(suppressed), verbose, expectedOutput::add);
                ErrorReporterTestSpec.expectOutputFragments(main, verbose, expectedOutput::add);

                sink.accept(new ErrorReporterTestSpec(cause, verbose, expectedOutput));
            }
        }
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


    private record FormattedException(ExceptionFormatter formatter, Exception exception) {

        FormattedException {
            Objects.requireNonNull(formatter);
            Objects.requireNonNull(exception);
        }

        String format() {
            return formatter.format(exception);
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
        }),
        FAILED_COMMAND_OUTPUT(ex -> {
            var result = failedCommandResult(ex);
            var commandOutput = ((ExecutableAttributesWithCapturedOutput)result.execAttrs()).printableOutput();

            var sink = new StringWriter();
            var pw = new PrintWriter(sink);

            pw.println(I18N.format("message.failed-command-output-header"));
            try (var lines = new BufferedReader(new StringReader(commandOutput)).lines()) {
                lines.forEach(pw::println);
            }

            return sink.toString();
        }),
        FAILED_COMMAND_UNEXPECTED_OUTPUT_MESSAGE(errorMessage(CommandFailureType.UNEXPECTED_OUTPUT::getMessage)),
        FAILED_COMMAND_UNEXPECTED_EXIT_CODE_MESSAGE(errorMessage(CommandFailureType.UNEXPECTED_EXIT_CODE::getMessage)),
        FAILED_COMMAND_TIMEDOUT_MESSAGE(errorMessage(CommandFailureType.TIMEDOUT::getMessage)),
        ;

        ExceptionFormatter(Function<Exception, String> formatter) {
            this.formatter = Objects.requireNonNull(formatter);
        }

        String format(Exception v) {
            return formatter.apply(v);
        }

        FormattedException bind(Exception v) {
            return new FormattedException(this, v);
        }

        private static Function<Exception, String> errorMessage(Function<Exception, String> formatter) {
            Objects.requireNonNull(formatter);
            return ex -> {
                var msg = formatter.apply(ex);
                return I18N.format("message.error-header", msg) + System.lineSeparator();
            };
        }

        private static CommandOutputControl.Result failedCommandResult(Exception ex) {
            Objects.requireNonNull(ex);
            if (ex instanceof UnexpectedResultException urex) {
                return urex.getResult();
            } else {
                throw new IllegalArgumentException();
            }
        }

        private enum CommandFailureType {
            UNEXPECTED_OUTPUT,
            UNEXPECTED_EXIT_CODE,
            TIMEDOUT,
            ;

            String getMessage(Exception ex) {
                var result = failedCommandResult(ex);
                var printableCommandLine = result.execAttrs().printableCommandLine();
                switch (this) {
                    case TIMEDOUT -> {
                        return I18N.format("error.command-failed-timed-out", printableCommandLine);
                    }
                    case UNEXPECTED_EXIT_CODE -> {
                        return I18N.format("error.command-failed-unexpected-exit-code", result.getExitCode(), printableCommandLine);
                    }
                    case UNEXPECTED_OUTPUT -> {
                        return I18N.format("error.command-failed-unexpected-output", printableCommandLine);
                    }
                    default -> {
                        // Unreachable
                        throw ExceptionBox.reachedUnreachable();
                    }
                }
            }
        }

        private final Function<Exception, String> formatter;
    }


    record ErrorReporterTestSpec(Exception cause, boolean verbose, List<FormattedException> expectOutput) {

        ErrorReporterTestSpec {
            Objects.requireNonNull(cause);
            Objects.requireNonNull(expectOutput);
            if (expectOutput.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        static ErrorReporterTestSpec create(
                Exception cause, boolean verbose, List<ExceptionFormatter> expectOutput) {
            return create(cause, cause, verbose, expectOutput);
        }

        static ErrorReporterTestSpec create(
                Exception cause, Exception expect, boolean verbose, List<ExceptionFormatter> expectOutput) {
            Objects.requireNonNull(cause);
            Objects.requireNonNull(expect);
            return new ErrorReporterTestSpec(cause, verbose, expectOutput.stream().map(formatter -> {
                return new FormattedException(formatter, expect);
            }).toList());
        }

        static void expectExceptionFormatters(Exception ex, boolean verbose, Consumer<ExceptionFormatter> sink) {
            Objects.requireNonNull(ex);
            Objects.requireNonNull(sink);

            final var isSelfContained = (ex.getClass().getAnnotation(SelfContainedException.class) != null);

            if (verbose || !(isSelfContained || ex instanceof UnexpectedResultException)) {
                sink.accept(ExceptionFormatter.STACK_TRACE);
            }

            switch (ex) {
                case ConfigException cex -> {
                    if (cex.getAdvice() != null) {
                        sink.accept(ExceptionFormatter.MESSAGE_WITH_ADVICE);
                    } else {
                        sink.accept(ExceptionFormatter.GET_MESSAGE);
                    }
                }
                case UnexpectedResultException urex -> {
                    if (urex instanceof UnexpectedExitCodeException) {
                        sink.accept(ExceptionFormatter.FAILED_COMMAND_UNEXPECTED_EXIT_CODE_MESSAGE);
                    } else if (urex.getResult().exitCode().isPresent()) {
                        sink.accept(ExceptionFormatter.FAILED_COMMAND_UNEXPECTED_OUTPUT_MESSAGE);
                    } else {
                        sink.accept(ExceptionFormatter.FAILED_COMMAND_TIMEDOUT_MESSAGE);
                    }
                    sink.accept(ExceptionFormatter.FAILED_COMMAND_OUTPUT);
                }
                default -> {
                    if (isSelfContained) {
                        sink.accept(ExceptionFormatter.GET_MESSAGE);
                    } else {
                        sink.accept(ExceptionFormatter.TO_STRING);
                    }
                }
            }
        }

        static void expectOutputFragments(Exception ex, boolean verbose, Consumer<FormattedException> sink) {
            Objects.requireNonNull(sink);
            expectExceptionFormatters(ex, verbose, formatter -> {
                sink.accept(formatter.bind(ex));
            });
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();

            var expect = expectOutput.stream()
                    .map(FormattedException::exception)
                    .map(IdentityWrapper::new)
                    .distinct()
                    .toList();

            if (expect.size() == 1 && expect.getFirst().value() == cause) {
                tokens.add(cause.toString());
            } else {
                tokens.add(String.format("[%s] => %s", cause, expect.stream().map(IdentityWrapper::value).toList()));
            }

            if (expect.size() == 1) {
                tokens.add(expectOutput.stream().map(FormattedException::formatter).map(Enum::name).collect(Collectors.joining("+")));
            } else {
                tokens.add(expectOutput.stream().map(fragment -> {
                    var idx = expect.indexOf(IdentityWrapper.wrapIdentity(fragment.exception()));
                    return String.format("%s@%d", fragment.formatter(), idx);
                }).collect(Collectors.joining("+")));
            }

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

            var expected = expectOutput.stream().map(FormattedException::format).collect(Collectors.joining(""));

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
