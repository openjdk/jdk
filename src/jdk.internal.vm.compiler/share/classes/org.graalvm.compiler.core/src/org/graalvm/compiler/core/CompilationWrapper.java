/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core;

import static org.graalvm.compiler.core.CompilationWrapper.ExceptionAction.ExitVM;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAsFailure;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.core.GraalCompilerOptions.ExitVMOnException;
import static org.graalvm.compiler.core.GraalCompilerOptions.MaxCompilationProblemsPerAction;
import static org.graalvm.compiler.core.common.GraalOptions.TrackNodeSourcePosition;
import static org.graalvm.compiler.debug.DebugOptions.Dump;
import static org.graalvm.compiler.debug.DebugOptions.DumpPath;
import static org.graalvm.compiler.debug.DebugOptions.MethodFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BailoutException;

/**
 * Wrapper for a compilation that centralizes what action to take based on
 * {@link GraalCompilerOptions#CompilationBailoutAsFailure} and
 * {@link GraalCompilerOptions#CompilationFailureAction} when an uncaught exception occurs during
 * compilation.
 */
public abstract class CompilationWrapper<T> {

    /**
     * Actions to take upon an exception being raised during compilation performed via
     * {@link CompilationWrapper}. The actions are with respect to what the user sees on the
     * console. The compilation requester determines what ultimate action is taken in
     * {@link CompilationWrapper#handleException(Throwable)}.
     *
     * The actions are in ascending order of verbosity.
     */
    public enum ExceptionAction {
        /**
         * Print nothing to the console.
         */
        Silent,

        /**
         * Print a stack trace to the console.
         */
        Print,

        /**
         * An exception causes the compilation to be retried with extra diagnostics enabled.
         */
        Diagnose,

        /**
         * Same as {@link #Diagnose} except that the VM process is exited after retrying.
         */
        ExitVM;

        private static final ExceptionAction[] VALUES = values();

        /**
         * Gets the action that is one level less verbose than this action, bottoming out at the
         * least verbose action.
         */
        ExceptionAction quieter() {
            assert ExceptionAction.Silent.ordinal() == 0;
            int index = Math.max(ordinal() - 1, 0);
            return VALUES[index];
        }
    }

    private final DiagnosticsOutputDirectory outputDirectory;

    private final Map<ExceptionAction, Integer> problemsHandledPerAction;

    /**
     * @param outputDirectory object used to access a directory for dumping if the compilation is
     *            re-executed
     * @param problemsHandledPerAction map used to count the number of compilation failures or
     *            bailouts handled by each action. This is provided by the caller as it is expected
     *            to be shared between instances of {@link CompilationWrapper}.
     */
    public CompilationWrapper(DiagnosticsOutputDirectory outputDirectory, Map<ExceptionAction, Integer> problemsHandledPerAction) {
        this.outputDirectory = outputDirectory;
        this.problemsHandledPerAction = problemsHandledPerAction;
    }

    /**
     * Handles an uncaught exception.
     *
     * @param t an exception thrown during {@link #run(DebugContext)}
     * @return a value representing the result of a failed compilation (may be {@code null})
     */
    protected abstract T handleException(Throwable t);

    /**
     * Gets the action to take based on the value of
     * {@link GraalCompilerOptions#CompilationBailoutAsFailure},
     * {@link GraalCompilerOptions#CompilationFailureAction} and
     * {@link GraalCompilerOptions#ExitVMOnException} in {@code options}.
     *
     * Subclasses can override this to choose a different action.
     *
     * @param cause the cause of the bailout or failure
     */
    protected ExceptionAction lookupAction(OptionValues options, Throwable cause) {
        if (cause instanceof BailoutException && !CompilationBailoutAsFailure.getValue(options)) {
            return ExceptionAction.Silent;
        }
        if (ExitVMOnException.getValue(options)) {
            assert CompilationFailureAction.getDefaultValue() != ExceptionAction.ExitVM;
            assert ExitVMOnException.getDefaultValue() != true;
            if (CompilationFailureAction.hasBeenSet(options) && CompilationFailureAction.getValue(options) != ExceptionAction.ExitVM) {
                TTY.printf("WARNING: Ignoring %s=%s since %s=true has been explicitly specified.%n",
                                CompilationFailureAction.getName(), CompilationFailureAction.getValue(options),
                                ExitVMOnException.getName());
            }
            return ExceptionAction.ExitVM;
        }
        return CompilationFailureAction.getValue(options);
    }

