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
package jdk.jpackage.internal.util;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class CommandOutputControlTest {

    @ParameterizedTest
    @MethodSource
    public void testSavedOutput(OutputTestSpec spec) {
        spec.test();
    }

    private static List<OutputTestSpec> testSavedOutput() {
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

    private record Command(List<String> stdout, List<String> stderr) {
        Command {
            stdout.forEach(Objects::requireNonNull);
            stderr.forEach(Objects::requireNonNull);
        }

        List<String> asExecutable() {
            final List<String> commandline = new ArrayList<>();
            if (OperatingSystem.isWindows()) {
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
                if (OperatingSystem.isWindows()) {
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
        DUMP(coc -> {
            coc.dumpOutput(true);
        }),
        SAVE_ALL(coc -> {
            coc.saveOutput(true);
        }),
        SAVE_FIRST_LINE(CommandOutputControl::saveFirstLineOfOutput),
        DISCARD_STDOUT(coc -> {
            coc.discardStdout(true);
        }),
        DISCARD_STDERR(coc -> {
            coc.discardStderr(true);
        }),
        REDIRECT_STDERR(coc -> {
            coc.redirectErrorStream(true);
        }),
        STORE_STREAMS_IN_FILES(coc -> {
            coc.storeStreamsInFiles(true);
        }),
        ;

        OutputControl(Consumer<CommandOutputControl> mutator) {
            this.mutator = Objects.requireNonNull(mutator);
        }

        CommandOutputControl applyTo(CommandOutputControl coc) {
            mutator.accept(coc);
            return coc;
        }

        static List<Set<OutputControl>> variants() {
            final List<Set<OutputControl>> variants = new ArrayList<>();
            for (final var redirectStderr : List.of(false)) {
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
                            return new SetBuilder<OutputControl>().add(v).add(DUMP).create();
                        } else {
                            return v;
                        }
                    }).map(v -> {
                        if (redirectStderr && !v.containsAll(List.of(DISCARD_STDOUT, DISCARD_STDERR))) {
                            return new SetBuilder<OutputControl>().add(v).add(REDIRECT_STDERR).create();
                        } else {
                            return v;
                        }
                    }).toList());
                }
            }
            return variants.stream().map(options -> {
                return options.stream().filter(o -> {
                    return o.mutator != NOP;
                }).collect(toSet());
            }).distinct().toList();
        }

        private final Consumer<CommandOutputControl> mutator;

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

            final Slot<CommandOutputControl.Result> result = Slot.createEmpty();
            final var dumpCapture = DumpCapture.captureDump(toRunnable(() -> {
                result.set(createExecutable(command).execute());
            }));

            assertEquals(0, result.get().getExitCode());

            verifyDump(dumpCapture, command);
            verifyResult(result.get(), command);

            if (!saveOutput()) {
                assertThrowsExactly(NoSuchElementException.class, result.get()::getOutput);
            } else {
                assertNotNull(result.get().getOutput());
                final var allExpectedOutput = expectedCommandOutput(command);
                assertEquals(allExpectedOutput.isEmpty(), result.get().getOutput().isEmpty());
                if (!allExpectedOutput.isEmpty()) {
                    if (outputControl.contains(OutputControl.SAVE_ALL)) {
                        assertEquals(allExpectedOutput, result.get().getOutput());
                    } else if (outputControl.contains(OutputControl.SAVE_FIRST_LINE)) {
                        assertEquals(1, result.get().getOutput().size());
                        assertEquals(allExpectedOutput.getFirst(), result.get().getFirstLineOfOutput());
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

        private boolean redirectStderr() {
            return outputControl.contains(OutputControl.REDIRECT_STDERR);
        }

        private static String format(Set<OutputControl> outputControl) {
            return outputControl.stream().map(OutputControl::name).sorted().collect(joining("+"));
        }

        private void verifyDump(DumpCapture dumpCapture, Command command) {
            if (!dumpOutput() || (!toolProvider && !saveOutput())) {
                // Dump of output streams is disabled, or it is enabled 
                // for a subprocess without saving the content of the output streams.
                // In the later case the test can't capture dumped content as 
                // it goes into the STDOUT/STERR streams associated with the Java process.
                assertEquals(List.of(), dumpCapture.outLines());
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            if (redirectStderr()) {
                assertEquals(List.of(), dumpCapture.errLines());
                if (discardStdout() && !discardStderr()) {
                    // STDERR replaces STDOUT
                    assertEquals(command.stderr(), dumpCapture.outLines());
                } else if (!discardStdout() && discardStderr()) {
                    assertEquals(command.stdout(), dumpCapture.outLines());
                } else {
                    // Intertwined STDOUT and STDERR
                    if (!Collections.disjoint(command.stdout(), command.stderr())) {
                        throw new UnsupportedOperationException("Testee stdout and stderr must be disjoint");
                    }
                    var capturedDump = new ArrayList<String>(dumpCapture.outLines());
                    capturedDump.removeAll(command.stdout());
                    capturedDump.removeAll(command.stderr());
                    assertEquals(List.of(), capturedDump);
                }
            } else {
                if (discardStdout()) {
                    assertEquals(List.of(), dumpCapture.outLines());
                } else {
                    assertEquals(command.stdout(), dumpCapture.outLines());
                }

                if (discardStderr()) {
                    assertEquals(List.of(), dumpCapture.errLines());
                } else {
                    assertEquals(command.stderr(), dumpCapture.errLines());
                }
            }
        }

        private void verifyResult(CommandOutputControl.Result result, Command command) {
            if (!saveOutput()) {
                assertTrue(result.findContent().isEmpty());
                assertTrue(result.findStdout().isEmpty());
                assertTrue(result.findStderr().isEmpty());
                return;
            }
        }

        private Optional<List<String>> expectedResultStdout(Command command) {
            return expectedResultStream(command.stdout());
        }

        private Optional<List<String>> expectedResultStderr(Command command) {
            if (outputControl.contains(OutputControl.SAVE_FIRST_LINE) && !command.stdout().isEmpty()) {
                return Optional.of(List.of());
            }
            return expectedResultStream(command.stderr());
        }

        private Optional<List<String>> expectedResultStream(List<String> commandOutput) {
            Objects.requireNonNull(commandOutput);
            if (outputControl.contains(OutputControl.SAVE_ALL)) {
                return Optional.of(commandOutput);
            } else if (outputControl.contains(OutputControl.SAVE_FIRST_LINE)) {
                return Optional.of(commandOutput.stream().findFirst().map(List::of).orElseGet(List::of));
            } else {
                return Optional.empty();
            }
        }

        private Command discardStreams(Command command) {
            return new Command(discardStdout() ? List.of() : command.stdout(), discardStderr() ? List.of() : command.stderr());
        }

        private record DumpCapture(byte[] out, byte[] err, Charset outCharset, Charset errCharset) {
            DumpCapture {
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

            static DumpCapture captureDump(Runnable runnable) {
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
                    return new DumpCapture(captureOut.toByteArray(), captureErr.toByteArray(), outCharset, errCharset);
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

        private CommandOutputControl.Executable createExecutable(Command command) {
            final CommandOutputControl coc = new CommandOutputControl();
            outputControl.forEach(control -> control.applyTo(coc));

            if (toolProvider) {
                return coc.createExecutable(command.asToolProvider());
            } else {
                return coc.createExecutable(new ProcessBuilder(command.asExecutable()));
            }
        }
    }

    private static final List<Boolean> BOOLEAN_VALUES = List.of(Boolean.TRUE, Boolean.FALSE);
    private static final Consumer<CommandOutputControl> NOP = exec -> {};
}
