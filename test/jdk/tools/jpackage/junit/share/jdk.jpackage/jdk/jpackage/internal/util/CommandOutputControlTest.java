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
import static jdk.jpackage.internal.util.CommandOutputControlTestUtils.isInterleave;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static jdk.jpackage.test.JUnitUtils.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.function.ExceptionBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class CommandOutputControlTest {

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
    public void testDumpStreams(OutputTestSpec spec) {
        spec.test();
    }

    @ParameterizedTest
    @MethodSource
    public void test_description(CommandOutputControlSpec spec) {
        // This test is mostly for coverage.
        var desc = spec.create().description();
        assertFalse(desc.isBlank());
//        System.out.println(desc);
    }

    @Test
    public void test_copy() {
        var orig = new CommandOutputControl();
        var copy = orig.copy();
        assertNotSame(orig, copy);
    }

    @ParameterizedTest
    @EnumSource(names = "SAVE_NOTHING", mode = Mode.EXCLUDE)
    public void test_flag(OutputControl flag) {
        var coc = new CommandOutputControl();
        assertFalse(flag.get(coc));
        flag.set(coc);
        assertTrue(flag.get(coc));
        if (flag.canUnset()) {
            flag.unset(coc);
            assertFalse(flag.get(coc));
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test_mutual_exclusive_flags(List<OutputControl> controls) {
        if (controls.isEmpty()) {
            throw new IllegalArgumentException();
        }

        var coc = new CommandOutputControl();
        for (var c : controls) {
            c.set(coc);
        }

        for (var c : controls.subList(0, controls.size() - 1)) {
            assertFalse(c.get(coc));
        }
        assertTrue(controls.getLast().get(coc));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_ExecutableSpec(boolean toolProvider) {
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
                    fail("Should never be called");
                    return 0;
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

    @ParameterizedTest
    @MethodSource
    public void test_Result_toCharacterResult(ToCharacterResultTestSpec spec) throws IOException, InterruptedException {
        spec.test();
    }

    @Test
    public void test_Result_toCharacterResult_nop() throws IOException, InterruptedException {

        var charset = StandardCharsets.UTF_8;

        var emptyResult = new CommandOutputControl.Result(7);
        assertSame(emptyResult, emptyResult.toCharacterResult(charset, true));
        assertSame(emptyResult, emptyResult.toCharacterResult(charset, false));

        var coc = new CommandOutputControl().saveOutput(true);

        var result = coc.createExecutable(new Command(List.of("foo"), List.of()).asToolProvider()).execute();

        assertSame(result, result.toCharacterResult(charset, true));
        assertSame(result, result.toCharacterResult(charset, false));
    }

    @Test
    public void test_Result_toCharacterResult_copyWithExecutableSpec() {

        var empty = new CommandOutputControl.Result(0);

        var execSpec = new CommandOutputControl.ExecutableSpec() {
            @Override
            public String toString() {
                return "foo";
            }
        };

        var copy = empty.copyWithExecutableSpec(execSpec);

        assertSame(empty.exitCode(), copy.exitCode());
        assertSame(empty.output(), copy.output());
        assertSame(empty.byteOutput(), copy.byteOutput());
        assertSame(execSpec, copy.execSpec());
    }

    @ParameterizedTest
    @EnumSource(ExecutableType.class)
    public void test_timeout_expires(ExecutableType mode) throws InterruptedException, IOException {

        final var toolProvider = (mode == ExecutableType.TOOL_PROVIDER);
        final var storeOutputInFiles = (mode == ExecutableType.PROCESS_BUILDER_WITH_STREAMS_IN_FILES);

        var actions = List.<CommandAction>of(
                CommandAction.echoStdout("The quick brown fox jumps"),
                CommandAction.sleep(5),
                CommandAction.echoStdout("over the lazy dog")
        );

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true).storeOutputInFiles(storeOutputInFiles);

        CommandOutputControl.Executable exec;

        InterruptibleToolProvider tp;

        if (toolProvider) {
            tp = new InterruptibleToolProvider(Command.createToolProvider(actions));
            exec = coc.createExecutable(tp);
        } else {
            var cmdline = Command.createShellCommandLine(actions);
            tp = null;
            exec = coc.createExecutable(new ProcessBuilder(cmdline));
        }

        var result = exec.execute(1, TimeUnit.SECONDS);
        assertFalse(result.exitCode().isPresent());

        var getExitCodeEx = assertThrowsExactly(IllegalStateException.class, result::getExitCode);
        assertEquals(("Exit code is unavailable for timed-out command"), getExitCodeEx.getMessage());

        // We want to check that the saved output contains only the text emitted before the "sleep" action.
        // It works for a subprocess, but in the case of a ToolProvider, sometimes the timing is such
        // that it gets interrupted before having written anything to the stdout, and the saved output is empty.
        // This happens when the test case is executed together with other test cases
        // and never when it is executed individually.
        if (!toolProvider || !result.content().isEmpty()) {
            assertEquals(List.of("The quick brown fox jumps"), result.content());
        }

        if (toolProvider) {
            assertTrue(tp.interrupted());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_timeout(boolean toolProvider) throws InterruptedException, IOException {

        var actions = List.<CommandAction>of(
                CommandAction.echoStdout("Sphinx of black quartz,"),
                CommandAction.echoStdout("judge my vow")
        );

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true);

        CommandOutputControl.Executable exec;

        if (toolProvider) {
            var tp = Command.createToolProvider(actions);
            exec = coc.createExecutable(tp);
        } else {
            var cmdline = Command.createShellCommandLine(actions);
            exec = coc.createExecutable(new ProcessBuilder(cmdline));
        }

        var result = exec.execute(10, TimeUnit.SECONDS);
        assertTrue(result.exitCode().isPresent());
        assertEquals(List.of("Sphinx of black quartz,", "judge my vow"), result.content());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_passthrough_exceptions(boolean withTimeout) throws IOException {

        var expected = new RuntimeException("Kaput!");

        var exec = new CommandOutputControl().createExecutable(new ToolProvider() {

            @Override
            public String name() {
                return "foo";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                throw expected;
            }
        });

        var actual = assertThrowsExactly(expected.getClass(), () -> {
            if (withTimeout) {
                exec.execute(10, TimeUnit.SECONDS);
            } else {
                exec.execute();
            }
        });

        assertSame(expected, actual);
    }

    @Test
    public void test_externally_terminated() throws InterruptedException, IOException {
        var cmdline = Command.createShellCommandLine(List.<CommandAction>of(
                CommandAction.echoStderr("The five boxing wizards"),
                CommandAction.sleep(10),
                CommandAction.echoStderr("jump quickly")
        ));

        var processDestroyer = Slot.<CompletableFuture<Void>>createEmpty();

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true).processNotifier(process -> {
            // Once we are notified the process has been started, schedule its destruction.
            // Give it a second to warm up and print some output and then destroy it.
            processDestroyer.set(CompletableFuture.runAsync(toRunnable(() -> {
                Thread.sleep(Duration.ofSeconds(1));
                // On Windows, CommandAction#sleep is implemented with the "ping" command.
                // By some reason, when the parent "cmd" process is destroyed,
                // the child "ping" command stays alive, and the test waits when it completes,
                // making it last for at least 10 seconds.
                // To optimize the test work time, destroy the entire subprocess tree.
                // Even though this is essential on Windows keep this logic on all platforms for simplicity.
                var descendants = List.<ProcessHandle>of();
                try (var descendantsStream = process.descendants()) {
                    descendants = descendantsStream.toList();
                } finally {
                    process.destroyForcibly();
                }
                descendants.forEach(ProcessHandle::destroyForcibly);
            })));
        });
        var exec = coc.createExecutable(new ProcessBuilder(cmdline));

        var result = exec.execute();
        assertNotEquals(0, result.getExitCode());
        assertEquals(List.of("The five boxing wizards"), result.content());
        processDestroyer.get().join();
    }

    @ParameterizedTest
    @EnumSource(CloseStream.class)
    public void test_close_streams(CloseStream action) throws InterruptedException, IOException {
        var cmdline = Command.createShellCommandLine(List.<CommandAction>of(
                CommandAction.echoStdout("Hello stdout"),
                CommandAction.echoStderr("Bye stderr")
        ));

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true).processNotifier(toConsumer(process -> {
            // Close process output stream(s). This should make corresponding stream gobbler(s) throw IOException.
            switch (action) {
                case STDOUT -> {
                    process.getInputStream().close();
                }
                case STDERR -> {
                    process.getErrorStream().close();
                }
                case STDOUT_AND_STDERR -> {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                }
            }
        }));
        var exec = coc.createExecutable(new ProcessBuilder(cmdline));

        var ex = assertThrows(IOException.class, exec::execute);
        System.out.println("test_close_streams: " + action);
        ex.printStackTrace(System.out);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_interleaved(boolean customDumpStreams) throws IOException, InterruptedException {
        var cmdline = Command.createShellCommandLine(List.<CommandAction>of(
                CommandAction.echoStdout("Eat some more"),
                CommandAction.echoStderr("of these"),
                CommandAction.echoStdout("soft French pastries"),
                CommandAction.echoStderr("and drink some tea")
        ));

        var coc = new CommandOutputControl();
        var exec = coc.createExecutable(new ProcessBuilder(cmdline));

        coc.saveOutput(true).dumpOutput(true);

        CommandOutputControl.Result result;

        if (customDumpStreams) {
            // Execute the command so that its stdout and stderr are dumped to the same sink.
            var sink = new ByteArrayOutputStream();
            var ps = new PrintStream(sink);

            coc.dumpStdout(ps).dumpStderr(ps);

            result = exec.execute();

            var commandStdout = List.of("Eat some more", "soft French pastries");
            var commandStderr = List.of("of these", "and drink some tea");

            var sinkContent = toStringList(sink.toByteArray(), StandardCharsets.US_ASCII);

            if (!isInterleave(sinkContent, commandStdout, commandStderr)) {
                fail(String.format("Unexpected combined output=%s; stdout=%s; stderr=%s",
                        sinkContent, commandStdout, commandStderr));
            }

            // CommandOutputControl was not configured to redirect stderr in stdout,
            // hence the output is ordered: stdout goes first, stderr follows.
            assertEquals(Stream.of(commandStdout, commandStderr).flatMap(List::stream).toList(), result.content());

            // Saved stdout an stderr can be accessed individually.
            assertEquals(commandStdout, result.stdout());
            assertEquals(commandStderr, result.stderr());
        } else {
            // Execute the command so that its stdout and stderr are dumped into System.out.
            coc.redirectStderr(true);
            result = exec.execute();

            // CommandOutputControl was configured to redirect stderr in stdout,
            // hence the output is interleaved.
            assertEquals(List.of("Eat some more", "of these", "soft French pastries", "and drink some tea"), result.content());

            // Saved stdout an stderr can NOT be accessed individually because they are interleaved.
            assertTrue(result.findStdout().isEmpty());
            assertTrue(result.findStderr().isEmpty());
        }
    }

    public enum CloseStream {
        STDOUT,
        STDERR,
        STDOUT_AND_STDERR
    }

    private static List<CommandOutputControlSpec> test_description() {
        List<CommandOutputControlSpec> testCases = new ArrayList<>();
        testCases.add(new CommandOutputControlSpec(Set.of()));
        for (var outputControl : OutputControl.variants()) {
            testCases.add(new CommandOutputControlSpec(outputControl));
        }
        return testCases;
    }

    private static List<List<OutputControl>> test_mutual_exclusive_flags() {
        List<List<OutputControl>> data = new ArrayList<>();

        var flags = List.of(OutputControl.SAVE_ALL, OutputControl.SAVE_FIRST_LINE, OutputControl.SAVE_NOTHING);

        List<OutputControl> seq = new ArrayList<>();
        for (var _1 : flags) {
            seq.add(_1);
            var flags2 = flags.stream().filter(Predicate.isEqual(_1).negate()).toList();
            for (var _2 : flags2) {
                seq.add(_2);
                var flags3 = flags2.stream().filter(Predicate.isEqual(_2).negate()).toList();
                for (var _3 : flags3) {
                    seq.add(_3);
                    data.add(List.copyOf(seq));
                    seq.removeLast();
                }
                seq.removeLast();
            }
            seq.removeLast();
        }

        return data;
    }

    public record ToCharacterResultTestSpec(OutputTestSpec execSpec, boolean keepByteContent) {

        public ToCharacterResultTestSpec {
            Objects.requireNonNull(execSpec);
        }

        @Override
        public String toString() {
            final List<String> tokens = new ArrayList<>();

            tokens.add(execSpec.toString());
            if (keepByteContent) {
                tokens.add("keepByteContent");
            }

            return String.join(", ", tokens.toArray(String[]::new));
        }

        void test() throws IOException, InterruptedException {
            var coc = execSpec.cocSpec().create();

            var command = execSpec.commandSpec().command().asToolProvider();

            var expected = coc.binaryOutput(false).createExecutable(command).execute();

            var byteResult = coc.binaryOutput(true).createExecutable(command).execute();

            var actual = byteResult.toCharacterResult(coc.charset(), keepByteContent);

            CommandOutputControl.Result expectedByteContent;
            if (keepByteContent) {
                expectedByteContent = byteResult;
            } else {
                expectedByteContent = expected;
            }

            assertArrayEquals(expectedByteContent.findByteContent().orElse(null), actual.findByteContent().orElse(null));
            assertArrayEquals(expectedByteContent.findByteStdout().orElse(null), actual.findByteStdout().orElse(null));
            assertArrayEquals(expectedByteContent.findByteStderr().orElse(null), actual.findByteStderr().orElse(null));

            assertEquals(expected.findContent(), actual.findContent());
            assertEquals(expected.findStdout(), actual.findStdout());
            assertEquals(expected.findStderr(), actual.findStderr());

            assertSame(byteResult.execSpec(), actual.execSpec());
            assertEquals(expected.exitCode(), actual.exitCode());
        }
    }

    private static Stream<ToCharacterResultTestSpec> test_Result_toCharacterResult() {
        List<OutputTestSpec> testCases = new ArrayList<>();

        var skip = Set.of(OutputControl.BINARY_OUTPUT, OutputControl.DUMP, OutputControl.SAVE_FIRST_LINE);

        for (var outputControl : OutputControl.variants().stream().filter(spec -> {
            return !skip.stream().anyMatch(spec::contains);
        }).toList()) {
            for (var stdoutContent : List.of(OutputData.EMPTY, OutputData.MANY)) {
                for (var stderrContent : List.of(OutputData.EMPTY, OutputData.MANY)) {
                    var commandSpec = new CommandSpec(stdoutContent, stderrContent);
                    testCases.add(new OutputTestSpec(false, new CommandOutputControlSpec(outputControl), commandSpec));
                }
            }
        }

        return testCases.stream().flatMap(execSpec -> {
            return Stream.of(true, false).map(keepByteContent -> {
                return new ToCharacterResultTestSpec(execSpec, keepByteContent);
            });
        });
    }

    private static boolean cherryPickSavedOutputTestCases() {
        return !testSomeSavedOutput().isEmpty();
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

                        if (outputControl.contains(OutputControl.BINARY_OUTPUT)
                                && (stdoutContent == OutputData.ONE_LINE || stderrContent == OutputData.ONE_LINE)) {
                            // Skip a test case if it runs a command writing
                            // a single line in stdout or stderr, and handles command output as a byte stream.
                            // It duplicates test cases that write multiple lines in stdout or stderr.
                            continue;
                        }

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
                                throw ExceptionBox.reachedUnreachable();
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

    private static List<OutputTestSpec> testDumpStreams() {
        List<OutputTestSpec> testCases = new ArrayList<>();
        final var commandSpec = new CommandSpec(OutputData.MANY, OutputData.MANY);
        final var outputControl = new ArrayList<OutputControl>();
        outputControl.add(OutputControl.DUMP);
        for (var discardStdout : BOOLEAN_VALUES) {
            if (discardStdout) {
                outputControl.add(OutputControl.DISCARD_STDOUT);
            }
            for (var discardStderr : BOOLEAN_VALUES) {
                if (discardStderr) {
                    outputControl.add(OutputControl.DISCARD_STDERR);
                }
                for (var redirectStderr : BOOLEAN_VALUES) {
                    if (redirectStderr) {
                        outputControl.add(OutputControl.REDIRECT_STDERR);
                    }
                    for (var binaryOutput : BOOLEAN_VALUES) {
                        if (binaryOutput) {
                            outputControl.add(OutputControl.BINARY_OUTPUT);
                        }
                        for (var dumpStdout : BOOLEAN_VALUES) {
                            if (dumpStdout) {
                                outputControl.add(OutputControl.DUMP_STDOUT_IN_SYSTEM_OUT);
                            }
                            for (var dumpStderr : BOOLEAN_VALUES) {
                                if (!dumpStderr && !dumpStdout) {
                                    continue;
                                }
                                if (dumpStderr) {
                                    outputControl.add(OutputControl.DUMP_STDERR_IN_SYSTEM_ERR);
                                }
                                testCases.add(new OutputTestSpec(
                                        false,
                                        new CommandOutputControlSpec(Set.copyOf(outputControl)),
                                        commandSpec));
                                if (dumpStderr) {
                                    outputControl.removeLast();
                                }
                            }
                            if (dumpStdout) {
                                outputControl.removeLast();
                            }
                        }
                        if (binaryOutput) {
                            outputControl.removeLast();
                        }
                    }
                    if (redirectStderr) {
                        outputControl.removeLast();
                    }
                }
                if (discardStderr) {
                    outputControl.removeLast();
                }
            }
            if (discardStdout) {
                outputControl.removeLast();
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

    private sealed interface CommandAction {
        static SleepCommandAction sleep(int seconds) {
            return new SleepCommandAction(seconds);
        }

        static EchoCommandAction echoStdout(String str) {
            return new EchoCommandAction(str, false);
        }

        static EchoCommandAction echoStderr(String str) {
            return new EchoCommandAction(str, true);
        }
    }

    private record EchoCommandAction(String value, boolean stderr) implements CommandAction {
        EchoCommandAction {
            Objects.requireNonNull(value);
        }
    }

    private record SleepCommandAction(int seconds) implements CommandAction {
        SleepCommandAction {
            if (seconds < 0) {
                throw new IllegalArgumentException();
            }
        }
    }

    private record Command(List<String> stdout, List<String> stderr) {
        Command {
            stdout.forEach(Objects::requireNonNull);
            stderr.forEach(Objects::requireNonNull);
        }

        List<String> asExecutable() {
            return createShellCommandLine(actions());
        }

        ToolProvider asToolProvider() {
            return createToolProvider(actions());
        }

        private Stream<CommandAction> actions() {
            return Stream.concat(
                    stdout.stream().map(CommandAction::echoStdout),
                    stderr.stream().map(CommandAction::echoStderr)
            );
        }

        static List<String> createShellCommandLine(Stream<CommandAction> actions) {
            final List<String> commandline = new ArrayList<>();
            if (OperatingSystem.isWindows()) {
                commandline.addAll(List.of("cmd", "/C"));
            } else {
                commandline.addAll(List.of("sh", "-c"));
            }
            commandline.add(actions.map(Command::toString).collect(joining(" && ")));
            return commandline;
        }

        static List<String> createShellCommandLine(List<CommandAction> actions) {
            return createShellCommandLine(actions.stream());
        }

        static ToolProvider createToolProvider(Stream<CommandAction> actions) {
            var copiedActions = actions.toList();
            return new ToolProvider() {

                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    for (var action : copiedActions) {
                        switch (action) {
                            case EchoCommandAction echo -> {
                                if (echo.stderr()) {
                                    err.println(echo.value());
                                } else {
                                    out.println(echo.value());
                                }
                            }
                            case SleepCommandAction sleep -> {
                                toRunnable(() -> {
                                    synchronized (this) {
                                        var millis = Duration.ofSeconds(sleep.seconds()).toMillis();
                                        this.wait(millis);
                                    }
                                }).run();
                            }
                        }
                    }
                    return 0;
                }

                @Override
                public String name() {
                    return "test";
                }
            };
        }

        static ToolProvider createToolProvider(List<CommandAction> actions) {
            return createToolProvider(actions.stream());
        }

        private static String toString(CommandAction action) {
            switch (action) {
                case EchoCommandAction echo -> {
                    String str;
                    if (OperatingSystem.isWindows()) {
                        str = "(echo " + echo.value() + ")";
                    } else {
                        str = "echo " + echo.value();
                    }
                    if (echo.stderr()) {
                        str += ">&2";
                    }
                    return str;
                }
                case SleepCommandAction sleep -> {
                    if (OperatingSystem.isWindows()) {
                        // The standard way to sleep on Windows is to use the "ping" command.
                        // It sends packets every second.
                        // To wait N seconds, it should send N+1 packets.
                        // The "timeout" command works only in a console.
                        return String.format("(ping -n %d localhost > nul)", sleep.seconds() + 1);
                    } else {
                        return "sleep " + sleep.seconds();
                    }
                }
            }
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

        @Override
        public String toString() {
            return String.format("[stdout=%s, stderr=%s]", stdout, stderr);
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
        DUMP(CommandOutputControl::dumpOutput, CommandOutputControl::isDumpOutput),
        SAVE_ALL(CommandOutputControl::saveOutput, CommandOutputControl::isSaveOutput),
        SAVE_FIRST_LINE(CommandOutputControl::saveFirstLineOfOutput, CommandOutputControl::isSaveFirstLineOfOutput),
        SAVE_NOTHING(coc -> {
            coc.saveOutput(false);
        }, coc -> {
            return !coc.isSaveOutput() && !coc.isSaveFirstLineOfOutput();
        }),
        DISCARD_STDOUT(CommandOutputControl::discardStdout, CommandOutputControl::isDiscardStdout),
        DISCARD_STDERR(CommandOutputControl::discardStderr, CommandOutputControl::isDiscardStderr),
        REDIRECT_STDERR(CommandOutputControl::redirectStderr, CommandOutputControl::isRedirectStderr),
        STORE_STREAMS_IN_FILES(CommandOutputControl::storeOutputInFiles, CommandOutputControl::isStoreOutputInFiles),
        BINARY_OUTPUT(CommandOutputControl::binaryOutput, CommandOutputControl::isBinaryOutput),
        DUMP_STDOUT_IN_SYSTEM_OUT(coc -> {
            coc.dumpStdout(new PrintStreamWrapper(System.out));
        },  coc -> {
            return coc.dumpStdout() instanceof PrintStreamWrapper;
        }),
        DUMP_STDERR_IN_SYSTEM_ERR(coc -> {
            coc.dumpStderr(new PrintStreamWrapper(System.err));
        },  coc -> {
            return coc.dumpStderr() instanceof PrintStreamWrapper;
        }),
        ;

        OutputControl(Consumer<CommandOutputControl> setter, Function<CommandOutputControl, Boolean> getter) {
            this.setter = Objects.requireNonNull(setter);
            this.unsetter = null;
            this.getter = Objects.requireNonNull(getter);
        }

        OutputControl(BiConsumer<CommandOutputControl, Boolean> setter, Function<CommandOutputControl, Boolean> getter) {
            Objects.requireNonNull(setter);
            this.setter = coc -> {
                setter.accept(coc, true);
            };
            this.unsetter = coc -> {
                setter.accept(coc, false);
            };
            this.getter = Objects.requireNonNull(getter);
        }

        CommandOutputControl set(CommandOutputControl coc) {
            setter.accept(coc);
            return coc;
        }

        CommandOutputControl unset(CommandOutputControl coc) {
            Objects.requireNonNull(unsetter).accept(coc);
            return coc;
        }

        boolean canUnset() {
            return unsetter != null;
        }

        boolean get(CommandOutputControl coc) {
            return getter.apply(coc);
        }

        static List<Set<OutputControl>> variants() {
            final List<Set<OutputControl>> variants = new ArrayList<>();
            for (final var binaryOutput : BOOLEAN_VALUES) {
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
                        }).map(v -> {
                            if (binaryOutput) {
                                return new SetBuilder<OutputControl>().add(v).add(BINARY_OUTPUT).create();
                            } else {
                                return v;
                            }
                        }).toList());
                    }
                }
            }
            return variants.stream().distinct().toList();
        }

        private static final class PrintStreamWrapper extends PrintStream {
            PrintStreamWrapper(PrintStream out) {
                super(out, true);
            }
        }

        private final Consumer<CommandOutputControl> setter;
        private final Consumer<CommandOutputControl> unsetter;
        private final Function<CommandOutputControl, Boolean> getter;

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
            outputControl.forEach(control -> control.set(coc));
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
            if (contains(OutputControl.BINARY_OUTPUT)) {
                verifyByteResultContent(result.get(), command, StandardCharsets.UTF_8);
            } else {
                verifyResultContent(result.get(), command);
            }
        }

        private boolean contains(OutputControl v) {
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

        private boolean stdoutInherited() {
            if (toolProvider || saveOutput() || replaceStdoutWithStderr()) {
                return false;
            }
            return dumpOutput() && !discardStdout() && !contains(OutputControl.DUMP_STDOUT_IN_SYSTEM_OUT);
        }

        private boolean stderrInherited() {
            if (toolProvider || saveOutput() || redirectStderr()) {
                return false;
            }
            return dumpOutput() && !discardStderr() && !contains(OutputControl.DUMP_STDERR_IN_SYSTEM_ERR);
        }

        private void verifyDump(DumpCapture dumpCapture, Command command) {
            if (!dumpOutput()) {
                assertEquals(List.of(), dumpCapture.outLines());
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            if (replaceStdoutWithStderr()) {
                // STDERR replaces STDOUT
                assertEquals(command.stderr(), dumpCapture.outLines());
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            verifyDumpedStdout(dumpCapture, command);
            verifyDumpedStderr(dumpCapture, command);
        }

        private void verifyDumpedStdout(DumpCapture dumpCapture, Command command) {
            if (stdoutInherited()) {
                // A subprocess wrote its STDOUT into a file descriptor associated
                // with the Java process's STDOUT, not into System.out. Can't capture it.
                assertEquals(List.of(), dumpCapture.outLines());
                return;
            }

            if (redirectStderr() && !discardStderr()) {
                // Interleaved STDOUT and STDERR
                if (!isInterleave(dumpCapture.outLines(), command.stdout(), command.stderr())) {
                    fail(String.format("Unexpected combined output=%s; stdout=%s; stderr=%s",
                            dumpCapture.outLines(), command.stdout(), command.stderr()));
                }
            } else if (discardStdout()) {
                assertEquals(List.of(), dumpCapture.outLines());
            } else {
                assertEquals(command.stdout(), dumpCapture.outLines());
            }
        }

        private void verifyDumpedStderr(DumpCapture dumpCapture, Command command) {
            if (stderrInherited()) {
                // A subprocess wrote its STDERR into a file descriptor associated
                // with the Java process's STDERR, not into System.err. Can't capture it.
                assertEquals(List.of(), dumpCapture.errLines());
                return;
            }

            if (redirectStderr() || discardStderr()) {
                assertEquals(List.of(), dumpCapture.errLines());
            } else {
                assertEquals(command.stderr(), dumpCapture.errLines());
            }
        }

        private void verifyResultContent(CommandOutputControl.Result result, Command command) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(command);

            assertTrue(result.findByteContent().isEmpty());
            assertTrue(result.findByteStdout().isEmpty());
            assertTrue(result.findByteStderr().isEmpty());

            if (!saveOutput()) {
                assertTrue(result.findContent().isEmpty());
                assertTrue(result.findStdout().isEmpty());
                assertTrue(result.findStderr().isEmpty());
                return;
            }

            assertTrue(result.findContent().isPresent());

            command = filterSavedStreams(command);

            var content = result.content();

            if (contains(OutputControl.SAVE_FIRST_LINE)) {
                assertTrue(content.size() <= 2, String.format("The number of saved lines must be less than or equal to two. Actual: %d", result.content().size()));
            }

            if (!redirectStderr()) {
                var stdout = result.stdout();
                var stderr = result.stderr();

                assertEquals(command.stdout(), stdout);
                assertEquals(command.stderr(), stderr);
                assertEquals(Stream.of(
                        stdout,
                        stderr
                ).flatMap(List::stream).toList(), content);
            } else {
                assertEquals(discardStderr(), result.findStdout().isPresent());
                assertTrue(result.findStderr().isEmpty());
                if (contains(OutputControl.SAVE_FIRST_LINE)) {
                    assertTrue(List.of(command.stdout(), command.stderr()).contains(result.content()),
                            String.format("Saved content %s is either %s or %s",
                                    content, command.stdout(), command.stderr()));
                } else if (contains(OutputControl.SAVE_ALL)) {
                    if (!isInterleave(content, command.stdout(), command.stderr())) {
                        fail(String.format("Unexpected combined saved content=%s; stdout=%s; stderr=%s",
                                content, command.stdout(), command.stderr()));
                    }
                } else {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
                }
            }
        }

        private void verifyByteResultContent(CommandOutputControl.Result result, Command command, Charset charset) {
            Objects.requireNonNull(result);
            Objects.requireNonNull(command);
            Objects.requireNonNull(charset);

            assertTrue(result.findContent().isEmpty());
            assertTrue(result.findStdout().isEmpty());
            assertTrue(result.findStderr().isEmpty());

            if (!saveOutput()) {
                assertTrue(result.findByteContent().isEmpty());
                assertTrue(result.findByteStdout().isEmpty());
                assertTrue(result.findByteStderr().isEmpty());
                return;
            }

            assertTrue(result.findByteContent().isPresent());

            command = filterSavedStreams(command);

            if (!redirectStderr()) {
                assertEquals(command.stdout(), toStringList(result.byteStdout(), charset));
                assertEquals(command.stderr(), toStringList(result.byteStderr(), charset));
                assertEquals(Stream.of(
                        command.stdout(),
                        command.stderr()
                ).flatMap(List::stream).toList(), toStringList(result.byteContent(), charset));
            } else {
                assertEquals(discardStderr(), result.findByteStdout().isPresent());
                assertTrue(result.findByteStderr().isEmpty());

                var combined = toStringList(result.byteContent(), charset);
                if (!isInterleave(combined, command.stdout(), command.stderr())) {
                    fail(String.format("Unexpected combined saved content=%s; stdout=%s; stderr=%s",
                            combined, command.stdout(), command.stderr()));
                }
            }
        }

        private List<String> expectedSavedStream(List<String> commandOutput) {
            Objects.requireNonNull(commandOutput);
            if (contains(OutputControl.SAVE_ALL) || (contains(OutputControl.SAVE_FIRST_LINE) && contains(OutputControl.BINARY_OUTPUT))) {
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
                return toStringList(out, outCharset);
            }

            List<String> errLines() {
                return toStringList(err, errCharset);
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

    private final static class InterruptibleToolProvider implements ToolProvider {

        InterruptibleToolProvider(ToolProvider impl) {
            this.impl = impl;
        }

        @Override
        public String name() {
            return impl.name();
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            boolean interruptedValue = false;
            try {
                return impl.run(out, err, args);
            } catch (ExceptionBox ex) {
                if (ex.getCause() instanceof InterruptedException) {
                    interruptedValue = true;
                    return 1;
                } else {
                    throw ex;
                }
            } finally {
                interrupted.complete(interruptedValue);
            }
        }

        boolean interrupted() {
            return interrupted.join();
        }

        private final ToolProvider impl;
        private final CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
    }

    private static List<String> toStringList(byte[] data, Charset charset) {
        try (var bufReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), charset))) {
            return bufReader.lines().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final List<Boolean> BOOLEAN_VALUES = List.of(Boolean.TRUE, Boolean.FALSE);
}