    /**
     * Perform the compilation wrapped by this object.
     *
     * @param debug the debug context to use for the compilation
     */
    protected abstract T performCompilation(DebugContext debug);

    /**
     * Gets a value that represents the input to the compilation.
     */
    @Override
    public abstract String toString();

    /**
     * Creates the {@link DebugContext} to use when retrying a compilation.
     *
     * @param initialDebug the debug context used in the failing compilation
     * @param options the options for configuring the debug context
     * @param logStream the log stream to use in the debug context
     */
    protected abstract DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream);

    @SuppressWarnings("try")
    public final T run(DebugContext initialDebug) {
        try {
            return performCompilation(initialDebug);
        } catch (Throwable cause) {
            OptionValues initialOptions = initialDebug.getOptions();

            synchronized (CompilationFailureAction) {
                // Serialize all compilation failure handling.
                // This prevents retry compilation storms and interleaving
                // of compilation exception messages.
                // It also allows for reliable testing of CompilationWrapper
                // by avoiding a race whereby retry compilation output from a
                // forced crash (i.e., use of GraalCompilerOptions.CrashAt)
                // is truncated.

                ExceptionAction action = lookupAction(initialOptions, cause);

                action = adjustAction(initialOptions, action);

                if (action == ExceptionAction.Silent) {
                    return handleException(cause);
                }

                if (action == ExceptionAction.Print) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (PrintStream ps = new PrintStream(baos)) {
                        ps.printf("%s: Compilation of %s failed: ", Thread.currentThread(), this);
                        cause.printStackTrace(ps);
                        ps.printf("To disable compilation failure notifications, set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                        CompilationFailureAction.getName(), ExceptionAction.Silent,
                                        CompilationFailureAction.getName(), ExceptionAction.Silent);
                        ps.printf("To capture more information for diagnosing or reporting a compilation failure, " +
                                        "set %s to %s or %s (e.g., -Dgraal.%s=%s).%n",
                                        CompilationFailureAction.getName(), ExceptionAction.Diagnose,
                                        ExceptionAction.ExitVM,
                                        CompilationFailureAction.getName(), ExceptionAction.Diagnose);
                    }
                    TTY.print(baos.toString());
                    return handleException(cause);
                }

                // action is Diagnose or ExitVM

                if (Dump.hasBeenSet(initialOptions)) {
                    // If dumping is explicitly enabled, Graal is being debugged
                    // so don't interfere with what the user is expecting to see.
                    return handleException(cause);
                }

                File dumpPath = null;
                try {
                    String dir = this.outputDirectory.getPath();
                    if (dir != null) {
                        String dumpName = PathUtilities.sanitizeFileName(toString());
                        dumpPath = new File(dir, dumpName);
                        dumpPath.mkdirs();
                        if (!dumpPath.exists()) {
                            TTY.println("Warning: could not create diagnostics directory " + dumpPath);
                            dumpPath = null;
                        }
                    }
                } catch (Throwable t) {
                    TTY.println("Warning: could not create Graal diagnostic directory");
                    t.printStackTrace(TTY.out);
                }

                String message;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(baos)) {
                    // This output is used by external tools to detect compilation failures.
                    ps.println("[[[Graal compilation failure]]]");

                    ps.printf("%s: Compilation of %s failed:%n", Thread.currentThread(), this);
                    cause.printStackTrace(ps);
                    ps.printf("To disable compilation failure notifications, set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                    CompilationFailureAction.getName(), ExceptionAction.Silent,
                                    CompilationFailureAction.getName(), ExceptionAction.Silent);
                    ps.printf("To print a message for a compilation failure without retrying the compilation, " +
                                    "set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                    CompilationFailureAction.getName(), ExceptionAction.Print,
                                    CompilationFailureAction.getName(), ExceptionAction.Print);
                    if (dumpPath != null) {
                        ps.println("Retrying compilation of " + this);
                    } else {
                        ps.println("Not retrying compilation of " + this + " as the dump path could not be created.");
                    }
                    message = baos.toString();
                }

                TTY.print(message);
                if (dumpPath == null) {
                    return handleException(cause);
                }

                File retryLogFile = new File(dumpPath, "retry.log");
                try (PrintStream ps = new PrintStream(new FileOutputStream(retryLogFile))) {
                    ps.print(message);
                } catch (IOException ioe) {
                    TTY.printf("Error writing to %s: %s%n", retryLogFile, ioe);
                }

                OptionValues retryOptions = new OptionValues(initialOptions,
                                Dump, ":" + DebugOptions.DiagnoseDumpLevel.getValue(initialOptions),
                                MethodFilter, null,
                                DumpPath, dumpPath.getPath(),
                                TrackNodeSourcePosition, true);

                ByteArrayOutputStream logBaos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(logBaos);
                try (DebugContext retryDebug = createRetryDebugContext(initialDebug, retryOptions, ps)) {
                    T res = performCompilation(retryDebug);
                    ps.println("There was no exception during retry.");
                    maybeExitVM(action);
                    return res;
                } catch (Throwable e) {
                    ps.println("Exception during retry:");
                    e.printStackTrace(ps);
                    // Failures during retry are silent
                    T res = handleException(cause);
                    maybeExitVM(action);
                    return res;
                } finally {
                    ps.close();
                    try (FileOutputStream fos = new FileOutputStream(retryLogFile, true)) {
                        fos.write(logBaos.toByteArray());
                    } catch (Throwable e) {
                        TTY.printf("Error writing to %s: %s%n", retryLogFile, e);
                    }
                }
            }
        }
    }

    /**
     * Calls {@link System#exit(int)} in the runtime embedding the Graal compiler. This will be a
     * different runtime than Graal's runtime in the case of libgraal.
     */
    protected abstract void exitHostVM(int status);

    private void maybeExitVM(ExceptionAction action) {
        if (action == ExitVM) {
            TTY.println("Exiting VM after retry compilation of " + this);
            exitHostVM(-1);
        }
    }

    /**
     * Adjusts {@code initialAction} if necessary based on
     * {@link GraalCompilerOptions#MaxCompilationProblemsPerAction}.
     */
    private ExceptionAction adjustAction(OptionValues initialOptions, ExceptionAction initialAction) {
        ExceptionAction action = initialAction;
        int maxProblems = MaxCompilationProblemsPerAction.getValue(initialOptions);
        if (action != ExceptionAction.ExitVM) {
            synchronized (problemsHandledPerAction) {
                while (action != ExceptionAction.Silent) {
                    int problems = problemsHandledPerAction.getOrDefault(action, 0);
                    if (problems >= maxProblems) {
                        if (problems == maxProblems) {
                            TTY.printf("Warning: adjusting %s from %s to %s after %s (%d) failed compilations%n", CompilationFailureAction, action, action.quieter(),
                                            MaxCompilationProblemsPerAction, maxProblems);
                            // Ensure that the message above is only printed once
                            problemsHandledPerAction.put(action, problems + 1);
                        }
                        action = action.quieter();
                    } else {
                        break;
                    }
                }
                problemsHandledPerAction.put(action, problemsHandledPerAction.getOrDefault(action, 0) + 1);
            }
        }
        return action;
    }
}
