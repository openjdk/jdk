/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.ExecutableAttributesWithCapturedOutput;
import jdk.jpackage.internal.util.CommandLineFormat;
import jdk.jpackage.internal.util.CommandOutputControl;
import jdk.jpackage.internal.util.CommandOutputControl.ProcessAttributes;
import jdk.jpackage.internal.util.CommandOutputControl.Result;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ExceptionBox;

final class Executor {

    static Executor of(String... cmdline) {
        return of(List.of(cmdline));
    }

    static Executor of(List<String> cmdline) {
        return of(new ProcessBuilder(cmdline));
    }

    static Executor of(ProcessBuilder pb) {
        return Globals.instance().objectFactory().executor().processBuilder(pb);
    }

    public Executor() {
        commandOutputControl = new CommandOutputControl();
        args = new ArrayList<>();
    }

    private Executor(Executor other) {
        commandOutputControl = other.commandOutputControl.copy();
        quietCommand = other.quietCommand;
        args = new ArrayList<>(other.args);
        processBuilder = other.processBuilder;
        toolProvider = other.toolProvider;
        timeout = other.timeout;
        mapper = other.mapper;
    }

    Executor saveOutput(boolean v) {
        commandOutputControl.saveOutput(v);
        return this;
    }

    Executor saveOutput() {
        return saveOutput(true);
    }

    Executor saveFirstLineOfOutput() {
        commandOutputControl.saveFirstLineOfOutput();
        return this;
    }

    Executor charset(Charset v) {
        commandOutputControl.charset(v);
        return this;
    }

    Executor storeOutputInFiles(boolean v) {
        commandOutputControl.storeOutputInFiles(v);
        return this;
    }

    Executor storeOutputInFiles() {
        return storeOutputInFiles(true);
    }

    Executor binaryOutput(boolean v) {
        commandOutputControl.binaryOutput(v);
        return this;
    }

    Executor binaryOutput() {
        return binaryOutput(true);
    }

    Executor discardStdout(boolean v) {
        commandOutputControl.discardStdout(v);
        return this;
    }

    Executor discardStdout() {
        return discardStdout(true);
    }

    Executor discardStderr(boolean v) {
        commandOutputControl.discardStderr(v);
        return this;
    }

    Executor discardStderr() {
        return discardStderr(true);
    }

    Executor timeout(long v, TimeUnit unit) {
        return timeout(Duration.of(v, unit.toChronoUnit()));
    }

    Executor timeout(Duration v) {
        timeout = v;
        return this;
    }

    Executor toolProvider(ToolProvider v) {
        toolProvider = Objects.requireNonNull(v);
        processBuilder = null;
        return this;
    }

    Optional<ToolProvider> toolProvider() {
        return Optional.ofNullable(toolProvider);
    }

    Executor processBuilder(ProcessBuilder v) {
        processBuilder = Objects.requireNonNull(v);
        toolProvider = null;
        return this;
    }

    Optional<ProcessBuilder> processBuilder() {
        return Optional.ofNullable(processBuilder);
    }

    Executor args(List<String> v) {
        args.addAll(v);
        return this;
    }

    Executor args(String... args) {
        return args(List.of(args));
    }

    List<String> args() {
        return args;
    }

    Executor setQuiet(boolean v) {
        quietCommand = v;
        return this;
    }

    Executor mapper(UnaryOperator<Executor> v) {
        mapper = v;
        return this;
    }

    Optional<UnaryOperator<Executor>> mapper() {
        return Optional.ofNullable(mapper);
    }

    Executor copy() {
        return new Executor(this);
    }

