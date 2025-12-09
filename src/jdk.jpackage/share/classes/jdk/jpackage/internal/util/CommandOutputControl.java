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
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * Configures the output streams of a process or a tool provider and executes
 * it.
 * <p>
 * Can save the first line of an output stream, the entire output stream or
 * discard it.
 * <p>
 * Supports separate configurations for stdout and stderr streams.
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
 * System.out: STDOUT & STDERR intertwined
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
 * Result.findContent(): STDOUT & STDERR intertwined
 * <p>
 * Result.findStdout(): {@code Optional.empty()}
 * <p>
 * Result.findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * Result.findContent(): an {@code Optional} object wrapping a single item list
 * with the first line of intertwined STDOUT & STDERR if a command wrote any of
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
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputStreamsControl, processOutputCharset, redirectErrorStream, storeStreamsInFiles);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CommandOutputControl other = (CommandOutputControl) obj;
        return Objects.equals(outputStreamsControl, other.outputStreamsControl)
                && Objects.equals(processOutputCharset, other.processOutputCharset)
                && redirectErrorStream == other.redirectErrorStream && storeStreamsInFiles == other.storeStreamsInFiles;
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
     * Sets character encoding for the stdout and the stderr streams of subprocesses
     * subsequently executed by this object. The default encoding is {@code UTF-8}.
     * <p>
     * Doesn't apply to executing {@code ToolProvider}-s.
     *
     * @param v character encoding for output stream of subsequently executed
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

    public CommandOutputControl copy() {
        return new CommandOutputControl(this);
    }

    public sealed interface Executable {

        public interface ExecutableSpec {
        }

        ExecutableSpec spec();

        Result execute() throws IOException, InterruptedException;

        default Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
            var task = new FutureTask<>(this::execute);
            Thread.ofVirtual().start(task);
            try {
                return task.get(timeout, unit);
            } catch (ExecutionException ex) {
                try {
                    throw ex.getCause();
                } catch (IOException|InterruptedException|TimeoutException|RuntimeException cause) {
                    throw cause;
                } catch (Throwable t) {
                    throw ExceptionBox.rethrowUnchecked(t);
                }
            }
        }
    }

    public record ProcessSpec(Optional<Long> pid, List<String> cmdline) implements Executable.ExecutableSpec {
        public ProcessSpec {
            Objects.requireNonNull(pid);
            cmdline.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(cmdline);
        }
    }

    public record ToolProviderSpec(String name, List<String> args) implements Executable.ExecutableSpec {
        public ToolProviderSpec {
            Objects.requireNonNull(name);
            args.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(Stream.concat(Stream.of(name), args.stream()).toList());
        }
    }

    public static Executable.ExecutableSpec EMPTY_EXECUTABLE_SPEC = new Executable.ExecutableSpec() {
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

    public record Result(Optional<Integer> exitCode, CommandOutput output, Executable.ExecutableSpec execSpec) implements Output {
        public Result {
            Objects.requireNonNull(output);
        }

        public Result(int exitCode) {
            this(Optional.of(exitCode), CommandOutput.EMPTY, EMPTY_EXECUTABLE_SPEC);
        }

        public int getExitCode() {
            return exitCode.orElseThrow(() -> {
                return new UnsupportedOperationException("Exit code is unavailable for timed-out process");
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
            return output.lines();
        }

        public Optional<Output> findStdout() {
            return output.stdoutLines().map(Result::createView);
        }

        public Optional<Output> findStderr() {
            return output.stderrLines().map(Result::createView);
        }

        public Output stdout() {
            return findStdout().orElseThrow();
        }

        public Output stderr() {
            return findStderr().orElseThrow();
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
            this(value, String.format("Unexpected exit code %d from executing the command %s", value.exitCode(), value.execSpec()));
        }

        private static final long serialVersionUID = 1L;
    }

    public String description() {
        return outputStreamsControl.describe();
    }

    private Result execute(ProcessBuilder pb, long timeoutMillis)
            throws IOException, InterruptedException {

        var charset = Optional.ofNullable(processOutputCharset).orElse(StandardCharsets.UTF_8);

        configureProcessBuilder(pb);

        var process = pb.start();

        final Optional<FutureTask<Optional<List<String>>>> readStdoutTask;
        if (mustReadOutputStream(pb.redirectOutput())) {
            readStdoutTask = Optional.of(new FutureTask<>(() -> {
                try (final var reader = process.inputReader(charset)) {
                    return processProcessStream(outputStreamsControl.stdout(), reader, true, System.out);
                }
            }));
        } else {
            readStdoutTask = Optional.empty();
        }

        final Optional<FutureTask<Optional<List<String>>>> readStderrTask;
        if (!pb.redirectErrorStream() && mustReadOutputStream(pb.redirectError())) {
            readStderrTask = Optional.of(new FutureTask<>(() -> {
                var outputControl = outputStreamsControl.stderr();
                PrintStream dumpStream;
                if (replaceStdoutWithStderr() && outputControl.dump()) {
                    dumpStream = System.out;
                } else {
                    dumpStream = System.err;
                }
                try (final var reader = process.errorReader(charset)) {
                    return processProcessStream(outputControl, reader, true, dumpStream);
                }
            }));
        } else {
            readStderrTask = Optional.empty();
        }

        // Start fetching process output streams.
        // Do it before waiting for the process termination to avoid deadlocks.
        readStdoutTask.ifPresent(Thread.ofVirtual()::start);
        readStderrTask.ifPresent(Thread.ofVirtual()::start);

        final Optional<Integer> exitCode;
        if (timeoutMillis < 0) {
            exitCode = Optional.of(process.waitFor());
        } else if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroy();
            exitCode = Optional.empty();
        } else {
            exitCode = Optional.of(process.exitValue());
        }

        CommandOutput output;
        if (storeStreamsInFiles) {
            output = processProcessOutputStoredInFiles(pb);
        } else {
            var stdout = getStreamContent(readStdoutTask, outputStreamsControl.stdout());
            var stderr = getStreamContent(readStderrTask, outputStreamsControl.stderr());

            output = combine(stdout, redirectErrorStream ? Optional.empty() : stderr);
        }

        return new Result(exitCode, output, new ProcessSpec(getPID(process), pb.command()));
    }

    private Result execute(ToolProvider tp, String... args) throws IOException {
        final var tpStreamConfig = ToolProviderStreamConfig.create(outputStreamsControl, redirectErrorStream);

        final int exitCode;
        try (var tpStdout = tpStreamConfig.out().ps()) {
            try (var tpStderr = tpStreamConfig.err().ps()) {
                exitCode = tp.run(tpStdout, tpStderr, args);
            }
        }

        var stdout = read(outputStreamsControl.stdout(), tpStreamConfig.out());
        var stderr = read(outputStreamsControl.stderr(), tpStreamConfig.err());

        final var output = combine(stdout, redirectErrorStream ? Optional.empty() : stderr);

        return new Result(Optional.of(exitCode), output, new ToolProviderSpec(tp.name(), List.of(args)));
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
            throw new UnsupportedOperationException(String.format(
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

    private CommandOutput processProcessOutputStoredInFiles(ProcessBuilder pb) throws IOException {
        Objects.requireNonNull(pb);
        if (!storeStreamsInFiles) {
            throw new IllegalStateException();
        }

        final var charset = Optional.ofNullable(processOutputCharset).orElse(StandardCharsets.UTF_8);

        final var stdoutStorage = streamFileSink(pb.redirectOutput());
        final var stderrStorage = streamFileSink(pb.redirectError());

        try {
            final Optional<List<String>> stdout;
            if (stdoutStorage.isPresent()) {
                try (final var reader = Files.newBufferedReader(stdoutStorage.get(), charset)) {
                    stdout = processProcessStream(outputStreamsControl.stdout(), reader, false, System.out);
                }
            } else if (outputStreamsControl.stdout().save()) {
                stdout = Optional.of(List.of());
            } else {
                stdout = Optional.empty();
            }

            final Optional<List<String>> stderr;
            if (stderrStorage.isPresent()) {
                var outputControl = outputStreamsControl.stderr();
                PrintStream dumpStream;
                if (replaceStdoutWithStderr() && outputControl.dump()) {
                    dumpStream = System.out;
                } else {
                    dumpStream = System.err;
                }
                try (final var reader = Files.newBufferedReader(stderrStorage.get(), charset)) {
                    stderr = processProcessStream(outputControl, reader, false, dumpStream);
                }
            } else if (outputStreamsControl.stderr().save()) {
                stderr = Optional.of(List.of());
            } else {
                stderr = Optional.empty();
            }

            final var output = combine(stdout, redirectErrorStream ? Optional.empty() : stderr);
            return output;
        } finally {
            Consumer<Path> silentDeleter = path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                }
            };

            stdoutStorage.ifPresent(silentDeleter);
            stderrStorage.ifPresent(silentDeleter);
        }
    }

    private boolean redirectErrorStream() {
        return redirectErrorStream && !outputStreamsControl.stderr().discard();
    }

    private boolean replaceStdoutWithStderr() {
        return redirectErrorStream() && outputStreamsControl.stdout().discard();
    }

    private static Optional<List<String>> getStreamContent(
            Optional<? extends Future<? extends Optional<List<String>>>> f, OutputControl c)
            throws IOException, InterruptedException {

        Objects.requireNonNull(f);
        Objects.requireNonNull(c);

        if (f.isPresent()) {
            return getFutureResult(f.get());
        } else if (c.save()) {
            return Optional.of(List.of());
        } else {
            return Optional.empty();
        }
    }

    private static <T> T getFutureResult(Future<T> f) throws IOException, InterruptedException {
        try {
            return f.get();
        } catch (ExecutionException ex) {
            switch (ex.getCause()) {
                case IOException cause -> {
                    throw cause;
                }
                case InterruptedException cause -> {
                    throw cause;
                }
                case RuntimeException cause -> {
                    throw cause;
                }
                default -> {
                    throw ExceptionBox.rethrowUnchecked(ex.getCause());
                }
            }
        }
    }

    private static boolean mustReadOutputStream(ProcessBuilder.Redirect redirect) {
        return redirect.equals(ProcessBuilder.Redirect.PIPE);
    }

    private static Optional<List<String>> processProcessStream(
            OutputControl outputControl,
            BufferedReader bufReader,
            boolean readAll,
            PrintStream dumpStream) throws IOException {

        List<String> outputLines = null;
        try {
            if (outputControl.dump() || outputControl.saveAll()) {
                outputLines = new ArrayList<>();
                for (;;) {
                    var line = bufReader.readLine();
                    if (line == null) {
                        break;
                    }
                    outputLines.add(line);
                }
            } else if (outputControl.saveFirstLine()) {
                outputLines = Optional.ofNullable(bufReader.readLine()).map(List::of).orElseGet(List::of);
                if (readAll) {
                    // Read all input, or the started process may exit with an error (cmd.exe does so).
                    bufReader.transferTo(Writer.nullWriter());
                }
            } else if (readAll) {
                // This should be empty input stream, fetch it anyway.
                bufReader.transferTo(Writer.nullWriter());
            }
        } finally {
            if (outputControl.dump() && outputLines != null) {
                outputLines.forEach(dumpStream::println);
                if (outputControl.saveFirstLine()) {
                    outputLines = outputLines.stream().findFirst().map(List::of).orElseGet(List::of);
                }
            }
            if (!outputControl.save()) {
                outputLines = null;
            }
        }
        return Optional.ofNullable(outputLines);
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

    private CommandOutput combine(Optional<List<String>> out, Optional<List<String>> err) {
        if (out.isEmpty() && err.isEmpty()) {
            return CommandOutput.EMPTY;
        } else if (out.isEmpty()) {
            // This branch is unreachable because it is impossible to make it save stderr without saving stdout.
            // If streams are configured for saving and stdout is discarded, 
            // its saved contents will be an Optional instance wrapping an empty list, not an empty Optional.
            throw new AssertionError();
        } else if (err.isEmpty()) {
            return new CommandOutput(out, Integer.MAX_VALUE, redirectErrorStream());
        } else {
            final var combined = Stream.of(out, err).map(Optional::orElseThrow).flatMap(List::stream);
            return new CommandOutput(Optional.of(combined.toList()), out.orElseThrow().size(), redirectErrorStream());
        }
    }

    private static PrintStream nullPrintStream() {
        return new PrintStream(OutputStream.nullOutputStream());
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

        String describe() {
            final List<String> tokens = new ArrayList<>();
            if (stdout.save()) { // Save flags are the same for stdout and stderr, checking stdout is sufficient.
                streamsLabel("save ", true).ifPresent(tokens::add);
            }
            if (stdout.dump() || stderr.dump()) {
                streamsLabel("inherit ", true).ifPresent(tokens::add);
            }
            streamsLabel("discard ", false).ifPresent(tokens::add);
            if (tokens.isEmpty()) {
                // Unreachable because there is always at least one token in the description.
                throw new AssertionError();
            } else {
                return String.join("; ", tokens);
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

            CachingPrintStream create() {
                final Optional<ByteArrayOutputStream> buf;
                if (save && !discard) {
                    buf = Optional.of(new ByteArrayOutputStream());
                } else {
                    buf = Optional.empty();
                }

                final PrintStream ps;
                if (buf.isPresent() && dumpStream != null) {
                    ps = new PrintStream(new TeeOutputStream(List.of(buf.orElseThrow(), dumpStream)), true, dumpStream.charset());
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
        }
    }

    private record ToolProviderStreamConfig(CachingPrintStream out, CachingPrintStream err) {
        ToolProviderStreamConfig {
            Objects.requireNonNull(out);
            Objects.requireNonNull(err);
        }

        static ToolProviderStreamConfig create(OutputStreamsControl cfg, boolean redirectErrorStream) {
            CachingPrintStream stdout = cfg.stdout().buildCachingPrintStream(System.out).create();
            CachingPrintStream stderr;
            if (redirectErrorStream) {
                stderr = cfg.stderr().buildCachingPrintStream(System.out).create();
            } else {
                stderr = cfg.stderr().buildCachingPrintStream(System.err).create();
            }
            return new ToolProviderStreamConfig(stdout, stderr);
        }
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

        @Override
        public int hashCode() {
            return Objects.hash(discard, dump, save);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            OutputControl other = (OutputControl) obj;
            return discard == other.discard && dump == other.dump && Objects.equals(save, other.save);
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

        private void forEach(OutputStreamConsumer c) throws IOException {
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

        @FunctionalInterface
        private static interface OutputStreamConsumer {
            void accept(OutputStream out) throws IOException;
        }

        private final Iterable<OutputStream> streams;
    }

    private record CommandOutput(Optional<List<String>> lines, int stdoutLineCount, boolean intertwined) {

        CommandOutput {
            Objects.requireNonNull(lines);
            if (intertwined) {
                stdoutLineCount = lines.map(Collection::size).orElse(-1);
            }
        }

        CommandOutput() {
            this(Optional.empty(), 0, false);
        }

        /**
         * Returns non-empty {@code Optional} if stdout is available and stdout and stderr are not intertwined.
         * @return stdout if it can be extracted from the combined output
         */
        Optional<List<String>> stdoutLines() {
            if (!withUnintertwinedStdout()) {
                return Optional.empty();
            }

            final var theLines = lines.orElseThrow();
            if (stdoutLineCount == theLines.size()) {
                return lines;
            } else {
                return Optional.of(theLines.subList(0, Integer.min(stdoutLineCount, theLines.size())));
            }
        }

        /**
         * Returns non-empty {@code Optional} if stderr is available and stdout and stderr are not intertwined.
         * @return stderr if it can be extracted from the combined output
         */
        Optional<List<String>> stderrLines() {
            if (!withUnintertwinedStderr()) {
                return Optional.empty();
            } else if (stdoutLineCount <= 0) {
                return lines;
            } else {
                final var theLines = lines.orElseThrow();
                return Optional.of(theLines.subList(stdoutLineCount, theLines.size()));
            }
        }

        private boolean withUnintertwinedStdout() {
            return !intertwined && lines.isPresent() && stdoutLineCount >= 0;
        }

        private boolean withUnintertwinedStderr() {
            return !intertwined && lines.isPresent() && stdoutLineCount <= lines.get().size();
        }

        static final CommandOutput EMPTY = new CommandOutput();
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

    private final OutputStreamsControl outputStreamsControl;
    private Charset processOutputCharset;
    private boolean redirectErrorStream;
    private boolean storeStreamsInFiles;

    private static enum OutputControlOption {
        SAVE_ALL, SAVE_FIRST_LINE, DUMP
    }
}
