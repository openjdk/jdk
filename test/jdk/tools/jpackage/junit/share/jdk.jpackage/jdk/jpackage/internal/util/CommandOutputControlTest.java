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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class CommandOutputControlTest {

//    @Disabled
    @DisabledIf("cherryPickSavedOutputTestCases")
    @ParameterizedTest
    @MethodSource
    public void testSavedOutput(OutputTestSpec spec) {
        spec.test();
    }

    @EnabledIf("cherryPickSavedOutputTestCases")
    @ParameterizedTest
    @MethodSource
    public void testSomeSavedOutput(OutputTestSpec spec) {
        System.out.println(spec);
        spec.test();
    }

    @ParameterizedTest
    @MethodSource
    public void testDescription(CommandOutputControlSpec spec) {
        // This test is mostly for coverage.
        var desc = spec.create().description();
        assertFalse(desc.isBlank());
    }

    @Test
    public void testCopy_default() {
        var orig = new CommandOutputControl();
        var copy = orig.copy();
        assertEquals(orig, copy);
        assertNotSame(orig, copy);

        assertNotEquals(orig, copy.discardStdout(true));
    }

    @Test
    public void testCopy_some_overrides() {
        var orig = new CommandOutputControl()
                .processOutputCharset(StandardCharsets.US_ASCII)
                .discardStderr(true)
                .saveOutput(true)
                .dumpOutput(true)
                .redirectErrorStream(true)
                .storeStreamsInFiles(true);
        var copy = orig.copy();
        assertEquals(orig, copy);
        assertNotSame(orig, copy);

        assertNotEquals(orig, copy.discardStdout(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExecutableSpec(boolean toolProvider) {
        var coc = new CommandOutputControl();
        CommandOutputControl.Executable exec;
        if (toolProvider) {
            exec = coc.createExecutable(new ToolProvider() {

                @Override
                public String name() {
                    return "runme";
                }

                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    throw new UnsupportedOperationException();
                }

            }, "--foo", "--baz=10");
        } else {
            exec = coc.createExecutable(new ProcessBuilder("runme", "--foo", "--baz=10"));
        }

        assertEquals("runme --foo --baz=10", exec.spec().toString());
    }

    @Test
    public void test_Result_no_args_ctor() {
        var result = new CommandOutputControl.Result(7);
        assertFalse(result.findContent().isPresent());
        assertFalse(result.findStdout().isPresent());
        assertFalse(result.findStderr().isPresent());
        assertEquals(7, result.getExitCode());
        assertSame(Objects.requireNonNull(CommandOutputControl.EMPTY_EXECUTABLE_SPEC), result.execSpec());
    }

    @Test
    public void test_Result_expectExitCode() throws IOException {
        var result = new CommandOutputControl.Result(7);

        assertSame(result, result.expectExitCode(7));
        assertSame(result, result.expectExitCode(7, 2));
        assertSame(result, result.expectExitCode(2, 7));

        assertSame(result, result.expectExitCode(List.of(7)));
        assertSame(result, result.expectExitCode(Set.of(7, 2)));
        assertSame(result, result.expectExitCode(List.of(2, 7)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_Result_expectExitCode_negative(boolean collection) {
        var result = new CommandOutputControl.Result(3);

        var ex = assertThrowsExactly(CommandOutputControl.UnexpectedExitCodeException.class, () -> {
            if (collection) {
                result.expectExitCode(List.of(17, 12));
            } else {
                result.expectExitCode(17, 12);
            }
        });

        assertNull(ex.getCause());
        assertSame(result, ex.getResult());
        assertEquals("Unexpected exit code 3 from executing the command <unknown>", ex.getMessage());
    }

    private static boolean cherryPickSavedOutputTestCases() {
        return !testSomeSavedOutput().isEmpty();
    }

    private static List<CommandOutputControlSpec> testDescription() {
        List<CommandOutputControlSpec> testCases = new ArrayList<>();
        for (final var toolProvider : BOOLEAN_VALUES) {
            testCases.add(new CommandOutputControlSpec(Set.of()));
            for (var outputControl : OutputControl.variants()) {
                testCases.add(new CommandOutputControlSpec(outputControl));
            }
        }
        return testCases;
    }

    private static List<OutputTestSpec> testSomeSavedOutput() {
        var testIds = List.<Integer>of(/* 10, 67, 456 */);
        if (testIds.isEmpty()) {
            return List.of();
        } else {
            var allTestCases = testSavedOutput();
            return testIds.stream().map(testId -> {
                return allTestCases.get(testId - 1);
            }).toList();
        }
    }

    private static List<OutputTestSpec> testSavedOutput() {
        List<OutputTestSpec> testCases = new ArrayList<>();
        for (final var executableType : List.of(ExecutableType.values())) {
            for (var outputControl : OutputControl.variants()) {
                for (final var stdoutContent : List.of(OutputData.values())) {
                    for (final var stderrContent : List.of(OutputData.values())) {
                        final var commandSpec = new CommandSpec(stdoutContent, stderrContent);
                        boolean toolProvider;
                        switch (executableType) {
                            case PROCESS_BUILDER -> {
                                toolProvider = false;
                            }
                            case PROCESS_BUILDER_WITH_STREAMS_IN_FILES -> {
                                outputControl = new SetBuilder<OutputControl>()
                                        .add(outputControl)
                                        .add(OutputControl.STORE_STREAMS_IN_FILES)
                                        .create();
                                toolProvider = false;
                            }
                            case TOOL_PROVIDER -> {
                                toolProvider = true;
                            }
                            default -> {
                                // Unreachable
                                throw new IllegalStateException();
                            }
                        }
                        testCases.add(new OutputTestSpec(
                                toolProvider,
                                new CommandOutputControlSpec(outputControl),
                                commandSpec));
                    }
                }
            }
        }
        return testCases;
    }

    private enum ExecutableType {
        TOOL_PROVIDER,
        PROCESS_BUILDER,
        PROCESS_BUILDER_WITH_STREAMS_IN_FILES,
        ;
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
            for (final var redirectStderr : BOOLEAN_VALUES) {
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

    public record CommandOutputControlSpec(Set<OutputControl> outputControl) {
        public CommandOutputControlSpec {
            outputControl.forEach(Objects::requireNonNull);
            if (outputControl.containsAll(OutputControl.SAVE)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return outputControl.stream().map(OutputControl::name).sorted().collect(joining("+"));
        }

        boolean contains(OutputControl v) {
            return outputControl.contains(Objects.requireNonNull(v));
        }

        boolean dumpOutput() {
            return contains(OutputControl.DUMP);
        }

        boolean saveOutput() {
            return !Collections.disjoint(outputControl, OutputControl.SAVE);
        }

        boolean discardStdout() {
            return contains(OutputControl.DISCARD_STDOUT);
        }

        boolean discardStderr() {
            return contains(OutputControl.DISCARD_STDERR);
        }

        boolean redirectStderr() {
            return contains(OutputControl.REDIRECT_STDERR);
        }

        CommandOutputControl create() {
            final CommandOutputControl coc = new CommandOutputControl();
            outputControl.forEach(control -> control.applyTo(coc));
            return coc;
        }
    }

    public record OutputTestSpec(boolean toolProvider, CommandOutputControlSpec cocSpec, CommandSpec commandSpec) {
        public OutputTestSpec {
            Objects.requireNonNull(cocSpec);
            Objects.requireNonNull(commandSpec);
        }

        @Override
        public String toString() {
            final List<String> tokens = new ArrayList<>();

            if (toolProvider) {
                tokens.add("tool-provider");
            }

            tokens.add("output=" + cocSpec.toString());
            tokens.add("command=" + commandSpec);

            return String.join(", ", tokens.toArray(String[]::new));
        }

        void test() {
            final var command = commandSpec.command();

            final Slot<CommandOutputControl.Result> result = Slot.createEmpty();
            final var dumpCapture = DumpCapture.captureDump(toRunnable(() -> {
                result.set(createExecutable(command).execute());
            }));

            assertEquals(0, result.get().getExitCode());

            verifyDump(dumpCapture, command);
            verifyResultContent(result.get(), command);
        }

        boolean contains(OutputControl v) {
            return cocSpec.contains(v);
        }

        private boolean dumpOutput() {
            return cocSpec.dumpOutput();
        }

        private boolean saveOutput() {
            return cocSpec.saveOutput();
        }

        private boolean discardStdout() {
            return cocSpec.discardStdout();
        }

        private boolean discardStderr() {
            return cocSpec.discardStderr();
        }

        private boolean redirectStderr() {
            return cocSpec.redirectStderr();
        }

        private boolean replaceStdoutWithStderr() {
            return redirectStderr() && discardStdout() && !discardStderr();
        }

        private void verifyDump(DumpCapture dumpCapture, Command command) {
            if (replaceStdoutWithStderr()) {
                if (dumpOutput()) {
                    assertEquals(command.stderr(), dumpCapture.outLines());
                } else {
                    assertEquals(List.of(), dumpCapture.outLines());
                }
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            if (!dumpOutput() || (!toolProvider && !saveOutput())) {
                // Dump of output streams is disabled, or it is enabled
                // for a subprocess without saving the content of the output streams.
                // In the later case the test can't capture dumped content as
                // it goes into the STDOUT/STERR streams associated with the Java process.
                assertEquals(List.of(), dumpCapture.outLines());
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            if (redirectStderr() && !discardStderr()) {
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
                    var capturedDump = new ArrayList<>(dumpCapture.outLines());
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

        private void verifyResultContent(CommandOutputControl.Result result, Command command) {
            if (!saveOutput()) {
                assertTrue(result.findContent().isEmpty());
                assertTrue(result.findStdout().isEmpty());
                assertTrue(result.findStderr().isEmpty());
                return;
            }

            assertTrue(result.findContent().isPresent());

            if (redirectStderr() && !Collections.disjoint(command.stdout(), command.stderr())) {
                // Intertwined STDOUT and STDERR
                throw new UnsupportedOperationException("Testee stdout and stderr must be disjoint");
            }

            command = filterSavedStreams(command);

            if (!redirectStderr()) {
                assertEquals(command.stdout(), result.stdout().getContent());
                assertEquals(command.stderr(), result.stderr().getContent());
                assertEquals(Stream.of(
                        command.stdout(),
                        command.stderr()
                ).flatMap(List::stream).toList(), result.getContent());
            } else {
                assertEquals(discardStderr(), result.findStdout().isPresent());
                assertTrue(result.findStderr().isEmpty());
                if (contains(OutputControl.SAVE_FIRST_LINE)) {
                    assertTrue(List.of(command.stdout(), command.stderr()).contains(result.getContent()),
                            String.format("Saved content %s is either %s or %s",
                                    result.getContent(), command.stdout(), command.stderr()));
                } else if (contains(OutputControl.SAVE_ALL)) {
                    var savedContent = new ArrayList<>(result.getContent());
                    savedContent.removeAll(command.stdout());
                    savedContent.removeAll(command.stderr());
                    assertEquals(List.of(), savedContent);
                } else {
                    // Unreachable
                    throw new AssertionError();
                }
            }
        }

        private List<String> expectedSavedStream(List<String> commandOutput) {
            Objects.requireNonNull(commandOutput);
            if (contains(OutputControl.SAVE_ALL)) {
                return commandOutput;
            } else if (contains(OutputControl.SAVE_FIRST_LINE)) {
                return commandOutput.stream().findFirst().map(List::of).orElseGet(List::of);
            } else {
                throw new IllegalStateException();
            }
        }

        private Command filterSavedStreams(Command command) {
            return new Command(
                    (discardStdout() ? List.of() : expectedSavedStream(command.stdout())),
                    (discardStderr() ? List.of() : expectedSavedStream(command.stderr())));
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

        private CommandOutputControl.Executable createExecutable(Command command) {
            final var coc = cocSpec.create();
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
