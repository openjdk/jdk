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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;

/**
 * Configures the output streams of a process or a tool provider and executes
 * it.
 * <p>
 * Can save the first line of an output stream, the entire output stream or
 * discard it.
 * <p>
 * Supports separate configurations for stdout and stderr streams.
 * <p>
 * Output can be saved as byte or character streams.
 *
 * <p>
 * The table below describes how combinations of parameters affect content added
 * to {@code System.out} and {@code System.err} streams for subsequently
 * executed {@link #execute(ProcessBuilder, long)} and
 * {@link #execute(ToolProvider, String...)} functions:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>discardStdout(false) and discardStderr(false)</th>
 * <th>discardStdout(false) and discardStderr(true)</th>
 * <th>discardStdout(true) and discardStderr(false)</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectErrorStream(true) and dumpOutput(true)</th>
 * <td>
 * <p>
 * System.out: STDOUT & STDERR interleaved
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: STDERR;
 * <p>
 * The command's STDERR will be written into the stream object referenced by the
 * {@link System#out} field and not into the underlying file descriptor
 * associated with the STDOUT of the Java process
 * <p>
 * System.err: unchanged</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(false) and dumpOutput(true)</th>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: STDERR</td>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: unchanged
 * <p>
 * System.err: STDERR</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p>
 * The table below describes how combinations of parameters affect the
 * properties of {@link Result} objects returned from subsequently executed
 * {@link #execute(ProcessBuilder, long)} and
 * {@link #execute(ToolProvider, String...)} functions:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>saveOutput(true)</th>
 * <th>saveFirstLineOfOutput()</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectErrorStream(true) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * Result.findContent(): STDOUT & STDERR interleaved
 * <p>
 * Result.findStdout(): {@code Optional.empty()}
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * Result.findContent(): an {@code Optional} object wrapping a single item list
 * with the first line of interleaved STDOUT & STDERR if a command wrote any of
 * them; otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStdout(): {@code Optional.empty()}
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(false) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * Result.findContent(): STDOUT followed by STDERR
 * <p>
 * Result.findStdout(): STDOUT
 * <p>
 * Result.findStderr(): STDERR</td>
 * <td>
 * <p>
 * Result.findContent(): The first line of STDOUT if a command wrote it,
 * followed by the first line of STDERR if the command wrote it; an
 * {@code Optional} object wraps a list with at most two items
 * <p>
 * Result.findStdout(): The first line of STDOUT if a command wrote it;
 * otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStderr(): The first line of STDERR if a command wrote it;
 * otherwise {@code Optional.of(List.of())}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(true) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * Result.findContent(): STDOUT
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * Result.findContent(): The first line of STDOUT if the command wrote it;
 * otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(false) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * Result.findContent(): STDOUT
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * Result.findContent(): The first line of STDOUT if the command wrote it;
 * otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(true) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * Result.findContent(): STDERR
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * Result.findContent(): The first line of STDERR if the command wrote it;
 * otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStdout(): The same as Result.findContent()
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectErrorStream(false) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * Result.findContent(): STDERR
 * <p>
 * Result.findStdout(): {@code Optional.empty()}
 * <p>
 * Result.findStderr(): The same as Result.findContent()</td>
 * <td>
 * <p>
 * Result.findContent(): The first line of STDERR if the command wrote it;
 * otherwise {@code Optional.of(List.of())}
 * <p>
 * Result.findStdout(): {@code Optional.empty()}
 * <p>
 * Result.findStderr(): The same as Result.findContent()</td>
 * </tr>
 * </tbody>
 * </table>
 */
public final class CommandOutputControl {

    public CommandOutputControl() {
        outputStreamsControl = new OutputStreamsControl();
    }

    private CommandOutputControl(CommandOutputControl other) {
        outputStreamsControl = other.outputStreamsControl.copy();
        processOutputCharset = other.processOutputCharset;
        redirectErrorStream = other.redirectErrorStream;
        storeStreamsInFiles = other.storeStreamsInFiles;
        binaryOutput = other.binaryOutput;
        processNotifier = other.processNotifier;
    }

