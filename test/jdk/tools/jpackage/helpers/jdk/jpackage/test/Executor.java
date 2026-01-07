/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.CommandLineFormat;
import jdk.jpackage.internal.util.CommandOutputControl;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedExitCodeException;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ExceptionBox;
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

    public Executor saveOutput() {
        return saveOutput(true);
    }

    public Executor saveOutput(boolean v) {
        commandOutputControl.saveOutput(v);
        return this;
    }

    public Executor saveFirstLineOfOutput() {
        commandOutputControl.saveFirstLineOfOutput();
        return this;
    }

    public Executor dumpOutput() {
        return dumpOutput(true);
    }

    public Executor dumpOutput(boolean v) {
        commandOutputControl.dumpOutput(v);
        return this;
    }

    public Executor discardStdout(boolean v) {
        commandOutputControl.discardStdout(v);
        return this;
    }

    public Executor discardStdout() {
        return discardStdout(true);
    }

    public Executor discardStderr(boolean v) {
        commandOutputControl.discardStderr(v);
        return this;
    }

    public Executor discardStderr() {
        return discardStderr(true);
    }

    public Executor binaryOutput(boolean v) {
        commandOutputControl.binaryOutput(v);
        return this;
    }

    public Executor binaryOutput() {
        return binaryOutput(true);
    }

    public Executor charset(Charset v) {
        commandOutputControl.charset(v);
        return this;
    }

    public Charset charset() {
        return commandOutputControl.charset();
    }

    Executor storeOutputInFiles(boolean v) {
        commandOutputControl.storeOutputInFiles(v);
        return this;
    }

    Executor storeOutputInFiles() {
        return storeOutputInFiles(true);
    }

    public record Result(CommandOutputControl.Result base) {
        public Result {
            Objects.requireNonNull(base);
        }

        public Result(int exitCode) {
            this(new CommandOutputControl.Result(exitCode));
        }

        public List<String> getOutput() {
            return base.content();
        }

        public String getFirstLineOfOutput() {
            return getOutput().getFirst();
        }

        public List<String> stdout() {
            return base.stdout();
        }

        public List<String> stderr() {
            return base.stderr();
        }

        public Optional<List<String>> findContent() {
            return base.findContent();
        }

        public Optional<List<String>> findStdout() {
            return base.findStdout();
        }

        public Optional<List<String>> findStderr() {
            return base.findStderr();
        }

        public byte[] byteContent() {
            return base.byteContent();
        }

        public byte[] byteStdout() {
            return base.byteStdout();
        }

        public byte[] byteStderr() {
            return base.byteStderr();
        }

        public Optional<byte[]> findByteContent() {
            return base.findByteContent();
        }

        public Optional<byte[]> findByteStdout() {
            return base.findByteStdout();
        }

        public Optional<byte[]> findByteStderr() {
            return base.findByteStderr();
        }

        public Result toCharacterResult(Charset charset, boolean keepByteContent) {
            try {
                return new Result(base.toCharacterResult(charset, keepByteContent));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        public Result assertExitCodeIs(int main, int... other) {
            if (other.length != 0) {
                return assertExitCodeIs(IntStream.concat(IntStream.of(main), IntStream.of(other)).boxed().toList());
            } else {
                return assertExitCodeIs(List.of(main));
            }
        }

        private Result assertExitCodeIs(List<Integer> expectedExitCodes) {
            Objects.requireNonNull(expectedExitCodes);
            switch (expectedExitCodes.size()) {
                case 0 -> {
                    throw new IllegalArgumentException();
                } case 1 -> {
                    long expectedExitCode = expectedExitCodes.getFirst();
                    TKit.assertEquals(expectedExitCode, getExitCode(), String.format(
                            "Check command %s exited with %d code",
                            base.execAttrs(), expectedExitCode));
                } default -> {
                    TKit.assertTrue(expectedExitCodes.contains(getExitCode()), String.format(
                            "Check command %s exited with one of %s codes",
                            base.execAttrs(), expectedExitCodes.stream().sorted().toList()));
                }
            }
            return this;
        }

        public Result assertExitCodeIsZero() {
            return assertExitCodeIs(0);
        }

        public int getExitCode() {
            return base.getExitCode();
        }

        public String getPrintableCommandLine() {
            return base.execAttrs().toString();
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

    Result execute(int mainExitCode, int... otherExitCodes) {
        return executeWithoutExitCodeCheck().assertExitCodeIs(mainExitCode, otherExitCodes);
    }

    public Result execute() {
        return execute(0);
    }

    public String executeAndGetFirstLineOfOutput() {
        return saveFirstLineOfOutput().execute().getOutput().getFirst();
    }

    public List<String> executeAndGetOutput() {
        return saveOutput().execute().getOutput();
    }

    private static class FailedAttemptException extends Exception {
        FailedAttemptException(Exception cause) {
            super(Objects.requireNonNull(cause));
        }

        private static final long serialVersionUID = 1L;
    }

    public RetryExecutor<Result, UnexpectedExitCodeException> retryUntilExitCodeIs(
            int mainExpectedExitCode, int... otherExpectedExitCodes) {
        return new RetryExecutor<Result, UnexpectedExitCodeException>(UnexpectedExitCodeException.class).setExecutable(() -> {
            var result = executeWithoutExitCodeCheck();
            result.base().expectExitCode(mainExpectedExitCode, otherExpectedExitCodes);
            return result;
        }).setExceptionMapper((UnexpectedExitCodeException ex) -> {
            createResult(ex.getResult()).assertExitCodeIs(mainExpectedExitCode, otherExpectedExitCodes);
            // Unreachable, because the above `Result.assertExitCodeIs(...)` must throw.
            throw ExceptionBox.reachedUnreachable();
        });
    }

    /**
     * Executes the configured command at most {@code max} times and waits for
     * {@code wait} seconds between each execution until the command exits with
     * {@code expectedCode} exit code.
     *
     * @param expectedExitCode the expected exit code of the command
     * @param max              the maximum times to execute the command
     * @param wait             number of seconds to wait between executions of the
     *                         command
     */
    public Result executeAndRepeatUntilExitCode(int expectedExitCode, int max, int wait) {
        return retryUntilExitCodeIs(expectedExitCode)
                .setAttemptTimeout(wait, TimeUnit.SECONDS)
                .setMaxAttemptsCount(max)
                .executeUnchecked();
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
        return new RetryExecutor<T, FailedAttemptException>(FailedAttemptException.class).setExecutable(() -> {
            try {
                return task.get();
            } catch (RuntimeException ex) {
                throw new FailedAttemptException(ex);
            }
        }).setExceptionMapper((FailedAttemptException ex) -> {
            return (RuntimeException)ex.getCause();
        }).setAttemptTimeout(wait, TimeUnit.SECONDS).setMaxAttemptsCount(max).executeUnchecked();

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

        StringBuilder sb = new StringBuilder(getPrintableCommandLine());
        sb.append("; ").append(commandOutputControl.description());

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

        return execute(sb, commandOutputControl.createExecutable(builder));
    }

    private Result runToolProvider() throws IOException, InterruptedException {
        final var sb = new StringBuilder(getPrintableCommandLine());
        sb.append("; ").append(commandOutputControl.description());

        return execute(sb, commandOutputControl.createExecutable(toolProvider, args.toArray(String[]::new)));
    }

    private Result execute(StringBuilder traceMsg, CommandOutputControl.Executable exec) throws IOException, InterruptedException {
        Objects.requireNonNull(traceMsg);

        trace("Execute " + traceMsg + "...");

        var result = exec.execute();

        trace("Done. Exit code: " + result.getExitCode());

        return createResult(result);
    }

    private Result createResult(CommandOutputControl.Result baseResult) {
        return new Result(baseResult.copyWithExecutableAttributes(
                new ExecutableAttributes(baseResult.execAttrs(), getPrintableCommandLine())));
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

        return String.format(format, CommandLineFormat.DEFAULT.apply(cmdline), cmdline.size());
    }

    private record ExecutableAttributes(CommandOutputControl.ExecutableAttributes base, String toStringValue)
            implements CommandOutputControl.ExecutableAttributes {

        ExecutableAttributes {
            Objects.requireNonNull(base);
            if (toStringValue.isBlank()) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return toStringValue;
        }

        @Override
        public List<String> commandLine() {
            return base.commandLine();
        }
    }

    private static void trace(String msg) {
        TKit.trace(String.format("exec: %s", msg));
    }

    private ToolProvider toolProvider;
    private Path executable;
    private final CommandOutputControl commandOutputControl = new CommandOutputControl();
    private Path directory;
    private Set<String> removeEnvVars = new HashSet<>();
    private Map<String, String> setEnvVars = new HashMap<>();
    private boolean winEnglishOutput;
    private String winTmpDir = null;
}