    Result execute() throws IOException {
        if (mapper != null) {
            var mappedExecutor = Objects.requireNonNull(mapper.apply(this));
            if (mappedExecutor != this) {
                return mappedExecutor.execute();
            }
        }

        var coc = commandOutputControl.copy();

        final CommandOutputControl.Executable exec;
        if (processBuilder != null) {
            exec = coc.createExecutable(copyProcessBuilder());
        } else if (toolProvider != null) {
            exec = coc.createExecutable(toolProvider, args.toArray(String[]::new));
        } else {
            throw new IllegalStateException("No target to execute");
        }

        if (dumpOutput()) {
            Log.verbose(String.format("Running %s", CommandLineFormat.DEFAULT.apply(List.of(commandLine().getFirst()))));
        }

        var printableOutputBuilder = new PrintableOutputBuilder(coc);
        Result result;
        try {
            if (timeout == null) {
                result = exec.execute();
            } else {
                result = exec.execute(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            throw ExceptionBox.toUnchecked(ex);
        }

        var printableOutput = printableOutputBuilder.create();
        if (dumpOutput()) {
            log(result, printableOutput);
        }

        return ExecutableAttributesWithCapturedOutput.augmentResultWithOutput(result, printableOutput);
    }

    Result executeExpectSuccess() throws IOException {
        return execute().expectExitCode(0);
    }

    Result executeExpect(int mainExitCode, int... otherExitCodes) throws IOException {
        return execute().expectExitCode(mainExitCode, otherExitCodes);
    }

    RetryExecutor<Result, IOException> retry() {
        return Globals.instance().objectFactory().<Result, IOException>retryExecutor(IOException.class)
                .setExecutable(this::executeExpectSuccess);
    }

    RetryExecutor<Result, IOException> retryOnKnownErrorMessage(String msg) {
        Objects.requireNonNull(msg);
        return saveOutput().retry().setExecutable(() -> {
            // Execute it without exit code check.
            var result = execute();
            if (result.stderr().stream().anyMatch(msg::equals)) {
                throw result.unexpected();
            }
            return result;
        });
    }

    List<String> commandLine() {
        if (processBuilder != null) {
            return Stream.of(processBuilder.command(), args).flatMap(Collection::stream).toList();
        } else if (toolProvider != null) {
            return Stream.concat(Stream.of(toolProvider.name()), args.stream()).toList();
        } else {
            throw new IllegalStateException("No target to execute");
        }
    }

    private ProcessBuilder copyProcessBuilder() {
        if (processBuilder == null) {
            throw new IllegalStateException();
        }

        var copy = new ProcessBuilder(commandLine());
        copy.directory(processBuilder.directory());
        var env = copy.environment();
        env.clear();
        env.putAll(processBuilder.environment());

        return copy;
    }

    private boolean dumpOutput() {
        return Log.isVerbose() && !quietCommand;
    }

    private static void log(Result result, String printableOutput) throws IOException {
        Objects.requireNonNull(result);
        Objects.requireNonNull(printableOutput);

        Optional<Long> pid;
        if (result.execAttrs() instanceof ProcessAttributes attrs) {
            pid = attrs.pid();
        } else {
            pid = Optional.empty();
        }

        var sb = new StringBuilder();
        sb.append("Command");
        pid.ifPresent(p -> {
            sb.append(" [PID: ").append(p).append("]");
        });
        sb.append(":\n    ").append(result.execAttrs().printableCommandLine());
        Log.verbose(sb.toString());

        if (!printableOutput.isEmpty()) {
            sb.delete(0, sb.length());
            sb.append("Output:");
            try (var lines = new BufferedReader(new StringReader(printableOutput)).lines()) {
                lines.forEach(line -> {
                    sb.append("\n    ").append(line);
                });
            }
            Log.verbose(sb.toString());
        }

        result.exitCode().ifPresentOrElse(exitCode -> {
            Log.verbose("Returned: " + exitCode + "\n");
        }, () -> {
            Log.verbose("Aborted: timed-out" + "\n");
        });
    }

    private static final class PrintableOutputBuilder {

        PrintableOutputBuilder(CommandOutputControl coc) {
            coc.dumpOutput(true);
            charset = coc.charset();
            if (coc.isBinaryOutput()) {
                // Assume binary output goes into stdout and text error messages go into stderr, so keep them separated.
                sinks = new ByteArrayOutputStream[2];
                sinks[0] = new ByteArrayOutputStream();
                sinks[1] = new ByteArrayOutputStream();
                coc.dumpStdout(new PrintStream(sinks[0], false, charset))
                    .dumpStderr(new PrintStream(sinks[1], false, charset));
            } else {
                sinks = new ByteArrayOutputStream[1];
                sinks[0] = new ByteArrayOutputStream();
                var ps = new PrintStream(sinks[0], false, charset);
                // Redirect stderr in stdout.
                coc.dumpStdout(ps).dumpStderr(ps);
            }
        }

        String create() {
            if (isBinaryOutput()) {
                // In case of binary output:
                //  - Convert binary stdout to text using ISO-8859-1 encoding and
                //    replace non-printable characters with the question mark symbol (?).
                //  - Convert binary stderr to text using designated encoding (assume stderr is always a character stream).
                //  - Merge text stdout and stderr into a single string;
                //    stderr first, stdout follows, with the aim to present user error messages first.
                var sb = new StringBuilder();
                var stdout = sinks[0].toString(StandardCharsets.ISO_8859_1).replaceAll("[^\\p{Print}\\p{Space}]", "?");
                return sb.append(sinks[1].toString(charset)).append(stdout).toString();
            } else {
                return sinks[0].toString(charset);
            }
        }

        private boolean isBinaryOutput() {
            return sinks.length == 2;
        }

        private final ByteArrayOutputStream sinks[];
        private final Charset charset;
    }

    private final CommandOutputControl commandOutputControl;
    private boolean quietCommand;
    private final List<String> args;
    private ProcessBuilder processBuilder;
    private ToolProvider toolProvider;
    private Duration timeout;
    private UnaryOperator<Executor> mapper;
}
