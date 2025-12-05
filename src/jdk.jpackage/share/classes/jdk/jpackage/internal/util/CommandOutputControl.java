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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * Configures the output streams of a process or a tool provider and executes it.
 * <p>
 * Can save the first line of an output stream, the entire output stream or discard it.
 * <p>
 * Supports separate configurations for stdout and stderr streams.
 */
public final class CommandOutputControl {

    public CommandOutputControl() {
        outputStreamsControl = new OutputStreamsControl();
    }

    private CommandOutputControl(CommandOutputControl other) {
        outputStreamsControl = other.outputStreamsControl.copy();
    }

    /**
     * Configures if entire stdout and stderr streams from the to be executed
     * command should be saved.
     * <p>
     * If <code>v</code> is <code>true</code>, the entire stdout and stderr streams
     * from the to be executed command will be saved; otherwise, they will be discarded.
     * <p>
     * This function is mutually exclusive with {@link #saveFirstLineOfOutput()}.
     *
     * @parameter v if both stdout and stderr streams should be saved
     *
     * @return this
     */
    public CommandOutputControl saveOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.SAVE_ALL);
    }

    /**
     * Configures this instance to save the first line of a stream merged from
     * stdout and stderr streams from the to be executed command.
     * <p>
     * This function is mutually exclusive with {@link #saveOutput(boolean)}.
     *
     * @return this
     */
    public CommandOutputControl saveFirstLineOfOutput() {
        return setOutputControl(true, OutputControlOption.SAVE_FIRST_LINE);
    }

    /**
     * Configures this instance to dump both stdout and stderr streams from the to
     * be executed command into {@link System.out}.
     *
     * @return this
     */
    public CommandOutputControl dumpOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.DUMP);
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
            var task = new FutureTask<Result>(this::execute);
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

    public record ProcessSpec(Optional<Long> pid, List<String> args) implements Executable.ExecutableSpec {
        public ProcessSpec {
            Objects.requireNonNull(pid);
            args.forEach(Objects::requireNonNull);
        }
    }

    public record ToolProviderSpec(String name, List<String> args) implements Executable.ExecutableSpec {
        public ToolProviderSpec {
            Objects.requireNonNull(name);
            args.forEach(Objects::requireNonNull);
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
            return getContent().stream().findFirst();
        }
    }

    public record Result(int exitCode, CommandOutput output, Executable.ExecutableSpec execSpec) implements Output {
        public Result {
            Objects.requireNonNull(output);
        }

        public Result(int exitCode) {
            this(exitCode, CommandOutput.EMPTY, EMPTY_EXECUTABLE_SPEC);
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
            if (!expected.test(exitCode)) {
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

    public Optional<String> description() {
        return outputStreamsControl.describe();
    }

    private Result execute(ProcessBuilder pb)
            throws IOException, InterruptedException {

        outputStreamsControl.applyTo(pb);

        var process = pb.start();

        var pid = getPID(process);

        final var output = combine(
                processProcessStream(outputStreamsControl.stdout(), process.inputReader()),
                processProcessStream(outputStreamsControl.stderr(), process.errorReader()));

        final int exitCode = process.waitFor();

        return new Result(exitCode, output, new ProcessSpec(pid, pb.command()));
    }

    private Result execute(ProcessBuilder pb, long timeout, TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {

        outputStreamsControl.applyTo(pb);

        var process = pb.start();

        var pid = getPID(process);

        var task = new FutureTask<CommandOutput>(() -> {
            return combine(
                    processProcessStream(outputStreamsControl.stdout(), process.inputReader()),
                    processProcessStream(outputStreamsControl.stderr(), process.errorReader()));
        });
        Thread.ofVirtual().start(task);

        if (process.waitFor(timeout, unit)) {
            var exitCode = process.exitValue();
            try {
                final var output = task.get(timeout, unit);
                return new Result(exitCode, output, new ProcessSpec(pid, pb.command()));
            } catch (ExecutionException ex) {
                try {
                    throw ex.getCause();
                } catch (IOException|InterruptedException|TimeoutException|RuntimeException cause) {
                    throw cause;
                } catch (Throwable t) {
                    throw ExceptionBox.rethrowUnchecked(t);
                }
            }
        } else {
            process.destroy();
            throw new TimeoutException();
        }
    }

    private Result execute(ToolProvider tp, String... args) throws IOException {
        final var toolProviderStreamConfig = ToolProviderStreamConfig.create(outputStreamsControl);

        final var exitCode = runToolProvider(toolProviderStreamConfig, tp, args);

        final var output = combine(
                read(outputStreamsControl.stdout(), toolProviderStreamConfig.out()),
                read(outputStreamsControl.stderr(), toolProviderStreamConfig.err()));

        return new Result(exitCode, output, new ToolProviderSpec(tp.name(), List.of(args)));
    }

    private CommandOutputControl setOutputControl(boolean set, OutputControlOption v) {
        outputStreamsControl.stdout().set(set, v);
        outputStreamsControl.stderr().set(set, v);
        return this;
    }

    private static int runToolProvider(ToolProviderStreamConfig cfg, ToolProvider tp, String... args) throws IOException {
        try {
            return tp.run(cfg.out().ps(), cfg.err().ps(), args);
        } finally {
            try {
                cfg.out().ps().flush();
            } finally {
                cfg.err().ps().flush();
            }
        }
    }

    private static Optional<List<String>> processProcessStream(OutputControl outputControl, BufferedReader bufReader) throws IOException {
        List<String> outputLines = null;
        try {
            if (outputControl.dump() || outputControl.saveAll()) {
                outputLines = bufReader.lines().toList();
            } else if (outputControl.saveFirstLine()) {
                outputLines = Optional.ofNullable(bufReader.readLine()).map(List::of).orElseGet(List::of);
                // Read all input, or the started process may exit with an error (cmd.exe does so).
                bufReader.transferTo(Writer.nullWriter());
            } else {
                // This should be empty input stream, fetch it anyway.
                bufReader.transferTo(Writer.nullWriter());
            }
        } finally {
            if (outputControl.dump() && outputLines != null) {
                outputLines.forEach(System.out::println);
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
            } else if (bufferAsString.isPresent()) {
                return Optional.of(List.of());
            } else {
                return Optional.empty();
            }
        }
    }

    private CommandOutput combine(Optional<List<String>> out, Optional<List<String>> err) {
        if (out.isEmpty() && err.isEmpty()) {
            return CommandOutput.EMPTY;
        } else if (out.isEmpty()) {
            return new CommandOutput(err, -1);
        } else if (err.isEmpty()) {
            return new CommandOutput(out, Integer.MAX_VALUE);
        } else {
            final var combined = Stream.of(out, err).map(Optional::orElseThrow).flatMap(List::stream);
            if (outputStreamsControl.stdout().saveFirstLine() && outputStreamsControl.stderr().saveFirstLine()) {
                return new CommandOutput(Optional.of(combined.findFirst().map(List::of).orElseGet(List::of)),
                        Integer.min(1, out.orElseThrow().size()));
            } else {
                return new CommandOutput(Optional.of(combined.toList()), out.orElseThrow().size());
            }
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

        void applyTo(ProcessBuilder pb) {
            pb.redirectOutput(stdout.asProcessBuilderRedirect());
            pb.redirectError(stderr.asProcessBuilderRedirect());
        }

        Optional<String> describe() {
            final List<String> tokens = new ArrayList<>();
            if (stdout.save() || stderr.save()) {
                streamsLabel("save ", true).ifPresent(tokens::add);
            }
            if (stdout.dump() || stderr.dump()) {
                streamsLabel("inherit ", true).ifPresent(tokens::add);
            }
            streamsLabel("discard ", false).ifPresent(tokens::add);
            if (tokens.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(String.join("; ", tokens));
            }
        }

        Optional<String> streamsLabel(String prefix, boolean negate) {
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
                    ps = buf.map(PrintStream::new).or(() -> Optional.ofNullable(dumpStream)).orElseGet(CommandOutputControl::nullPrintStream);
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

        static ToolProviderStreamConfig create(OutputStreamsControl cfg) {
            final var errCfgBuilder = cfg.stderr().buildCachingPrintStream(System.err);
            if (cfg.stderr().dump() && cfg.stderr().save()) {
                errCfgBuilder.dumpStream(System.out);
            }
            return new ToolProviderStreamConfig(
                    cfg.stdout().buildCachingPrintStream(System.out).create(), errCfgBuilder.create());
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

    private record CommandOutput(Optional<List<String>> lines, int stdoutLineCount) {

        CommandOutput {
            Objects.requireNonNull(lines);
        }

        CommandOutput() {
            this(Optional.empty(), 0);
        }

        Optional<List<String>> stdoutLines() {
            if (lines.isEmpty() || stdoutLineCount < 0) {
                return Optional.empty();
            }

            final var theLines = lines.orElseThrow();
            if (stdoutLineCount == theLines.size()) {
                return lines;
            } else {
                return Optional.of(theLines.subList(0, Integer.min(stdoutLineCount, theLines.size())));
            }
        }

        Optional<List<String>> stderrLines() {
            if (lines.isEmpty() || stdoutLineCount > lines.orElseThrow().size()) {
                return Optional.empty();
            } else if (stdoutLineCount == 0) {
                return lines;
            } else {
                final var theLines = lines.orElseThrow();
                return Optional.of(theLines.subList(stdoutLineCount, theLines.size()));
            }
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
            return coc.execute(pb);
        }

        @Override
        public Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
            return coc.execute(pb, timeout, unit);
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

    private static enum OutputControlOption {
        SAVE_ALL, SAVE_FIRST_LINE, DUMP
    }
}
