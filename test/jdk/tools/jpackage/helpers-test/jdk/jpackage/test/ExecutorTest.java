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
package jdk.jpackage.test;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ExecutorTest extends JUnitAdapter {

    private record Command(List<String> stdout, List<String> stderr) {
        Command {
            stdout.forEach(Objects::requireNonNull);
            stderr.forEach(Objects::requireNonNull);
        }

        List<String> asExecutable() {
            final List<String> commandline = new ArrayList<>();
            if (TKit.isWindows()) {
                commandline.addAll(List.of("cmd", "/C"));
            } else {
                commandline.addAll(List.of("sh", "-c"));
            }
            commandline.add(Stream.concat(createEchoCommands(stdout),
                    createEchoCommands(stderr).map(v -> v + ">&2")).collect(joining(" && ")));
            return commandline;
        }

        private static Stream<String> createEchoCommands(List<String> lines) {
            return lines.stream().map(line -> {
                if (TKit.isWindows()) {
                    return "(echo " + line + ")";
                } else {
                    return "echo " + line;
                }
            });
        }

        ToolProvider asToolProvider() {
            return new ToolProvider() {

                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    stdout.forEach(out::println);
                    stderr.forEach(err::println);
                    return 0;
                }

                @Override
                public String name() {
                    return "test";
                }
            };
        }
    }

    private enum OutputData {
        EMPTY(List.of()),
        ONE_LINE(List.of("Jupiter")),
        MANY(List.of("Uranus", "Saturn", "Earth"));

        OutputData(List<String> data) {
            data.forEach(Objects::requireNonNull);
            this.data = data;
        }

        final List<String> data;
    }

    private record CommandSpec(OutputData stdout, OutputData stderr) {
        CommandSpec {
            Objects.requireNonNull(stdout);
            Objects.requireNonNull(stderr);
        }

        Command command() {
            return new Command(stdout.data.stream().map(line -> {
                return "stdout." + line;
            }).toList(), stderr.data.stream().map(line -> {
                return "stderr." + line;
            }).toList());
        }
    }

    public enum OutputControl {
        DUMP(Executor::dumpOutput),
        SAVE_ALL(Executor::saveOutput),
        SAVE_FIRST_LINE(Executor::saveFirstLineOfOutput),
        DISCARD_STDOUT(Executor::discardStdout),
        DISCARD_STDERR(Executor::discardStderr),
        ;

        OutputControl(Consumer<Executor> configureExector) {
            this.configureExector = Objects.requireNonNull(configureExector);
        }

        Executor applyTo(Executor exec) {
            configureExector.accept(exec);
            return exec;
        }

        static List<Set<OutputControl>> variants() {
            final List<Set<OutputControl>> variants = new ArrayList<>();
            for (final var withDump : BOOLEAN_VALUES) {
                variants.addAll(Stream.of(
                        Set.<OutputControl>of(),
                        Set.of(SAVE_ALL),
                        Set.of(SAVE_FIRST_LINE),
                        Set.of(DISCARD_STDOUT),
                        Set.of(DISCARD_STDERR),
                        Set.of(SAVE_ALL, DISCARD_STDOUT),
                        Set.of(SAVE_FIRST_LINE, DISCARD_STDOUT),
                        Set.of(SAVE_ALL, DISCARD_STDERR),
                        Set.of(SAVE_FIRST_LINE, DISCARD_STDERR),
                        Set.of(SAVE_ALL, DISCARD_STDOUT, DISCARD_STDERR),
                        Set.of(SAVE_FIRST_LINE, DISCARD_STDOUT, DISCARD_STDERR)
                ).map(v -> {
                    if (withDump) {
                        return Stream.concat(Stream.of(DUMP), v.stream()).collect(toSet());
                    } else {
                        return v;
                    }
                }).toList());
            }
            return variants.stream().map(options -> {
                return options.stream().filter(o -> {
                    return o.configureExector != NOP;
                }).collect(toSet());
            }).distinct().toList();
        }

        private final Consumer<Executor> configureExector;

        static final Set<OutputControl> SAVE = Set.of(SAVE_ALL, SAVE_FIRST_LINE);
    }

    public record OutputTestSpec(boolean toolProvider, Set<OutputControl> outputControl, CommandSpec commandSpec) {
        public OutputTestSpec {
            outputControl.forEach(Objects::requireNonNull);
            if (outputControl.containsAll(OutputControl.SAVE)) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(commandSpec);
        }

        @Override
        public String toString() {
            final List<String> tokens = new ArrayList<>();

            if (toolProvider) {
                tokens.add("tool-provider");
            }

            tokens.add("output=" + format(outputControl));
            tokens.add("command=" + commandSpec);

            return String.join(",", tokens.toArray(String[]::new));
        }

        void test() {
            final var command = commandSpec.command();
            final var commandWithDiscardedStreams = discardStreams(command);

            final Executor.Result[] result = new Executor.Result[1];
            final var outputCapture = OutputCapture.captureOutput(() -> {
                result[0] = createExecutor(command).executeWithoutExitCodeCheck();
            });

            assertEquals(0, result[0].getExitCode());

            assertEquals(expectedCapturedSystemOut(commandWithDiscardedStreams), outputCapture.outLines());
            assertEquals(expectedCapturedSystemErr(commandWithDiscardedStreams), outputCapture.errLines());

            assertEquals(expectedResultStdout(commandWithDiscardedStreams), result[0].stdout().getOutput());
            assertEquals(expectedResultStderr(commandWithDiscardedStreams), result[0].stderr().getOutput());

            if (!saveOutput()) {
                assertNull(result[0].getOutput());
            } else {
                assertNotNull(result[0].getOutput());
                final var allExpectedOutput = expectedCommandOutput(command);
                assertEquals(allExpectedOutput.isEmpty(), result[0].getOutput().isEmpty());
                if (!allExpectedOutput.isEmpty()) {
                    if (outputControl.contains(OutputControl.SAVE_ALL)) {
                        assertEquals(allExpectedOutput, result[0].getOutput());
                    } else if (outputControl.contains(OutputControl.SAVE_FIRST_LINE)) {
                        assertEquals(1, result[0].getOutput().size());
                        assertEquals(allExpectedOutput.getFirst(), result[0].getFirstLineOfOutput());
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        }

        private boolean dumpOutput() {
            return outputControl.contains(OutputControl.DUMP);
        }

        private boolean saveOutput() {
            return !Collections.disjoint(outputControl, OutputControl.SAVE);
        }

        private boolean discardStdout() {
            return outputControl.contains(OutputControl.DISCARD_STDOUT);
        }

        private boolean discardStderr() {
            return outputControl.contains(OutputControl.DISCARD_STDERR);
        }

        private static String format(Set<OutputControl> outputControl) {
            return outputControl.stream().map(OutputControl::name).sorted().collect(joining("+"));
        }

        private List<String> expectedCapturedSystemOut(Command command) {
            if (!dumpOutput() || (!toolProvider && !saveOutput())) {
                return List.of();
            } else if(saveOutput()) {
                return Stream.concat(command.stdout().stream(), command.stderr().stream()).toList();
            } else {
                return command.stdout();
            }
        }

        private List<String> expectedCapturedSystemErr(Command command) {
            if (!dumpOutput() || (!toolProvider && !saveOutput())) {
                return List.of();
            } else if(saveOutput()) {
                return List.of();
            } else {
                return command.stderr();
            }
        }

        private List<String> expectedResultStdout(Command command) {
            return expectedResultStream(command.stdout());
        }

        private List<String> expectedResultStderr(Command command) {
            if (outputControl.contains(OutputControl.SAVE_FIRST_LINE) && !command.stdout().isEmpty()) {
                return List.of();
            }
            return expectedResultStream(command.stderr());
        }

        private List<String> expectedResultStream(List<String> commandOutput) {
            Objects.requireNonNull(commandOutput);
            if (outputControl.contains(OutputControl.SAVE_ALL)) {
                return commandOutput;
            } else if (outputControl.contains(OutputControl.SAVE_FIRST_LINE)) {
                return commandOutput.stream().findFirst().map(List::of).orElseGet(List::of);
            } else {
                return null;
            }
        }

        private Command discardStreams(Command command) {
            return new Command(discardStdout() ? List.of() : command.stdout(), discardStderr() ? List.of() : command.stderr());
        }

        private record OutputCapture(byte[] out, byte[] err, Charset outCharset, Charset errCharset) {
            OutputCapture {
                Objects.requireNonNull(out);
                Objects.requireNonNull(err);
                Objects.requireNonNull(outCharset);
                Objects.requireNonNull(errCharset);
            }

            List<String> outLines() {
                return toLines(out, outCharset);
            }

            List<String> errLines() {
                return toLines(err, errCharset);
            }

            private static List<String> toLines(byte[] buf, Charset charset) {
                try (var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf), charset))) {
                    return reader.lines().filter(line -> {
                        return !line.contains("TRACE");
                    }).toList();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }

            static OutputCapture captureOutput(Runnable runnable) {
                final var captureOut = new ByteArrayOutputStream();
                final var captureErr = new ByteArrayOutputStream();

                final var out = System.out;
                final var err = System.err;
                try {
                    final var outCharset = System.out.charset();
                    final var errCharset = System.err.charset();
                    System.setOut(new PrintStream(captureOut, true, outCharset));
                    System.setErr(new PrintStream(captureErr, true, errCharset));
                    runnable.run();
                    return new OutputCapture(captureOut.toByteArray(), captureErr.toByteArray(), outCharset, errCharset);
                } finally {
                    try {
                        System.setOut(out);
                    } finally {
                        System.setErr(err);
                    }
                }
            }
        }

        private List<String> expectedCommandOutput(Command command) {
            command = discardStreams(command);
            return Stream.of(command.stdout(), command.stderr()).flatMap(List::stream).toList();
        }

        private Executor createExecutor(Command command) {
            final Executor exec;
            if (toolProvider) {
                exec = Executor.of(command.asToolProvider());
            } else {
                exec = Executor.of(command.asExecutable());
            }

            outputControl.forEach(control -> control.applyTo(exec));

            return exec;
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testSavedOutput(OutputTestSpec spec) {
        spec.test();
    }

    public static List<OutputTestSpec> testSavedOutput() {
        List<OutputTestSpec> testCases = new ArrayList<>();
        for (final var toolProvider : BOOLEAN_VALUES) {
            for (final var outputControl : OutputControl.variants()) {
                for (final var stdoutContent : List.of(OutputData.values())) {
                    for (final var stderrContent : List.of(OutputData.values())) {
                        final var commandSpec = new CommandSpec(stdoutContent, stderrContent);
                        testCases.add(new OutputTestSpec(toolProvider, outputControl, commandSpec));
                    }
                }
            }
        }
        return testCases;
    }

    private static final List<Boolean> BOOLEAN_VALUES = List.of(Boolean.TRUE, Boolean.FALSE);
    private static final Consumer<Executor> NOP = exec -> {};
}