    /**
     * Configures if the entire stdout and stderr streams from commands subsequently
     * executed by this object should be saved.
     * <p>
     * If {@code v} is {@code true}, the entire stdout and stderr streams will be
     * saved; otherwise, they will be discarded.
     * <p>
     * This function is mutually exclusive with {@link #saveFirstLineOfOutput()}.
     *
     * @param v if both stdout and stderr streams should be saved
     *
     * @return this
     */
    public CommandOutputControl saveOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.SAVE_ALL);
    }

    /**
     * Configures if the first line of the output, combined from stdout and stderr
     * streams from commands subsequently executed by this object, should be saved.
     * <p>
     * This function is mutually exclusive with {@link #saveOutput(boolean)}.
     *
     * @return this
     */
    public CommandOutputControl saveFirstLineOfOutput() {
        return setOutputControl(true, OutputControlOption.SAVE_FIRST_LINE);
    }

    /**
     * Configures this object to dump stdout and stderr streams from subsequently
     * executed commands into {@code System.out} and {@code System.err}
     * respectively.
     * <p>
     * If this object is configured to redirect stderr of subsequently executed
     * commands into their stdout ({@code redirectErrorStream(true)}), they will be
     * dumped into {@code System.out}; {@code System.err} will remain unchanged.
     * Otherwise, their stdout and stderr streams will be dumped into
     * {@code System.out} and {@code System.err} respectively.
     *
     * @param v if output streams from subsequently executed commands should be
     *          dumped into {@code System.out} and {@code System.err} streams
     *
     * @return this
     *
     * @see #redirectErrorStream(boolean)
     */
    public CommandOutputControl dumpOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.DUMP);
    }

    /**
     * Configures this object to treat stdout and stderr streams from subsequently
     * executed commands as byte streams rather than character streams.
     *
     * @param v if output streams from subsequently executed commands should be
     *          treated as byte streams rather than character streams
     *
     * @return this
     */
    public CommandOutputControl binaryOutput(boolean v) {
        binaryOutput = v;
        return this;
    }

    /**
     * Sets character encoding for the stdout and the stderr streams of subprocesses
     * subsequently executed by this object. The default encoding is {@code UTF-8}.
     * <p>
     * Doesn't apply to executing {@code ToolProvider}-s.
     * <p>
     * The value will be ignored if this object is configured for byte output streams.
     *
     * @param v character encoding for output streams of subsequently executed
     *          subprocesses
     *
     * @return this
     */
    public CommandOutputControl processOutputCharset(Charset v) {
        processOutputCharset = v;
        return this;
    }

    /**
     * Configures if the stderr stream should be redirected into the stdout stream
     * for commands subsequently executed by this object.
     *
     * @see ProcessBuilder#redirectErrorStream(boolean)
     *
     * @param v {@code true} if the stderr stream of commands subsequently executed
     *          by this object should be redirected into the stdout stream;
     *          {@code false} otherwise
     *
     * @return this
     */
    public CommandOutputControl redirectErrorStream(boolean v) {
        redirectErrorStream = v;
        return this;
    }

    /**
     * Configures if stderr and stdout streams for subprocesses subsequently
     * executed by this object should be stored in files.
     * <p>
     * By default, if an output stream of a subprocess is configured for saving,
     * this object will retrieve the content using {@link Process#getInputStream()}
     * function for stdout and {@link Process#getErrorStream()} function for stderr.
     * However, these functions don't always work correctly due to a
     * <a href="https://bugs.openjdk.org/browse/JDK-8236825">JDK-8236825</a> bug
     * still reproducible on macOS JDK26. The alternative way to get the content of
     * output streams of a subprocess is to redirect them into files and read these
     * files when the subprocess terminates.
     * <p>
     * It will use {@code Files.createTempFile("jpackageOutputTempFile", ".tmp")} to
     * create a file for each subprocess's output stream configured for saving. All
     * created files will be automatically deleted at the exit of
     * {@link #execute(ProcessBuilder, long)} method.
     * <p>
     * Doesn't apply to executing {@code ToolProvider}-s.
     *
     * @param v {@code true} if this object should use files to store saved output
     *          streams of subsequently executed subprocesses; {@code false}
     *          otherwise
     * @return this
     */
    public CommandOutputControl storeStreamsInFiles(boolean v) {
        storeStreamsInFiles = v;
        return this;
    }

    public CommandOutputControl discardStdout(boolean v) {
        outputStreamsControl.stdout().discard(v);
        return this;
    }

    public CommandOutputControl discardStderr(boolean v) {
        outputStreamsControl.stderr().discard(v);
        return this;
    }

    public CommandOutputControl processNotifier(Consumer<Process> v) {
        processNotifier = v;
        return this;
    }

    public CommandOutputControl copy() {
        return new CommandOutputControl(this);
    }

    public interface ExecutableSpec {
    }

    public sealed interface Executable {

        ExecutableSpec spec();

        Result execute() throws IOException, InterruptedException;

        Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException;
    }

    public record ProcessSpec(Optional<Long> pid, List<String> cmdline) implements ExecutableSpec {
        public ProcessSpec {
            Objects.requireNonNull(pid);
            cmdline.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(cmdline);
        }
    }

    public record ToolProviderSpec(String name, List<String> args) implements ExecutableSpec {
        public ToolProviderSpec {
            Objects.requireNonNull(name);
            args.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(Stream.concat(Stream.of(name), args.stream()).toList());
        }
    }

    public static ExecutableSpec EMPTY_EXECUTABLE_SPEC = new ExecutableSpec() {
        @Override
        public String toString() {
            return "<unknown>";
        }
    };

    public Executable createExecutable(ToolProvider tp, String... args) {
        return new ToolProviderExecutable(tp, List.of(args), this);
    }

    public Executable createExecutable(ProcessBuilder pb) {
        return new ProcessExecutable(pb, this);
    }

    public interface Output {

        public Optional<List<String>> findContent();

        public default List<String> getContent() {
            return findContent().orElseThrow();
        }

        public default String getFirstLineOfOutput() {
            return findFirstLineOfOutput().orElseThrow();
        }

        public default Optional<String> findFirstLineOfOutput() {
            return findContent().map(List::stream).flatMap(Stream::findFirst);
        }
    }

    public record Result(
            Optional<Integer> exitCode,
            Optional<CommandOutput<List<String>>> output,
            Optional<CommandOutput<byte[]>> byteOutput,
            ExecutableSpec execSpec) implements Output {

        public Result {
            Objects.requireNonNull(exitCode);
            Objects.requireNonNull(output);
            Objects.requireNonNull(byteOutput);
            Objects.requireNonNull(execSpec);
        }

        public Result(int exitCode) {
            this(Optional.of(exitCode), Optional.empty(), Optional.empty(), EMPTY_EXECUTABLE_SPEC);
        }

        public int getExitCode() {
            return exitCode.orElseThrow(() -> {
                return new IllegalStateException("Exit code is unavailable for timed-out process");
            });
        }

        public Result expectExitCode(int main, int... other) throws UnexpectedExitCodeException {
            return expectExitCode(v -> {
                return IntStream.concat(IntStream.of(main), IntStream.of(other)).boxed().anyMatch(Predicate.isEqual(v));
            });
        }

        public Result expectExitCode(Collection<Integer> expected) throws UnexpectedExitCodeException {
            return expectExitCode(expected::contains);
        }

        public Result expectExitCode(IntPredicate expected) throws UnexpectedExitCodeException {
            if (!expected.test(getExitCode())) {
                throw new UnexpectedExitCodeException(this);
            }
            return this;
        }

        public List<String> getOutput() {
            return getContent();
        }

        @Override
        public Optional<List<String>> findContent() {
            return output.flatMap(CommandOutput::combined);
        }

        public Optional<Output> findStdout() {
            return output.flatMap(CommandOutput::stdout).map(Result::createView);
        }

        public Optional<Output> findStderr() {
            return output.flatMap(CommandOutput::stderr).map(Result::createView);
        }

        public Output stdout() {
            return findStdout().orElseThrow();
        }

        public Output stderr() {
            return findStderr().orElseThrow();
        }

        public byte[] getByteContent() {
            return findByteContent().orElseThrow();
        }

        public Optional<byte[]> findByteContent() {
            return byteOutput.flatMap(CommandOutput::combined);
        }

        public Optional<byte[]> findByteStdout() {
            return byteOutput.flatMap(CommandOutput::stdout);
        }

        public Optional<byte[]> findByteStderr() {
            return byteOutput.flatMap(CommandOutput::stderr);
        }

        public byte[] byteStdout() {
            return findByteStdout().orElseThrow();
        }

        public byte[] byteStderr() {
            return findByteStderr().orElseThrow();
        }

        public Result toCharacterResult(Charset charset, boolean keepByteContent) throws IOException {
            Objects.requireNonNull(charset);

            if (byteOutput.isEmpty()) {
                return this;
            }

            var theByteOutput = byteOutput.get();

            try {
                Optional<? extends Content<List<String>>> out;
                if (theByteOutput.content().isEmpty()) {
                    // The content is unavailable.
                    out = Optional.empty();
                } else if (theByteOutput.stdoutContentSize() == 0) {
                    // The content is available, but empty.
                    out = Optional.of(new StringListContent(List.of()));
                } else if (theByteOutput.interleaved()) {
                    // STDOUT and STDERR streams are interleaved.
                    out = theByteOutput.combined().map(data -> {
                        return toStringList(data, charset);
                    });
                } else {
                    // Non-empty STDOUT not interleaved with STDERR.
                    out = findByteStdout().map(data -> {
                        return toStringList(data, charset);
                    });
                }

                var err = findByteStderr().map(data -> {
                    return toStringList(data, charset);
                });

                var newOutput = combine(out, err, theByteOutput.interleaved);

                return new Result(exitCode, Optional.of(newOutput), byteOutput.filter(_ -> keepByteContent), execSpec);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }

        private static Output createView(List<String> lines) {
            Objects.requireNonNull(lines);
            return new Output() {

                @Override
                public Optional<List<String>> findContent() {
                    return Optional.of(lines);
                }
            };
        }

        private static StringListContent toStringList(byte[] data, Charset charset) {
            try (var bufReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), charset))) {
                return new StringListContent(bufReader.lines().toList());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public static sealed class UnexpectedResultException extends IOException {

        public UnexpectedResultException(Result value, String message) {
            super(Objects.requireNonNull(message));
            this.value = Objects.requireNonNull(value);
        }

        public UnexpectedResultException(Result value) {
            this(value, String.format("Unexpected result from executing the command %s", value.execSpec()));
        }

        Result getResult() {
            return value;
        }

        private final transient Result value;

        private static final long serialVersionUID = 1L;
    }

    public static final class UnexpectedExitCodeException extends UnexpectedResultException {

        public UnexpectedExitCodeException(Result value, String message) {
            super(value, message);
        }

        public UnexpectedExitCodeException(Result value) {
            this(value, String.format("Unexpected exit code %d from executing the command %s", value.getExitCode(), value.execSpec()));
        }

        private static final long serialVersionUID = 1L;
    }

    public String description() {
        var tokens = outputStreamsControl.descriptionTokens();
        if (binaryOutput) {
            tokens.add("byte");
        }
        if (redirectErrorStream()) {
            tokens.add("interleave");
        }
        return String.join("; ", tokens);
    }

    private Result execute(ProcessBuilder pb, long timeoutMillis) throws IOException, InterruptedException {

        Objects.requireNonNull(pb);

        var charset = processOutputCharset();

        configureProcessBuilder(pb);

        var csc = new CachingStreamsConfig();

        var process = pb.start();

        Optional.ofNullable(processNotifier).ifPresent(c -> {
            c.accept(process);
        });

        BiConsumer<InputStream, PrintStream> gobbler = (in, ps) -> {
            if (binaryOutput) {
                try (in) {
                    in.transferTo(ps);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            } else {
                try (var bufReader = new BufferedReader(new InputStreamReader(in, charset))) {
                    bufReader.lines().forEach(ps::println);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        };

        // Start fetching process output streams.
        // Do it before waiting for the process termination to avoid deadlocks.

        final Optional<CompletableFuture<Void>> stdoutGobbler;
        if (mustReadOutputStream(pb.redirectOutput())) {
            stdoutGobbler = Optional.of(CompletableFuture.runAsync(() -> {
                gobbler.accept(process.getInputStream(), csc.out());
            }));
        } else {
            stdoutGobbler = Optional.empty();
        }

        final Optional<CompletableFuture<Void>> stderrGobbler;
        if (!pb.redirectErrorStream() && mustReadOutputStream(pb.redirectError())) {
            stderrGobbler = Optional.of(CompletableFuture.runAsync(() -> {
                gobbler.accept(process.getErrorStream(), csc.err());
            }));
        } else {
            stderrGobbler = Optional.empty();
        }

        final Optional<Integer> exitCode;
        if (timeoutMillis < 0) {
            exitCode = Optional.of(process.waitFor());
        } else if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroy();
            exitCode = Optional.empty();
        } else {
            exitCode = Optional.of(process.exitValue());
        }

        try {
            if (storeStreamsInFiles) {
                var stdoutStorage = streamFileSink(pb.redirectOutput());
                var stderrStorage = streamFileSink(pb.redirectError());

                Function<Path, InputStream> toInputStream = path -> {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                };

                try {
                    stdoutStorage.map(toInputStream).ifPresent(in -> {
                        gobbler.accept(in, csc.out());
                    });

                    stderrStorage.map(toInputStream).ifPresent(in -> {
                        gobbler.accept(in, csc.err());
                    });
                } finally {
                    Consumer<Path> silentDeleter = path -> {
                        suppressIOException(Files::delete, path);
                    };

                    stdoutStorage.ifPresent(silentDeleter);
                    stderrStorage.ifPresent(silentDeleter);
                }
            } else {
                stdoutGobbler.ifPresent(CommandOutputControl::joinProcessStreamGobbler);
                stderrGobbler.ifPresent(CommandOutputControl::joinProcessStreamGobbler);
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }

        return csc.createResult(exitCode, new ProcessSpec(getPID(process), pb.command()));
    }

    private Result execute(ToolProvider tp, String... args) throws IOException {
        var csc = new CachingStreamsConfig();

        final int exitCode;
        var out = csc.out();
        var err = csc.err();
        try {
            exitCode = tp.run(out, err, args);
        } finally {
            suppressIOException(out::flush);
            suppressIOException(err::flush);
        }

        return csc.createResult(Optional.of(exitCode), new ToolProviderSpec(tp.name(), List.of(args)));
    }

    private CommandOutputControl setOutputControl(boolean set, OutputControlOption v) {
        outputStreamsControl.stdout().set(set, v);
        outputStreamsControl.stderr().set(set, v);
        return this;
    }

    private Optional<Path> streamFileSink(ProcessBuilder.Redirect redirect) {
        return Optional.of(redirect)
                .filter(Predicate.isEqual(ProcessBuilder.Redirect.DISCARD).negate())
                .map(ProcessBuilder.Redirect::file)
                .map(File::toPath);
    }

    private void configureProcessBuilder(ProcessBuilder pb) throws IOException {

        var stdoutRedirect = outputStreamsControl.stdout().asProcessBuilderRedirect();
        var stderrRedirect = outputStreamsControl.stderr().asProcessBuilderRedirect();

        if (!stdoutRedirect.equals(stderrRedirect) && Stream.of(
                stdoutRedirect,
                stderrRedirect
        ).noneMatch(Predicate.isEqual(ProcessBuilder.Redirect.DISCARD)) && redirectErrorStream()) {
            throw new IllegalStateException(String.format(
                    "Can't redirect stderr into stdout because they have different redirects: stdout=%s; stderr=%s",
                    stdoutRedirect, stderrRedirect));
        }

        pb.redirectErrorStream(redirectErrorStream());
        if (replaceStdoutWithStderr()) {
            if (stderrRedirect.equals(ProcessBuilder.Redirect.INHERIT)) {
                stderrRedirect = ProcessBuilder.Redirect.PIPE;
            }
            pb.redirectErrorStream(false);
        }

        stdoutRedirect = mapRedirect(stdoutRedirect);
        stderrRedirect = mapRedirect(stderrRedirect);

        pb.redirectOutput(stdoutRedirect);
        pb.redirectError(stderrRedirect);
    }

    private ProcessBuilder.Redirect mapRedirect(ProcessBuilder.Redirect redirect) throws IOException {
        if (storeStreamsInFiles && redirect.equals(ProcessBuilder.Redirect.PIPE)) {
            var sink = Files.createTempFile("jpackageOutputTempFile", ".tmp");
            return ProcessBuilder.Redirect.to(sink.toFile());
        } else {
            return redirect;
        }
    }

    private boolean redirectErrorStream() {
        return redirectErrorStream && !outputStreamsControl.stderr().discard();
    }

    private boolean replaceStdoutWithStderr() {
        return redirectErrorStream() && outputStreamsControl.stdout().discard();
    }

    private Charset processOutputCharset() {
        return Optional.ofNullable(processOutputCharset).orElse(StandardCharsets.UTF_8);
    }

    private static void joinProcessStreamGobbler(CompletableFuture<Void> streamGobbler) {
        try {
            streamGobbler.join();
        } catch (CancellationException ex) {
            return;
        } catch (CompletionException ex) {
            switch (ExceptionBox.unbox(ex.getCause())) {
                case IOException cause -> {
                    throw new UncheckedIOException(cause);
                }
                case UncheckedIOException cause -> {
                    throw cause;
                }
                case Exception cause -> {
                    throw ExceptionBox.toUnchecked(cause);
                }
            }
        }
    }

    private static boolean mustReadOutputStream(ProcessBuilder.Redirect redirect) {
        return redirect.equals(ProcessBuilder.Redirect.PIPE);
    }

    private static Optional<List<String>> read(OutputControl outputControl, CachingPrintStream cps) throws IOException {
        final var bufferAsString = cps.bufferContents();
        try (final var bufReader = new BufferedReader(new StringReader(bufferAsString.orElse("")))) {
            if (outputControl.saveFirstLine()) {
                return Optional.of(bufReader.lines().findFirst().map(List::of).orElseGet(List::of));
            } else if (outputControl.saveAll()) {
                return Optional.of(bufReader.lines().toList());
            } else {
                return Optional.empty();
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static Optional<byte[]> readBinary(OutputControl outputControl, CachingPrintStream cps) {
        if (outputControl.save()) {
            return cps.buf().map(ByteArrayOutputStream::toByteArray).or(() -> {
                return Optional.of(new byte[0]);
            });
        } else {
            return Optional.empty();
        }
    }

    private static <T> CommandOutput<T> combine(
            Optional<? extends Content<T>> out,
            Optional<? extends Content<T>> err,
            boolean interleaved) {

        if (out.isEmpty() && err.isEmpty()) {
            return CommandOutput.empty();
        } else if (out.isEmpty()) {
            // This branch is unreachable because it is impossible to make it save stderr without saving stdout.
            // If streams are configured for saving and stdout is discarded,
            // its saved contents will be an Optional instance wrapping an empty content, not an empty Optional.
            throw ExceptionBox.reachedUnreachable();
        } else if (err.isEmpty()) {
            return new CommandOutput<>(out, Integer.MAX_VALUE, interleaved);
        } else {
            final var combined = out.get().append(err.get());
            return new CommandOutput<>(Optional.of(combined), out.orElseThrow().size(), interleaved);
        }
    }

    private static PrintStream nullPrintStream() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    private sealed interface Content<T> {
        T data();
        int size();
        Content<T> slice(int from, int to);
        Content<T> append(Content<T> other);
    }

    private record StringListContent(List<String> data) implements Content<List<String>> {
        StringListContent {
            Objects.requireNonNull(data);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public StringListContent slice(int from, int to) {
            return new StringListContent(data.subList(from, to));
        }

        @Override
        public StringListContent append(Content<List<String>> other) {
            return new StringListContent(Stream.of(data, other.data()).flatMap(List::stream).toList());
        }
    }

    private record ByteContent(byte[] data) implements Content<byte[]> {
        ByteContent {
            Objects.requireNonNull(data);
        }

        @Override
        public int size() {
            return data.length;
        }

        @Override
        public ByteContent slice(int from, int to) {
            return new ByteContent(Arrays.copyOfRange(data, from, to));
        }

        @Override
        public ByteContent append(Content<byte[]> other) {
            byte[] combined = new byte[size() + other.size()];
            System.arraycopy(data, 0, combined, 0, data.length);
            System.arraycopy(other.data(), 0, combined, data.length, other.size());
            return new ByteContent(combined);
        }
    }

    private record OutputStreamsControl(OutputControl stdout, OutputControl stderr) {
        OutputStreamsControl {
            Objects.requireNonNull(stdout);
            Objects.requireNonNull(stderr);
        }

        OutputStreamsControl() {
            this(new OutputControl(), new OutputControl());
        }

        OutputStreamsControl copy() {
            return new OutputStreamsControl(stdout.copy(), stderr.copy());
        }

        List<String> descriptionTokens() {
            final List<String> tokens = new ArrayList<>();
            if (stdout.save()) { // Save flags are the same for stdout and stderr, checking stdout is sufficient.
                streamsLabel("save ", true).ifPresent(tokens::add);
            }
            if (stdout.dump() || stderr.dump()) {
                streamsLabel("echo ", true).ifPresent(tokens::add);
            }
            streamsLabel("discard ", false).ifPresent(tokens::add);
            if (tokens.isEmpty()) {
                // Unreachable because there is always at least one token in the description.
                throw ExceptionBox.reachedUnreachable();
            } else {
                return tokens;
            }
        }

        private Optional<String> streamsLabel(String prefix, boolean negate) {
            Objects.requireNonNull(prefix);
            final var str = Stream.of(stdoutLabel(negate), stderrLabel(negate))
                    .filter(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .collect(joining("+"));
            if (str.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(prefix + str);
            }
        }

        private Optional<String> stdoutLabel(boolean negate) {
            if ((stdout.discard() && !negate) || (!stdout.discard() && negate)) {
                return Optional.of("out");
            } else {
                return Optional.empty();
            }
        }

        private Optional<String> stderrLabel(boolean negate) {
            if ((stderr.discard() && !negate) || (!stderr.discard() && negate)) {
                return Optional.of("err");
            } else {
                return Optional.empty();
            }
        }
    }

    private record CachingPrintStream(PrintStream ps, Optional<ByteArrayOutputStream> buf) {
        CachingPrintStream {
            Objects.requireNonNull(ps);
            Objects.requireNonNull(buf);
        }

        Optional<String> bufferContents() {
            return buf.map(ByteArrayOutputStream::toString);
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Builder save(boolean v) {
                save = v;
                return this;
            }

            Builder discard(boolean v) {
                discard = v;
                return this;
            }

            Builder dumpStream(PrintStream v) {
                dumpStream = v;
                return this;
            }

            Builder buffer(ByteArrayOutputStream v) {
                externalBuffer = v;
                return this;
            }

            CachingPrintStream create() {
                final Optional<ByteArrayOutputStream> buf;
                if (save && !discard) {
                    buf = Optional.ofNullable(externalBuffer).or(() -> {
                        return Optional.of(new ByteArrayOutputStream());
                    });
                } else {
                    buf = Optional.empty();
                }

                final PrintStream ps;
                if (buf.isPresent() && dumpStream != null) {
                    ps = new PrintStream(new TeeOutputStream(List.of(buf.get(), dumpStream)), true, dumpStream.charset());
                } else if (!discard) {
                    ps = buf.map(PrintStream::new).or(() -> {
                        return Optional.ofNullable(dumpStream);
                    }).orElseGet(CommandOutputControl::nullPrintStream);
                } else {
                    ps = nullPrintStream();
                }

                return new CachingPrintStream(ps, buf);
            }

            private boolean save;
            private boolean discard;
            private PrintStream dumpStream;
            private ByteArrayOutputStream externalBuffer;
        }
    }

    private final class CachingStreamsConfig {

        CachingStreamsConfig() {
            out = outputStreamsControl.stdout().buildCachingPrintStream(System.out).create();
            if (redirectErrorStream) {
                var builder = outputStreamsControl.stderr().buildCachingPrintStream(System.out);
                out.buf().ifPresent(builder::buffer);
                err = builder.create();
            } else {
                err = outputStreamsControl.stderr().buildCachingPrintStream(System.err).create();
            }
        }

        Result createResult(Optional<Integer> exitCode, ExecutableSpec execSpec) throws IOException {

            CommandOutput<List<String>> output;
            CommandOutput<byte[]> byteOutput;

            CachingPrintStream effectiveOut;
            if (out.buf().isEmpty() && redirectErrorStream) {
                effectiveOut = new CachingPrintStream(nullPrintStream(), err.buf());
            } else {
                effectiveOut = out;
            }

            if (binaryOutput) {
                Optional<ByteContent> outContent, errContent;
                if (redirectErrorStream) {
                    outContent = readBinary(outputStreamsControl.stdout(), effectiveOut).map(ByteContent::new);
                    errContent = Optional.empty();
                } else {
                    outContent = readBinary(outputStreamsControl.stdout(), out).map(ByteContent::new);
                    errContent = readBinary(outputStreamsControl.stderr(), err).map(ByteContent::new);
                }

                byteOutput = combine(outContent, errContent, redirectErrorStream());
                output = null;
            } else {
                Optional<StringListContent> outContent, errContent;
                if (redirectErrorStream) {
                    outContent = read(outputStreamsControl.stdout(), effectiveOut).map(StringListContent::new);
                    errContent = Optional.empty();
                } else {
                    outContent = read(outputStreamsControl.stdout(), out).map(StringListContent::new);
                    errContent = read(outputStreamsControl.stderr(), err).map(StringListContent::new);
                }

                output = combine(outContent, errContent, redirectErrorStream());
                byteOutput = null;
            }

            return new Result(exitCode, Optional.ofNullable(output), Optional.ofNullable(byteOutput), execSpec);
        }

        PrintStream out() {
            return out.ps();
        }

        PrintStream err() {
            return err.ps();
        }

        private final CachingPrintStream out;
        private final CachingPrintStream err;
    }

    private static final class OutputControl {

        OutputControl() {
        }

        private OutputControl(OutputControl other) {
            dump = other.dump;
            discard = other.discard;
            save = other.save;
        }

        boolean save() {
            return save.isPresent();
        }

        boolean saveAll() {
            return save.orElse(null) == OutputControlOption.SAVE_ALL;
        }

        boolean saveFirstLine() {
            return save.orElse(null) == OutputControlOption.SAVE_FIRST_LINE;
        }

        boolean discard() {
            return discard || (!dump && save.isEmpty());
        }

        boolean dump() {
            return !discard && dump;
        }

        OutputControl dump(boolean v) {
            this.dump = v;
            return this;
        }

        OutputControl discard(boolean v) {
            this.discard = v;
            return this;
        }

        OutputControl saveAll(boolean v) {
            if (v) {
                save = Optional.of(OutputControlOption.SAVE_ALL);
            } else {
                save = Optional.empty();
            }
            return this;
        }

        OutputControl saveFirstLine(boolean v) {
            if (v) {
                save = Optional.of(OutputControlOption.SAVE_FIRST_LINE);
            } else {
                save = Optional.empty();
            }
            return this;
        }

        OutputControl set(boolean set, OutputControlOption v) {
            switch (v) {
            case DUMP -> dump(set);
            case SAVE_ALL -> saveAll(set);
            case SAVE_FIRST_LINE -> saveFirstLine(set);
            }
            return this;
        }

        OutputControl copy() {
            return new OutputControl(this);
        }

        ProcessBuilder.Redirect asProcessBuilderRedirect() {
            if (discard()) {
                return ProcessBuilder.Redirect.DISCARD;
            } else if (dump && !save()) {
                return ProcessBuilder.Redirect.INHERIT;
            } else {
                return ProcessBuilder.Redirect.PIPE;
            }
        }

        CachingPrintStream.Builder buildCachingPrintStream(PrintStream dumpStream) {
            Objects.requireNonNull(dumpStream);
            final var builder = CachingPrintStream.build().save(save()).discard(discard());
            if (dump()) {
                builder.dumpStream(dumpStream);
            }
            return builder;
        }

        private boolean dump;
        private boolean discard;
        private Optional<OutputControlOption> save = Optional.empty();
    }

    private static final class TeeOutputStream extends OutputStream {

        public TeeOutputStream(Iterable<OutputStream> streams) {
            streams.forEach(Objects::requireNonNull);
            this.streams = streams;
        }

        @Override
        public void write(int b) throws IOException {
            for (final var out : streams) {
                out.write(b);
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            for (final var out : streams) {
                out.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (final var out : streams) {
                out.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            forEach(OutputStream::flush);
        }

        @Override
        public void close() throws IOException {
            forEach(OutputStream::close);
        }

        private void forEach(ThrowingConsumer<OutputStream, IOException> c) throws IOException {
            IOException firstEx = null;
            for (final var out : streams) {
                try {
                    c.accept(out);
                } catch (IOException e) {
                    if (firstEx == null) {
                        firstEx = e;
                    }
                }
            }
            if (firstEx != null) {
                throw firstEx;
            }
        }

        private final Iterable<OutputStream> streams;
    }

    private record CommandOutput<T>(Optional<? extends Content<T>> content, int stdoutContentSize, boolean interleaved) {

        CommandOutput {
            Objects.requireNonNull(content);
            if (interleaved) {
                stdoutContentSize = content.map(Content::size).orElse(-1);
            }
        }

        CommandOutput() {
            this(Optional.empty(), 0, false);
        }

        Optional<T> combined() {
            return content.map(Content::data);
        }

        /**
         * Returns non-empty {@code Optional} if stdout is available and stdout and stderr are not interleaved.
         * @return stdout if it can be extracted from the combined output
         */
        Optional<T> stdout() {
            if (withoutExtractableStdout()) {
                return Optional.empty();
            }

            final var theContent = content.orElseThrow();
            if (stdoutContentSize == theContent.size()) {
                return combined();
            } else {
                return Optional.of(theContent.slice(0, Integer.min(stdoutContentSize, theContent.size())).data());
            }
        }

        /**
         * Returns non-empty {@code Optional} if stderr is available and stdout and stderr are not interleaved.
         * @return stderr if it can be extracted from the combined output
         */
        Optional<T> stderr() {
            if (withoutExtractableStderr()) {
                return Optional.empty();
            } else if (stdoutContentSize <= 0) {
                return combined();
            } else {
                final var theContent = content.orElseThrow();
                return Optional.of(theContent.slice(stdoutContentSize, theContent.size()).data());
            }
        }

        @SuppressWarnings("unchecked")
        static <T> CommandOutput<T> empty() {
            return (CommandOutput<T>)EMPTY;
        }

        private boolean withoutExtractableStdout() {
            return interleaved || content.isEmpty() || stdoutContentSize < 0;
        }

        private boolean withoutExtractableStderr() {
            return interleaved || content.isEmpty() || stdoutContentSize > content.get().size();
        }

        private static final CommandOutput<?> EMPTY = new CommandOutput<>();
    }

    private record ToolProviderExecutable(ToolProvider tp, List<String> args, CommandOutputControl coc) implements Executable {

        ToolProviderExecutable {
            Objects.requireNonNull(tp);
            Objects.requireNonNull(args);
            Objects.requireNonNull(coc);
        }

        @Override
        public Result execute() throws IOException {
            return coc.execute(tp, args.toArray(String[]::new));
        }

        @Override
        public Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Executing tool provider with timeout is unsupported");
        }

        @Override
        public ExecutableSpec spec() {
            return new ToolProviderSpec(tp.name(), args);
        }
    }

    private record ProcessExecutable(ProcessBuilder pb, CommandOutputControl coc) implements Executable {

        ProcessExecutable {
            Objects.requireNonNull(pb);
            Objects.requireNonNull(coc);
        }

        @Override
        public Result execute() throws IOException, InterruptedException {
            return coc.execute(pb, -1L);
        }

        @Override
        public Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException {
            return coc.execute(pb, unit.toMillis(timeout));
        }

        @Override
        public ExecutableSpec spec() {
            return new ProcessSpec(Optional.empty(), pb.command());
        }
    }

    private static Optional<Long> getPID(Process p) {
        try {
            return Optional.of(p.pid());
        } catch (UnsupportedOperationException ex) {
            return Optional.empty();
        }
    }

    private static void suppressIOException(ThrowingRunnable<IOException> r) {
        try {
            r.run();
        } catch (IOException ex) {}
    }

    private static <T> void suppressIOException(ThrowingConsumer<T, IOException> c, T value) {
        suppressIOException(() -> {
            c.accept(value);
        });
    }

    private final OutputStreamsControl outputStreamsControl;
    private Charset processOutputCharset;
    private boolean redirectErrorStream;
    private boolean storeStreamsInFiles;
    private boolean binaryOutput;
    private Consumer<Process> processNotifier;

    private static enum OutputControlOption {
        SAVE_ALL, SAVE_FIRST_LINE, DUMP
    }
}
