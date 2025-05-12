/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

public final class Executor extends CommandArguments<Executor> {

    public static Executor of(String... cmdline) {
        return of(List.of(cmdline));
    }

    public static Executor of(List<String> cmdline) {
        cmdline.forEach(Objects::requireNonNull);
        return new Executor().setExecutable(cmdline.getFirst()).addArguments(cmdline.subList(1, cmdline.size()));
    }

    public static Executor of(ToolProvider toolProvider, String... args) {
        return new Executor().setToolProvider(toolProvider).addArguments(List.of(args));
    }

    public Executor() {
        outputStreamsControl = new OutputStreamsControl();
        winEnglishOutput = false;
    }

    public Executor setExecutable(String v) {
        return setExecutable(Path.of(v));
    }

    public Executor setExecutable(Path v) {
        executable = Objects.requireNonNull(v);
        toolProvider = null;
        return this;
    }

    public Executor setToolProvider(ToolProvider v) {
        toolProvider = Objects.requireNonNull(v);
        executable = null;
        return this;
    }

    public Executor setToolProvider(JavaTool v) {
        return setToolProvider(v.asToolProvider());
    }

    public Optional<Path> getExecutable() {
        return Optional.ofNullable(executable);
    }

    public Executor setDirectory(Path v) {
        directory = v;
        return this;
    }

    public Executor setExecutable(JavaTool v) {
        return setExecutable(v.getPath());
    }

    public Executor removeEnvVar(String envVarName) {
        removeEnvVars.add(Objects.requireNonNull(envVarName));
        setEnvVars.remove(envVarName);
        return this;
    }

    public Executor setEnvVar(String envVarName, String envVarValue) {
        setEnvVars.put(Objects.requireNonNull(envVarName), Objects.requireNonNull(envVarValue));
        removeEnvVars.remove(envVarName);
        return this;
    }

    public Executor setWinRunWithEnglishOutput(boolean value) {
        if (!TKit.isWindows()) {
            throw new UnsupportedOperationException(
                    "setWinRunWithEnglishOutput is only valid on Windows platform");
        }
        winEnglishOutput = value;
        return this;
    }

    public Executor setWindowsTmpDir(String tmp) {
        if (!TKit.isWindows()) {
            throw new UnsupportedOperationException(
                    "setWindowsTmpDir is only valid on Windows platform");
        }
        winTmpDir = tmp;
        return this;
    }

    /**
     * Configures this instance to save all stdout and stderr streams from the to be
     * executed command.
     * <p>
     * This function is mutually exclusive with {@link #saveFirstLineOfOutput()}.
     *
     * @return this
     */
    public Executor saveOutput() {
        return saveOutput(true);
    }

