/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAction;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.core.GraalCompilerOptions.ExitVMOnException;
import static org.graalvm.compiler.debug.DebugContext.VERBOSE_LEVEL;
import static org.graalvm.compiler.debug.DebugOptions.Dump;
import static org.graalvm.compiler.debug.DebugOptions.DumpPath;
import static org.graalvm.compiler.debug.DebugOptions.MethodFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BailoutException;

/**
 * Wrapper for a compilation that centralizes what action to take based on
 * {@link GraalCompilerOptions#CompilationBailoutAction} and
 * {@link GraalCompilerOptions#CompilationFailureAction} when an uncaught exception occurs during
 * compilation.
 */
public abstract class CompilationWrapper<T> {

    /**
     * Actions to take upon an exception being raised during compilation performed via
     * {@link CompilationWrapper}. The actions are with respect to what the user sees on the
     * console. The compilation requester determines what ultimate action is taken in
     * {@link CompilationWrapper#handleException(Throwable)}.
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

        static ValueHelp HELP = new ValueHelp();

        static class ValueHelp implements EnumOptionKey.ValueHelp<ExceptionAction> {
            @Override
            public String getHelp(Object value) {
                ExceptionAction action = (ExceptionAction) value;
                switch (action) {
                    case Silent:
                        return action + ": Print nothing to the console.";
                    case Print:
                        return action + ": Print a stack trace to the console.";
                    case Diagnose:
                        return action + ": Retry the compilation with extra diagnostics.";
                    case ExitVM:
                        return action + ": Same as " + Diagnose + " except that the VM process exits after retrying.";
                }
                return null;
            }
        }
    }

    private final DiagnosticsOutputDirectory outputDirectory;

    /**
     * @param outputDirectory object used to access a directory for dumping if the compilation is
     *            re-executed
     */
    public CompilationWrapper(DiagnosticsOutputDirectory outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Handles an uncaught exception.
     *
     * @param t an exception thrown during {@link #run(DebugContext)}
     * @return a value representing the result of a failed compilation (may be {@code null})
     */
    protected abstract T handleException(Throwable t);

    /**
     * Gets the action to take based on the value of {@code actionKey} in {@code options}.
     *
     * Subclasses can override this to choose a different action based on factors such as whether
     * {@code actionKey} has been explicitly set in {@code options} for example.
     */
    protected ExceptionAction lookupAction(OptionValues options, EnumOptionKey<ExceptionAction> actionKey) {
        if (actionKey == CompilationFailureAction) {
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
        }
        return actionKey.getValue(options);
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
     * @param options the options for configuring the debug context
     */
    protected abstract DebugContext createRetryDebugContext(OptionValues options);

    @SuppressWarnings("try")
    public final T run(DebugContext initialDebug) {
        try {
            return performCompilation(initialDebug);
        } catch (Throwable cause) {
            OptionValues initialOptions = initialDebug.getOptions();

            String causeType = "failure";
            EnumOptionKey<ExceptionAction> actionKey;
            if (cause instanceof BailoutException) {
                actionKey = CompilationBailoutAction;
                causeType = "bailout";
            } else {
                actionKey = CompilationFailureAction;
                causeType = "failure";
            }
            ExceptionAction action = lookupAction(initialOptions, actionKey);

            if (action == ExceptionAction.Silent) {
                return handleException(cause);
            }
            if (action == ExceptionAction.Print) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(baos)) {
                    ps.printf("%s: Compilation of %s failed: ", Thread.currentThread(), this);
                    cause.printStackTrace(ps);
                    ps.printf("To disable compilation %s notifications, set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                    causeType,
                                    actionKey.getName(), ExceptionAction.Silent,
                                    actionKey.getName(), ExceptionAction.Silent);
                    ps.printf("To capture more information for diagnosing or reporting a compilation %s, " +
                                    "set %s to %s or %s (e.g., -Dgraal.%s=%s).%n",
                                    causeType,
                                    actionKey.getName(), ExceptionAction.Diagnose,
                                    ExceptionAction.ExitVM,
                                    actionKey.getName(), ExceptionAction.Diagnose);
                }
                synchronized (CompilationFailureAction) {
                    // Synchronize to prevent compilation exception
                    // messages from interleaving.
                    TTY.println(baos.toString());
                }
                return handleException(cause);
            }

            // action is Diagnose or ExitVM

            if (Dump.hasBeenSet(initialOptions)) {
                // If dumping is explicitly enabled, Graal is being debugged
                // so don't interfere with what the user is expecting to see.
                return handleException(cause);
            }

            String dir = this.outputDirectory.getPath();
            if (dir == null) {
                return null;
            }
            String dumpName = PathUtilities.sanitizeFileName(toString());
            File dumpPath = new File(dir, dumpName);
            dumpPath.mkdirs();
            if (!dumpPath.exists()) {
                TTY.println("Warning: could not create diagnostics directory " + dumpPath);
                return handleException(cause);
            }

            String message;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(baos)) {
                ps.printf("%s: Compilation of %s failed: ", Thread.currentThread(), this);
                cause.printStackTrace(ps);
                ps.printf("To disable compilation %s notifications, set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                causeType,
                                actionKey.getName(), ExceptionAction.Silent,
                                actionKey.getName(), ExceptionAction.Silent);
                ps.printf("To print a message for a compilation %s without retrying the compilation, " +
                                "set %s to %s (e.g., -Dgraal.%s=%s).%n",
                                causeType,
                                actionKey.getName(), ExceptionAction.Print,
                                actionKey.getName(), ExceptionAction.Print);
                ps.println("Retrying compilation of " + this);
                message = baos.toString();
            }

            synchronized (CompilationFailureAction) {
                // Synchronize here to serialize retry compilations. This
                // mitigates retry compilation storms.
                TTY.println(message);
                File retryLogFile = new File(dumpPath, "retry.log");
                try (PrintStream ps = new PrintStream(new FileOutputStream(retryLogFile))) {
                    ps.print(message);
                } catch (IOException ioe) {
                    TTY.printf("Error writing to %s: %s%n", retryLogFile, ioe);
                }

                OptionValues retryOptions = new OptionValues(initialOptions,
                                Dump, ":" + VERBOSE_LEVEL,
                                MethodFilter, null,
                                DumpPath, dumpPath.getPath());

                try (DebugContext retryDebug = createRetryDebugContext(retryOptions)) {
                    return performCompilation(retryDebug);
                } catch (Throwable ignore) {
                    // Failures during retry are silent
                    return handleException(cause);
                } finally {
                    if (action == ExitVM) {
                        synchronized (ExceptionAction.class) {
                            TTY.println("Exiting VM after retry compilation of " + this);
                            System.exit(-1);
                        }
                    }
                }
            }
        }
    }
}
