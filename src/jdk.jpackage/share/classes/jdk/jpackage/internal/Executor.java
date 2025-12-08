/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.CommandLineFormat;
import jdk.jpackage.internal.util.CommandOutputControl;
import jdk.jpackage.internal.util.CommandOutputControl.ProcessSpec;
import jdk.jpackage.internal.util.CommandOutputControl.Result;
import jdk.jpackage.internal.util.function.ExceptionBox;

final class Executor {

    static Executor of(String... cmdline) {
        return of(List.of(cmdline));
    }

    static Executor of(List<String> cmdline) {
        return of(new ProcessBuilder(cmdline));
    }

    static Executor of(ProcessBuilder pb) {
        return new Executor().processBuilder(pb);
    }

    private Executor() {
    }

    Executor saveOutput(boolean v) {
        commandOutputControl.saveOutput(v);
        return this;
    }

    Executor saveOutput() {
        return saveOutput(true);
    }

    public Executor saveFirstLineOfOutput() {
        commandOutputControl.saveFirstLineOfOutput();
        return this;
    }

    Executor processOutputCharset(Charset v) {
        commandOutputControl.processOutputCharset(v);
        return this;
    }

    Executor redirectErrorStream(boolean v) {
        commandOutputControl.redirectErrorStream(v);
        return this;
    }

    Executor redirectErrorStream() {
        return redirectErrorStream(true);
    }

    Executor storeStreamsInFiles(boolean v) {
        commandOutputControl.storeStreamsInFiles(v);
        return this;
    }

    Executor storeStreamsInFiles() {
        return storeStreamsInFiles(true);
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
        timeout = Duration.of(v, unit.toChronoUnit());
        return this;
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

    Executor processBuilder(ProcessBuilder v) {
        processBuilder = Objects.requireNonNull(v);
        toolProvider = null;
        return this;
    }

    Executor args(List<String> v) {
        args.addAll(v);
        return this;
    }

    Executor args(String... args) {
        return args(List.of(args));
    }

    Executor setQuiet(boolean v) {
        quietCommand = v;
        return this;
    }

    Result execute() throws IOException {
        final CommandOutputControl.Executable exec;

        final CommandOutputControl coc;
        if (quietCommand) {
            coc = commandOutputControl;
        } else {
            coc = commandOutputControl.copy().saveOutput(true);
        }

        if (processBuilder != null) {
            exec = coc.createExecutable(copyProcessBuilder());
        } else if (toolProvider != null) {
            exec = coc.createExecutable(toolProvider, args.toArray(String[]::new));
        } else {
            throw new IllegalStateException("No target to execute");
        }

        if (!quietCommand) {
            Log.verbose(String.format("Running %s", CommandLineFormat.DEFAULT.apply(List.of(commandLine().getFirst()))));
        }

        Result result;
        try {
            if (timeout == null) {
                result = exec.execute();
            } else {
                try {
                    result = exec.execute(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException tex) {
                    var msg = String.format("Command %s timed out", CommandLineFormat.DEFAULT.apply(commandLine()));
                    throw new IOException(msg, tex);
                }
            }
        } catch (InterruptedException ex) {
            throw ExceptionBox.rethrowUnchecked(ex);
        }

        if (!quietCommand) {
            log(result);
        }

        return result;
    }

    Result executeExpectSuccess() throws IOException {
        return execute().expectExitCode(0);
    }

    private List<String> commandLine() {
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

    private static void log(Result result) {
        Objects.requireNonNull(result);
        if (Log.isVerbose()) {
            Optional<Long> pid;
            if (result.execSpec() instanceof ProcessSpec spec) {
                pid = spec.pid();
            } else {
                pid = Optional.empty();
            }

            var sb = new StringBuilder();
            sb.append("Command");
            pid.ifPresent(p -> {
                sb.append(" [PID: ").append(p).append("]");
            });
            sb.append(":\n    ").append(result.execSpec());
            Log.verbose(sb.toString());

            result.findContent().filter(Predicate.not(Collection::isEmpty)).ifPresent(output -> {
                sb.delete(0, sb.length());
                sb.append("Output:");
                for (String s : output) {
                    sb.append("\n    " + s);
                }
                Log.verbose(sb.toString());
            });

            Log.verbose("Returned: " + result.exitCode() + "\n");
        }
    }

    private final CommandOutputControl commandOutputControl = new CommandOutputControl();
    private boolean quietCommand;
    private List<String> args = new ArrayList<>();
    private ProcessBuilder processBuilder;
    private ToolProvider toolProvider;
    private Duration timeout;
}