    /**
     * Configures if all stdout and stderr streams from the to be executed command
     * should be saved.
     * <p>
     * If <code>v</code> is <code>true</code>, the function call is equivalent to
     * {@link #saveOutput()} call. If <code>v</code> is <code>false</code>, command
     * output will not be saved.
     *
     * @parameter v if both stdout and stderr streams should be saved
     *
     * @return this
     */
    public Executor saveOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.SAVE_ALL);
    }

    /**
     * Configures this instance to save the first line of a stream merged from
     * stdout and stderr streams from the to be executed command.
     * <p>
     * This function is mutually exclusive with {@link #saveOutput()}.
     *
     * @return this
     */
    public Executor saveFirstLineOfOutput() {
        return setOutputControl(true, OutputControlOption.SAVE_FIRST_LINE);
    }

    /**
     * Configures this instance to dump both stdout and stderr streams from the to
     * be executed command into {@link System.out}.
     *
     * @return this
     */
    public Executor dumpOutput() {
        return dumpOutput(true);
    }

    public Executor dumpOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.DUMP);
    }

    public Executor discardStdout(boolean v) {
        outputStreamsControl.stdout().discard(v);
        return this;
    }

    public Executor discardStdout() {
        return discardStdout(true);
    }

    public Executor discardStderr(boolean v) {
        outputStreamsControl.stderr().discard(v);
        return this;
    }

    public Executor discardStderr() {
        return discardStderr(true);
    }

    public interface Output {
        public List<String> getOutput();

        public default String getFirstLineOfOutput() {
            return findFirstLineOfOutput().orElseThrow();
        }

        public default Optional<String> findFirstLineOfOutput() {
            return getOutput().stream().findFirst();
        }
    }

    public record Result(int exitCode, CommandOutput output, Supplier<String> cmdline) implements Output {
        public Result {
            Objects.requireNonNull(output);
            Objects.requireNonNull(cmdline);
        }

        public Result(int exitCode, Supplier<String> cmdline) {
            this(exitCode, CommandOutput.EMPTY, cmdline);
        }

        @Override
        public List<String> getOutput() {
            return output.lines().orElse(null);
        }

        public Output stdout() {
            return createView(output.stdoutLines());
        }

        public Output stderr() {
            return createView(output.stderrLines());
        }

        public Result assertExitCodeIs(int expectedExitCode) {
            TKit.assertEquals(expectedExitCode, exitCode, String.format(
                    "Check command %s exited with %d code",
                    cmdline.get(), expectedExitCode));
            return this;
        }

        public Result assertExitCodeIsZero() {
            return assertExitCodeIs(0);
        }

        public int getExitCode() {
            return exitCode;
        }

        private static Output createView(Optional<List<String>> lines) {
            return new Output() {
                @Override
                public List<String> getOutput() {
                    return lines.orElse(null);
                }
            };
        }
    }

    public Result executeWithoutExitCodeCheck() {
        if (toolProvider != null && directory != null) {
            throw new IllegalArgumentException(
                    "Can't change directory when using tool provider");
        }

        if (toolProvider != null && winEnglishOutput) {
            throw new IllegalArgumentException(
                    "Can't change locale when using tool provider");
        }

        return ThrowingSupplier.toSupplier(() -> {
            if (toolProvider != null) {
                return runToolProvider();
            }

            if (executable != null) {
                return runExecutable();
            }

            throw new IllegalStateException("No command to execute");
        }).get();
    }

    public Result execute(int expectedCode) {
        return executeWithoutExitCodeCheck().assertExitCodeIs(expectedCode);
    }

    public Result execute() {
        return execute(0);
    }

    public String executeAndGetFirstLineOfOutput() {
        return saveFirstLineOfOutput().execute().getFirstLineOfOutput();
    }

    public List<String> executeAndGetOutput() {
        return saveOutput().execute().getOutput();
    }

    private static class BadResultException extends RuntimeException {
        BadResultException(Result v) {
            value = v;
        }

        Result getValue() {
            return value;
        }

        private final transient Result value;
        private static final long serialVersionUID = 1L;
    }

    /**
     * Executes the configured command {@code max} at most times and waits for
     * {@code wait} seconds between each execution until the command exits with
     * {@code expectedCode} exit code.
     *
     * @param expectedExitCode the expected exit code of the command
     * @param max              the maximum times to execute the command
     * @param wait             number of seconds to wait between executions of the
     *                         command
     */
    public Result executeAndRepeatUntilExitCode(int expectedExitCode, int max, int wait) {
        try {
            return tryRunMultipleTimes(() -> {
                Result result = executeWithoutExitCodeCheck();
                if (result.getExitCode() != expectedExitCode) {
                    throw new BadResultException(result);
                }
                return result;
            }, max, wait).assertExitCodeIs(expectedExitCode);
        } catch (BadResultException ex) {
            return ex.getValue().assertExitCodeIs(expectedExitCode);
        }
    }

    /**
     * Calls {@code task.get()} at most {@code max} times and waits for {@code wait}
     * seconds between each call until {@code task.get()} invocation returns without
     * throwing {@link RuntimeException} exception.
     * <p>
     * Returns the object returned by the first {@code task.get()} invocation that
     * didn't throw an exception or rethrows the last exception if all of
     * {@code max} attempts ended in exception being thrown.
     *
     * @param task the object of which to call {@link Supplier#get()} function
     * @param max  the maximum times to execute the command
     * @param wait number of seconds to wait between executions of the
     */
    public static <T> T tryRunMultipleTimes(Supplier<T> task, int max, int wait) {
        RuntimeException lastException = null;
        int count = 0;

        do {
            try {
                return task.get();
            } catch (RuntimeException ex) {
                lastException = ex;
            }

            try {
                Thread.sleep(wait * 1000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            count++;
        } while (count < max);

        throw lastException;
    }

    public static void tryRunMultipleTimes(Runnable task, int max, int wait) {
        tryRunMultipleTimes(() -> {
            task.run();
            return null;
        }, max, wait);
    }

    public List<String> executeWithoutExitCodeCheckAndGetOutput() {
        return saveOutput().executeWithoutExitCodeCheck().getOutput();
    }

    private Executor setOutputControl(boolean set, OutputControlOption v) {
        outputStreamsControl.stdout().set(set, v);
        outputStreamsControl.stderr().set(set, v);
        return this;
    }

    private Path executablePath() {
        if (directory == null
                || executable.isAbsolute()
                || !Set.of(".", "..").contains(executable.getName(0).toString())) {
            return executable;
        }

        // If relative path to executable is used it seems to be broken when
        // ProcessBuilder changes the directory. On Windows it changes the
        // directory first and on Linux it looks up for executable before
        // changing the directory. Use absolute path to executable to play
        // it safely on all platforms.
        return executable.toAbsolutePath();
    }

    private List<String> prefixCommandLineArgs() {
        if (winEnglishOutput) {
            return List.of("cmd.exe", "/c", "chcp", "437", ">nul", "2>&1", "&&");
        } else {
            return List.of();
        }
    }

    private Result runExecutable() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.addAll(prefixCommandLineArgs());
        command.add(executablePath().toString());
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        if (winTmpDir != null) {
            builder.environment().put("TMP", winTmpDir);
        }

        outputStreamsControl.applyTo(builder);

        StringBuilder sb = new StringBuilder(getPrintableCommandLine());
        outputStreamsControl.describe().ifPresent(desc -> {
            sb.append("; ").append(desc);
        });

        if (directory != null) {
            builder.directory(directory.toFile());
            sb.append(String.format("; in directory [%s]", directory));
        }
        if (!setEnvVars.isEmpty()) {
            final var defaultEnv = builder.environment();
            final var envComm = Comm.compare(defaultEnv.keySet(), setEnvVars.keySet());
            envComm.unique2().forEach(envVar -> {
                trace(String.format("Adding %s=[%s] to environment", envVar, setEnvVars.get(envVar)));
            });
            envComm.common().forEach(envVar -> {
                final var curValue = defaultEnv.get(envVar);
                final var newValue = setEnvVars.get(envVar);
                if (!curValue.equals(newValue)) {
                    trace(String.format("Setting %s=[%s] in environment", envVar, setEnvVars.get(envVar)));
                }
            });
            defaultEnv.putAll(setEnvVars);
        }
        if (!removeEnvVars.isEmpty()) {
            final var defaultEnv = builder.environment().keySet();
            final var envComm = Comm.compare(defaultEnv, removeEnvVars);
            defaultEnv.removeAll(envComm.common());
            envComm.common().forEach(envVar -> {
                trace(String.format("Clearing %s in environment", envVar));
            });
        }

        trace("Execute " + sb.toString() + "...");
        Process process = builder.start();

        final var output = combine(
                processProcessStream(outputStreamsControl.stdout(), process.getInputStream()),
                processProcessStream(outputStreamsControl.stderr(), process.getErrorStream()));

        final int exitCode = process.waitFor();
        trace("Done. Exit code: " + exitCode);

        return createResult(exitCode, output);
    }

    private int runToolProvider(PrintStream out, PrintStream err) {
        final var sb = new StringBuilder(getPrintableCommandLine());
        outputStreamsControl.describe().ifPresent(desc -> {
            sb.append("; ").append(desc);
        });
        trace("Execute " + sb + "...");
        final int exitCode = toolProvider.run(out, err, args.toArray(
                String[]::new));
        trace("Done. Exit code: " + exitCode);
        return exitCode;
    }

    private Result runToolProvider() throws IOException {
        final var toolProviderStreamConfig = ToolProviderStreamConfig.create(outputStreamsControl);

        final var exitCode = runToolProvider(toolProviderStreamConfig);

        final var output = combine(
                read(outputStreamsControl.stdout(), toolProviderStreamConfig.out()),
                read(outputStreamsControl.stderr(), toolProviderStreamConfig.err()));
        return createResult(exitCode, output);
    }

    private int runToolProvider(ToolProviderStreamConfig cfg) throws IOException {
        try {
            return runToolProvider(cfg.out().ps(), cfg.err().ps());
        } finally {
            cfg.out().ps().flush();
            cfg.err().ps().flush();
        }
    }

    private static Optional<List<String>> processProcessStream(OutputControl outputControl, InputStream in) throws IOException {
        List<String> outputLines = null;
        try (final var bufReader = new BufferedReader(new InputStreamReader(in))) {
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
            return new CommandOutput();
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

    private Result createResult(int exitCode, CommandOutput output) {
        return new Result(exitCode, output, this::getPrintableCommandLine);
    }

    public String getPrintableCommandLine() {
        final String exec;
        String format = "[%s](%d)";
        if (toolProvider == null && executable == null) {
            exec = "<null>";
        } else if (toolProvider != null) {
            format = "tool provider " + format;
            exec = toolProvider.name();
        } else {
            exec = executablePath().toString();
        }

        var cmdline = Stream.of(prefixCommandLineArgs(), List.of(exec), args).flatMap(
                List::stream).toList();

        return String.format(format, printCommandLine(cmdline), cmdline.size());
    }

    private static String printCommandLine(List<String> cmdline) {
        // Want command line printed in a way it can be easily copy/pasted
        // to be executed manually
        Pattern regex = Pattern.compile("\\s");
        return cmdline.stream().map(
                v -> (v.isEmpty() || regex.matcher(v).find()) ? "\"" + v + "\"" : v).collect(
                        Collectors.joining(" "));
    }

    private static void trace(String msg) {
        TKit.trace(String.format("exec: %s", msg));
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
                    ps = buf.map(PrintStream::new).or(() -> Optional.ofNullable(dumpStream)).orElseGet(Executor::nullPrintStream);
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

    private static final class CommandOutput {
        CommandOutput(Optional<List<String>> lines, int stdoutLineCount) {
            this.lines = Objects.requireNonNull(lines);
            this.stdoutLineCount = stdoutLineCount;
        }

        CommandOutput() {
            this(Optional.empty(), 0);
        }

        Optional<List<String>> lines() {
            return lines;
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

        private final Optional<List<String>> lines;
        private final int stdoutLineCount;

        static final CommandOutput EMPTY = new CommandOutput();
    }

    private ToolProvider toolProvider;
    private Path executable;
    private OutputStreamsControl outputStreamsControl;
    private Path directory;
    private Set<String> removeEnvVars = new HashSet<>();
    private Map<String, String> setEnvVars = new HashMap<>();
    private boolean winEnglishOutput;
    private String winTmpDir = null;

    private static enum OutputControlOption {
        SAVE_ALL, SAVE_FIRST_LINE, DUMP
    }
}
