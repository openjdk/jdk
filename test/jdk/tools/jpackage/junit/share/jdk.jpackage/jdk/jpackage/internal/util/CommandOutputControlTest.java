/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
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
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.function.ExceptionBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
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

    /**
     * Runs cherry-picked {@link OutputTestSpec} test cases.
     * <p>
     * This test method is mutual exclusive with
     * {@link #testSavedOutput(OutputTestSpec)} and is aimed for debugging
     * {@code OutputTestSpec} test cases.
     * <p>
     * It is disabled by default. To enable it, manually edit {@link #testSomeSavedOutput()}.
     *
     * @see #testSomeSavedOutput()
     *
     * @param spec the test case
     */
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
    public void testCharset(CharsetTestSpec spec) throws IOException, InterruptedException {
        spec.test();
    }

    @ParameterizedTest
    @MethodSource
    public void test_description(CommandOutputControlSpec spec) {
        // This test is mostly for coverage.
        var desc = spec.create().description();
        assertFalse(desc.isBlank());
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
    public void test_ExecutableAttributes(boolean toolProvider) {
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

        assertEquals("runme --foo --baz=10", exec.attributes().printableCommandLine());
    }

    @Test
    public void test_Result_single_arg_ctor() {
        var result = new CommandOutputControl.Result(7);
        assertFalse(result.findContent().isPresent());
        assertFalse(result.findStdout().isPresent());
        assertFalse(result.findStderr().isPresent());
        assertEquals(7, result.getExitCode());
        assertSame(Objects.requireNonNull(CommandOutputControl.EMPTY_EXECUTABLE_ATTRIBUTES), result.execAttrs());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_Result_unexpected(boolean customMessage) throws IOException {
        var result = new CommandOutputControl.Result(7);

        if (customMessage) {
            assertEquals("Kaput!", result.unexpected("Kaput!").getMessage());
        } else {
            assertEquals("Unexpected result from executing the command <unknown>", result.unexpected().getMessage());
        }
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
    public void test_Result_toCharacterResult_copyWithExecutableAttributes() {

        var empty = new CommandOutputControl.Result(0);

        var execAttrs = new CommandOutputControl.ExecutableAttributes() {
            @Override
            public List<String> commandLine() {
                return List.of("foo");
            }
        };

        var copy = empty.copyWithExecutableAttributes(execAttrs);

        assertSame(empty.exitCode(), copy.exitCode());
        assertSame(empty.output(), copy.output());
        assertSame(empty.byteOutput(), copy.byteOutput());
        assertSame(execAttrs, copy.execAttrs());
    }

    @Test
    public void test_UnexpectedExitCodeException_no_exit_code() {

        var resultWithoutExitCode = CommandOutputControl.Result.build().create();

        assertThrowsExactly(IllegalArgumentException.class, () -> {
            new CommandOutputControl.UnexpectedExitCodeException(resultWithoutExitCode);
        });

        assertThrowsExactly(IllegalArgumentException.class, () -> {
            new CommandOutputControl.UnexpectedExitCodeException(resultWithoutExitCode, "Kaput!");
        });
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

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true).processListener(process -> {
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

    @DisabledOnOs(value = OS.MAC, disabledReason = "Closing a stream doesn't consistently cause a trouble as it should")
    @ParameterizedTest
    @EnumSource(OutputStreams.class)
    public void test_close_streams(OutputStreams action) throws InterruptedException, IOException {
        var cmdline = Command.createShellCommandLine(List.<CommandAction>of(
                CommandAction.echoStdout("Hello stdout"),
                CommandAction.echoStderr("Bye stderr")
        ));

        var coc = new CommandOutputControl().saveOutput(true).dumpOutput(true).processListener(toConsumer(process -> {
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

    @ParameterizedTest
    @ValueSource(booleans = {true})
    public void stressTest(boolean binaryOutput, @TempDir Path workDir) throws Exception {

        // Execute multiple subprocesses asynchronously.
        // Each subprocess writes a few chunks of data each larger than the default buffer size (8192 bytes)

        final var chunkCount = 5;
        final var subprocessCount = 100;
        final var subprocessExecutor = Executors.newVirtualThreadPerTaskExecutor();

        final var md = MessageDigest.getInstance("MD5");

        var cmdline = Command.createShellCommandLine(IntStream.range(0, chunkCount).mapToObj(chunk -> {
            byte[] bytes = new byte[10 * 1024]; // 10K to exceed the default BufferedOutputStream's buffer size of 8192.
            new Random().nextBytes(bytes);
            md.update(bytes);
            var path = workDir.resolve(Integer.toString(chunk));
            try {
                Files.write(path, bytes);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return path;
        }).map(CommandAction::cat).toList());

        final var digest = HexFormat.of().formatHex(md.digest());

        // Schedule to start every subprocess in a separate virtual thread.
        // Start and suspend threads, waiting until all scheduled threads have started.
        // After all scheduled threads start, resume them.
        // This should result in starting all scheduled subprocesses simultaneously.

        var readyLatch = new CountDownLatch(subprocessCount);
        var startLatch = new CountDownLatch(1);

        var futures = IntStream.range(0, subprocessCount).mapToObj(_ -> {
            return CompletableFuture.supplyAsync(toSupplier(() -> {

                var exec = new CommandOutputControl()
                        .saveOutput(true)
                        .binaryOutput(binaryOutput)
                        .createExecutable(new ProcessBuilder(cmdline));

                readyLatch.countDown();
                startLatch.await();

                var result = exec.execute();

                var localMd = MessageDigest.getInstance("MD5");
                localMd.update(result.byteContent());

                return HexFormat.of().formatHex(localMd.digest());

            }), subprocessExecutor);
        }).toList();

        readyLatch.await();
        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        futures.forEach(future -> {
            var actualDigest = future.join();
            assertEquals(digest, actualDigest);
        });
    }

    public enum OutputStreams {
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

            assertSame(byteResult.execAttrs(), actual.execAttrs());
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

    /**
     * Returns test cases for {@link #testSomeSavedOutput(OutputTestSpec)}.
     * <p>
     * Aimed to simplify debugging of {@link #OutputTestSpec} test cases.
     * <p>
     * The total number of {@code #OutputTestSpec} test cases is ~1500. When some
     * fail and need debugging, it is a waste of time to run them all. This method
     * allows running only selected test cases. It works this way:
     * <ul>
     * <li>Run CommandOutputControlTest test.
     * <li>If some {@linke #testSavedOutput(OutputTestSpec)} invocations fail,
     * capture their IDs (test case ID is an index starting from 1).
     * <li>Replace "/* 10, 67, 456 *&#47;" comment in the body of this method with
     * the captured test case IDs.
     * <li>Rerun CommandOutputControlTest test. This time, it will run
     * {@link #testSomeSavedOutput(OutputTestSpec)} method instead of
     * {@link #testSavedOutput(OutputTestSpec)} with the list of the captured test
     * case IDs.
     * </ul>
     */
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
        for (var discardStdout : withAndWithout(OutputControl.DISCARD_STDOUT)) {
            for (var discardStderr : withAndWithout(OutputControl.DISCARD_STDERR)) {
                for (var redirectStderr : withAndWithout(OutputControl.REDIRECT_STDERR)) {
                    for (var binaryOutput : withAndWithout(OutputControl.BINARY_OUTPUT)) {
                        for (var dumpStdout : withAndWithout(OutputControl.DUMP_STDOUT_IN_SYSTEM_OUT)) {
                            for (var dumpStderr : withAndWithout(OutputControl.DUMP_STDERR_IN_SYSTEM_ERR)) {

                                if (dumpStderr.isEmpty() && dumpStdout.isEmpty()) {
                                    // Output dumping disabled
                                    continue;
                                }

                                if (discardStderr.isPresent() && discardStdout.isPresent()) {
                                    // Output dumping enabled, but all stream discarded
                                    continue;
                                }

                                if (dumpStderr.isPresent() == discardStderr.isPresent() && dumpStdout.isEmpty()) {
                                    // Stderr dumping enabled but discarded, stdout dumping disabled
                                    continue;
                                }

                                if (dumpStdout.isPresent() == discardStdout.isPresent() && dumpStderr.isEmpty()) {
                                    // Stdout dumping enabled but discarded, stderr dumping disabled
                                    continue;
                                }

                                final var outputControl = new HashSet<OutputControl>();
                                outputControl.add(OutputControl.DUMP);
                                discardStdout.ifPresent(outputControl::add);
                                discardStderr.ifPresent(outputControl::add);
                                redirectStderr.ifPresent(outputControl::add);
                                binaryOutput.ifPresent(outputControl::add);
                                dumpStdout.ifPresent(outputControl::add);
                                dumpStderr.ifPresent(outputControl::add);

                                testCases.add(new OutputTestSpec(
                                        false,
                                        new CommandOutputControlSpec(outputControl),
                                        commandSpec));
                            }
                        }
                    }
                }
            }
        }
        return testCases;
    }

    private static List<CharsetTestSpec> testCharset() {
        List<CharsetTestSpec> testCases = new ArrayList<>();

        for (boolean toolProvider : BOOLEAN_VALUES) {
            for (var redirectStderr : withAndWithout(OutputControl.REDIRECT_STDERR)) {
                for (var charset : withAndWithout(OutputControl.CHARSET_UTF16LE)) {
                    var stdoutSink = new CharsetTestSpec.DumpOutputSink(StandardCharsets.US_ASCII, OutputStreams.STDOUT);
                    var stderrSink = new CharsetTestSpec.DumpOutputSink(StandardCharsets.UTF_32LE, OutputStreams.STDERR);
                    var outputControl = new HashSet<CommandOutputControlMutator>();
                    redirectStderr.ifPresent(outputControl::add);
                    charset.ifPresent(outputControl::add);
                    outputControl.add(stdoutSink);
                    outputControl.add(stderrSink);
                    testCases.add(new CharsetTestSpec(toolProvider, new CommandOutputControlSpec(outputControl)));
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

        static WriteCommandAction writeStdout(byte[] binary) {
            return new WriteCommandAction(binary, false);
        }

        static WriteCommandAction writeStderr(byte[] binary) {
            return new WriteCommandAction(binary, true);
        }

        static CatCommandAction cat(Path file) {
            return new CatCommandAction(file);
        }
    }

    private record EchoCommandAction(String value, boolean stderr) implements CommandAction {
        EchoCommandAction {
            Objects.requireNonNull(value);
        }
    }

    private record WriteCommandAction(byte[] value, boolean stderr) implements CommandAction {
        WriteCommandAction {
            Objects.requireNonNull(value);
        }
    }

    private record CatCommandAction(Path file) implements CommandAction {
        CatCommandAction {
            Objects.requireNonNull(file);
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

        //
        // Type of shell for which to create a command line.
        // On Unix it is always the "sh".
        // On Windows, it is "cmd" by default and "powershell" when a command needs to write binary data to output stream(s).
        // Extra complexity on Windows is because "powershell" is times slower than "cmd",
        // and the latter doesn't support binary output.
        //
        private enum ShellType {
            SH(OperatingSystem.LINUX, OperatingSystem.MACOS),
            CMD(OperatingSystem.WINDOWS),
            POWERSHELL(OperatingSystem.WINDOWS),
            ;

            ShellType(OperatingSystem... os) {
                if (os.length == 0) {
                    throw new IllegalArgumentException();
                }
                this.os = Set.of(os);
            }

            boolean isSupportedOnCurrentOS() {
                return os.contains(OperatingSystem.current());
            }

            private final Set<OperatingSystem> os;
        }

        private List<CommandAction> actions() {
            return Stream.<CommandAction>concat(
                    stdout.stream().map(CommandAction::echoStdout),
                    stderr.stream().map(CommandAction::echoStderr)
            ).toList();
        }

        static List<String> createShellCommandLine(List<? extends CommandAction> actions) {
            final var shellType = detectShellType(actions);
            final List<String> commandline = new ArrayList<>();
            final String commandSeparator;
            switch (shellType) {
                case SH -> {
                    commandline.addAll(List.of("sh", "-c"));
                    commandSeparator = " && ";
                }
                case CMD -> {
                    commandline.addAll(List.of("cmd", "/C"));
                    commandSeparator = " && ";
                }
                case POWERSHELL -> {
                    commandline.addAll(List.of("powershell", "-NoProfile", "-Command"));
                    commandSeparator = "; ";
                }
                default -> {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
                }
            }
            commandline.add(actions.stream().map(action -> {
                return Command.toString(action, shellType);
            }).collect(joining(commandSeparator)));
            return commandline;
        }

        static ToolProvider createToolProvider(List<? extends CommandAction> actions) {
            var copiedActions = List.copyOf(actions);
            return new ToolProvider() {

                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int run(PrintStream out, PrintStream err, String... args) {
                    for (var action : copiedActions) {
                        switch (action) {
                            case EchoCommandAction echo -> {
                                if (echo.stderr()) {
                                    err.println(echo.value());
                                } else {
                                    out.println(echo.value());
                                }
                            }
                            case WriteCommandAction write -> {
                                try {
                                    if (write.stderr()) {
                                        err.write(write.value());
                                    } else {
                                        out.write(write.value());
                                    }
                                } catch (IOException ex) {
                                    throw new UncheckedIOException(ex);
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
                            case CatCommandAction _ -> {
                                // Not used, no point to implement.
                                throw new UnsupportedOperationException();
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

        private static ShellType detectShellType(List<? extends CommandAction> actions) {
            var supportedShellTypes = Stream.of(ShellType.values())
                    .filter(ShellType::isSupportedOnCurrentOS)
                    .collect(Collectors.toCollection(HashSet::new));
            for (var action : actions) {
                if (action instanceof WriteCommandAction) {
                    supportedShellTypes.remove(ShellType.CMD);
                }
            }
            return supportedShellTypes.stream()
                    .sorted(Comparator.comparingInt(Enum::ordinal))
                    .findFirst().orElseThrow();
        }

        private static String toString(CommandAction action, ShellType shellType) {
            switch (action) {
                case EchoCommandAction a -> {
                    return toString(a, shellType);
                }
                case WriteCommandAction a -> {
                    return toString(a, shellType);
                }
                case SleepCommandAction a -> {
                    return toString(a, shellType);
                }
                case CatCommandAction a -> {
                    return toString(a, shellType);
                }
            }
        }

        private static String toString(EchoCommandAction echo, ShellType shellType) {
            String str;
            switch (shellType) {
                case SH -> {
                    str = "echo " + echo.value();
                    if (echo.stderr()) {
                        str += ">&2";
                    }
                }
                case CMD -> {
                    str = "(echo " + echo.value() + ")";
                    if (echo.stderr()) {
                        str += ">&2";
                    }
                }
                case POWERSHELL -> {
                    str = String.format("[Console]::%s.WriteLine(\\\"%s\\\")",
                            echo.stderr() ? "Error" : "Out", echo.value());
                }
                default -> {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
                }
            }
            return str;
        }

        private static String toString(WriteCommandAction write, ShellType shellType) {
            String str;
            switch (shellType) {
                case SH -> {
                    // Convert byte[] to octal string to make it work with POSIX printf.
                    // POSIX printf doesn't recognize hex strings, so can't use handy HexFormat.
                    var sb = new StringBuilder();
                    sb.append("printf ");
                    for (var b : write.value()) {
                        sb.append("\\\\").append(Integer.toOctalString(b & 0xFF));
                    }
                    if (write.stderr()) {
                        sb.append(">&2");
                    }
                    str = sb.toString();
                }
                case CMD -> {
                    throw new UnsupportedOperationException("Can't output binary data with 'cmd'");
                }
                case POWERSHELL -> {
                    var base64 = Base64.getEncoder().encodeToString(write.value());
                    str = String.format(
                            "$base64 = '%s'; " +
                            "$bytes = [Convert]::FromBase64String($base64); " +
                            "[Console]::%s().Write($bytes, 0, $bytes.Length)",
                            base64, write.stderr() ? "OpenStandardError" : "OpenStandardOutput");
                }
                default -> {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
                }
            }
            return str;
        }

        private static String toString(SleepCommandAction sleep, ShellType shellType) {
            switch (shellType) {
                case SH -> {
                    return "sleep " + sleep.seconds();
                }
                case CMD -> {
                    // The standard way to sleep in "cmd" is to use the "ping" command.
                    // It sends packets every second.
                    // To wait N seconds, it should send N+1 packets.
                    // The "timeout" command works only in a console.
                    return String.format("(ping -n %d localhost > nul)", sleep.seconds() + 1);
                }
                case POWERSHELL -> {
                    return "Start-Sleep -Seconds " + sleep.seconds();
                }
                default -> {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
                }
            }
        }

        private static String toString(CatCommandAction cat, ShellType shellType) {
            switch (shellType) {
                case SH -> {
                    return "cat " + cat.file();
                }
                case CMD -> {
                    return "type " + cat.file();
                }
                case POWERSHELL -> {
                    // Not used, no point to implement.
                    throw new UnsupportedOperationException();
                }
                default -> {
                    // Unreachable
                    throw ExceptionBox.reachedUnreachable();
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

    public interface CommandOutputControlMutator {
        String name();
        void mutate(CommandOutputControl coc);

        static <T extends CommandOutputControlMutator> Function<T, Set<T>> addToSet(Set<T> set) {
            return m -> {
                return new SetBuilder<T>().add(set).add(m).create();
            };
        }
    }

    public enum OutputControl implements CommandOutputControlMutator {
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
        }, coc -> {
            return coc.dumpStdout() instanceof PrintStreamWrapper;
        }),
        DUMP_STDERR_IN_SYSTEM_ERR(coc -> {
            coc.dumpStderr(new PrintStreamWrapper(System.err));
        }, coc -> {
            return coc.dumpStderr() instanceof PrintStreamWrapper;
        }),
        CHARSET_UTF16LE(coc -> {
            coc.charset(StandardCharsets.UTF_16LE);
        }, coc -> {
            return coc.charset() == StandardCharsets.UTF_16LE;
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

        @Override
        public void mutate(CommandOutputControl coc) {
            set(coc);
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
            for (final var binaryOutput : withAndWithout(BINARY_OUTPUT)) {
                for (final var redirectStderr : withAndWithout(REDIRECT_STDERR)) {
                    for (final var withDump : withAndWithout(DUMP)) {
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
                            return withDump.map(CommandOutputControlMutator.addToSet(v)).orElse(v);
                        }).map(v -> {
                            return redirectStderr.filter(_ -> {
                                return !v.containsAll(List.of(DISCARD_STDOUT, DISCARD_STDERR));
                            }).map(CommandOutputControlMutator.addToSet(v)).orElse(v);
                        }).map(v -> {
                            return binaryOutput.map(CommandOutputControlMutator.addToSet(v)).orElse(v);
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

    public record CommandOutputControlSpec(Set<? extends CommandOutputControlMutator> outputControl) {
        public CommandOutputControlSpec {
            outputControl.forEach(Objects::requireNonNull);
            if (outputControl.containsAll(OutputControl.SAVE)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return outputControl.stream().map(CommandOutputControlMutator::name).sorted().collect(joining("+"));
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
            outputControl.forEach(control -> control.mutate(coc));
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

    record CharsetTestSpec(boolean toolProvider, CommandOutputControlSpec cocSpec) {

        void test() throws IOException, InterruptedException {
            if (cocSpec.outputControl().stream().noneMatch(DumpOutputSink.class::isInstance)) {
                throw new IllegalArgumentException();
            }

            final var expectedString = "veni-vidi-vici";

            var coc = cocSpec.create().dumpOutput(true);

            CommandOutputControl.Executable exec;
            if (toolProvider) {
                var tp = Command.createToolProvider(Stream.of(expectedString).<CommandAction>mapMulti((str, sink) -> {
                    sink.accept(CommandAction.echoStdout(str));
                    sink.accept(CommandAction.echoStderr(str));
                }).toList());
                exec = coc.createExecutable(tp);
            } else {
                var cmdline = Command.createShellCommandLine(Stream.of(expectedString).map(str -> {
                    return (str + System.lineSeparator()).getBytes(coc.charset());
                }).<CommandAction>mapMulti((bytes, sink) -> {
                    sink.accept(CommandAction.writeStdout(bytes));
                    sink.accept(CommandAction.writeStderr(bytes));
                }).toList());
                exec = coc.createExecutable(new ProcessBuilder(cmdline));
            }

            exec.execute();

            for (var outputContolMutator : cocSpec.outputControl()) {
                if (outputContolMutator instanceof DumpOutputSink sink) {
                    var actual = sink.lines();
                    List<String> expected;
                    if (cocSpec.redirectStderr()) {
                        switch (sink.streams()) {
                            case STDERR -> {
                                expected = List.of();
                            }
                            default -> {
                                expected = List.of(expectedString, expectedString);
                            }
                        }
                    } else {
                        expected = List.of(expectedString);
                    }
                    assertEquals(expected, actual);
                }
            }

        }

        record DumpOutputSink(Charset charset, ByteArrayOutputStream buffer, OutputStreams streams) implements CommandOutputControlMutator {
            DumpOutputSink {
                Objects.requireNonNull(charset);
                Objects.requireNonNull(buffer);
                Objects.requireNonNull(streams);
            }

            DumpOutputSink(Charset charset, OutputStreams streams) {
                this(charset, new ByteArrayOutputStream(), streams);
            }

            List<String> lines() {
                var str = buffer.toString(charset);
                return new BufferedReader(new StringReader(str)).lines().toList();
            }

            @Override
            public String name() {
                return String.format("DUMP-%s-%s", streams, charset.name());
            }

            @Override
            public void mutate(CommandOutputControl coc) {
                var ps = new PrintStream(buffer, false, charset);
                switch (streams) {
                    case STDOUT -> {
                        coc.dumpStdout(ps);
                    }
                    case STDERR -> {
                        coc.dumpStderr(ps);
                    }
                    case STDOUT_AND_STDERR -> {
                        // Easy to implement, but not used.
                        throw new IllegalArgumentException();
                    }
                    default -> {
                        // Unreachable
                        throw ExceptionBox.reachedUnreachable();
                    }
                }
            }
        }
    }

    private static final class InterruptibleToolProvider implements ToolProvider {

        InterruptibleToolProvider(ToolProvider impl) {
            this.impl = impl;
        }

        @Override
        public String name() {
            return impl.name();
        }

        @Override
        public int run(PrintStream out, PrintStream err, String... args) {
            return run(_ -> {
                return impl.run(out, err, args);
            }, args);
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return run(_ -> {
                return impl.run(out, err, args);
            }, args);
        }

        boolean interrupted() {
            return interrupted.join();
        }

        private int run(Function<String[], Integer> workload, String... args) {
            boolean interruptedValue = false;
            try {
                return workload.apply(args);
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

    private static <T> List<Optional<T>> withAndWithout (T value) {
        return List.of(Optional.empty(), Optional.of(value));
    }

    private static final List<Boolean> BOOLEAN_VALUES = List.of(true, false);
}
