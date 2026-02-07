/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.cli;

import static jdk.jpackage.internal.cli.StandardOption.HELP;
import static jdk.jpackage.internal.cli.StandardOption.VERBOSE;
import static jdk.jpackage.internal.cli.StandardOption.VERSION;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import jdk.internal.opt.CommandLine;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Globals;
import jdk.jpackage.internal.Log;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExecutableAttributesWithCapturedOutput;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.SelfContainedException;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedExitCodeException;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedResultException;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * Main jpackage entry point.
 */
public final class Main {

    public record Provider(Supplier<CliBundlingEnvironment> bundlingEnvSupplier) implements ToolProvider {

        public Provider {
            Objects.requireNonNull(bundlingEnvSupplier);
        }

        public Provider() {
            this(DefaultBundlingEnvironmentLoader.INSTANCE);
        }

        @Override
        public String name() {
            return "jpackage";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return Main.run(bundlingEnvSupplier, out, err, args);
        }

        @Override
        public int run(PrintStream out, PrintStream err, String... args) {
            PrintWriter outWriter = toPrintWriter(out);
            PrintWriter errWriter = toPrintWriter(err);
            try {
                try {
                    return run(outWriter, errWriter, args);
                } finally {
                    outWriter.flush();
                }
            } finally {
                errWriter.flush();
            }
        }
    }


    private Main() {
    }

    public static void main(String... args) {
        var out = toPrintWriter(System.out);
        var err = toPrintWriter(System.err);
        System.exit(run(out, err, args));
    }

    static int run(PrintWriter out, PrintWriter err, String... args) {
        return run(DefaultBundlingEnvironmentLoader.INSTANCE, out, err, args);
    }

    static int run(Supplier<CliBundlingEnvironment> bundlingEnvSupplier, PrintWriter out, PrintWriter err, String... args) {
        return Globals.main(() -> {
            return runWithGlobals(bundlingEnvSupplier, out, err, args);
        });
    }

    private static int runWithGlobals(
            Supplier<CliBundlingEnvironment> bundlingEnvSupplier,
            PrintWriter out,
            PrintWriter err,
            String... args) {

        Objects.requireNonNull(bundlingEnvSupplier);
        Objects.requireNonNull(args);
        for (String arg : args) {
            Objects.requireNonNull(arg);
        }
        Objects.requireNonNull(out);
        Objects.requireNonNull(err);

        Globals.instance().loggerOutputStreams(out, err);

        final var runner = new Runner(t -> {
            new ErrorReporter(_ -> {
                t.printStackTrace(err);
            }, Log::fatalError, Log.isVerbose()).reportError(t);
        });

        try {
            var mappedArgs = Slot.<String[]>createEmpty();

            int preprocessStatus = runner.run(() -> {
                try {
                    mappedArgs.set(CommandLine.parse(args));
                    return List.of();
                } catch (FileNotFoundException | NoSuchFileException ex) {
                    return List.of(new JPackageException(I18N.format("ERR_CannotParseOptions", ex.getMessage()), ex));
                } catch (IOException ex) {
                    return List.of(ex);
                }
            });

            if (preprocessStatus != 0) {
                return preprocessStatus;
            }

            final var bundlingEnv = bundlingEnvSupplier.get();

            final var parseResult = Utils.buildParser(OperatingSystem.current(), bundlingEnv).create().apply(mappedArgs.get());

            return runner.run(() -> {
                final var parsedOptionsBuilder = parseResult.orElseThrow();

                final var options = parsedOptionsBuilder.create();

                final var nonOptionArgumentsError = !parsedOptionsBuilder.nonOptionArguments().isEmpty();

                if (options.ids().isEmpty() && !nonOptionArgumentsError) {
                    StandardHelpFormatter.INSTANCE.formatNoArgsHelp(out::append);
                    return List.of();
                } else if (HELP.containsIn(options)) {
                    if (VERSION.containsIn(options)) {
                        out.println(getVersion() + "\n");
                    }
                    StandardHelpFormatter.INSTANCE.format(out::append);
                    return List.of();
                } else if (VERSION.containsIn(options) && !nonOptionArgumentsError) {
                    out.println(getVersion());
                    return List.of();
                }

                if (VERBOSE.containsIn(options)) {
                    Globals.instance().loggerVerbose();
                }

                final var optionsProcessor = new OptionsProcessor(parsedOptionsBuilder, bundlingEnv);

                final var validationResult = optionsProcessor.validate();

                final var bundlingResult = validationResult.map(optionsProcessor::runBundling);

                if (bundlingResult.hasValue()) {
                    return bundlingResult.orElseThrow();
                } else {
                    return bundlingResult.errors();
                }
            });
        } finally {
            try {
                out.flush();
            } finally {
                err.flush();
            }
        }
    }

    /*
     * Exception (error) reporting:
     *
     * There are two types of exceptions to handle:
     *
     * 1. Exceptions explicitly thrown by jpackage code with localized,
     *    jpackage-specific error messages. These are usually instances of
     *    JPackageException.
     *
     * 2. Exceptions thrown by JDK code (for example, an NPE from Optional.of(...)).
     *    These should normally not occur or should be handled at the point
     *    where they arise. If they reach this level of exception handling,
     *    it indicates a flaw in jpackage’s internal logic.
     *
     * Always print stack traces for exceptions of type #2.
     * Print stack traces for exceptions of type #1 only in verbose mode.
     * Always print the messages for exceptions of any type.
     */

    record ErrorReporter(Consumer<Throwable> stackTracePrinter, Consumer<String> messagePrinter, boolean verbose) {
        ErrorReporter {
            Objects.requireNonNull(stackTracePrinter);
            Objects.requireNonNull(messagePrinter);
        }

        ErrorReporter(Consumer<Throwable> stackTracePrinter, Consumer<String> messagePrinter) {
            this(stackTracePrinter, messagePrinter, true);
        }

        void reportError(Throwable t) {

            var unfoldedExceptions = new ArrayList<Exception>();
            ExceptionBox.visitUnboxedExceptionsRecursively(t, unfoldedExceptions::add);

            unfoldedExceptions.forEach(ex -> {
                if (ex instanceof ConfigException cfgEx) {
                    printError(cfgEx, Optional.ofNullable(cfgEx.getAdvice()));
                } else if (ex instanceof UncheckedIOException) {
                    printError(ex.getCause(), Optional.empty());
                } else if (ex instanceof UnexpectedResultException urex) {
                    printExternalCommandError(urex);
                } else {
                    printError(ex, Optional.empty());
                }
            });
        }

        private void printExternalCommandError(UnexpectedResultException ex) {
            var result = ex.getResult();
            var commandOutput = ((ExecutableAttributesWithCapturedOutput)result.execAttrs()).printableOutput();
            var printableCommandLine = result.execAttrs().printableCommandLine();

            if (verbose) {
                stackTracePrinter.accept(ex);
            }

            String msg;
            if (ex instanceof UnexpectedExitCodeException) {
                msg = I18N.format("error.command-failed-unexpected-exit-code", result.getExitCode(), printableCommandLine);
            } else if (result.exitCode().isPresent()) {
                msg = I18N.format("error.command-failed-unexpected-output", printableCommandLine);
            } else {
                msg = I18N.format("error.command-failed-timed-out", printableCommandLine);
            }

            messagePrinter.accept(I18N.format("message.error-header", msg));
            messagePrinter.accept(I18N.format("message.failed-command-output-header"));
            try (var lines = new BufferedReader(new StringReader(commandOutput)).lines()) {
                lines.forEach(messagePrinter);
            }
        }

        private void printError(Throwable t, Optional<String> advice) {
            var isSelfContained = isSelfContained(t);

            if (!isSelfContained || verbose) {
                stackTracePrinter.accept(t);
            }

            String msg;
            if (isSelfContained) {
                msg = t.getMessage();
            } else {
                msg = t.toString();
            }

            messagePrinter.accept(I18N.format("message.error-header", msg));
            advice.ifPresent(v -> messagePrinter.accept(I18N.format("message.advice-header", v)));
        }

        private static boolean isSelfContained(Throwable t) {
            return t.getClass().getAnnotation(SelfContainedException.class) != null;
        }
    }


    record Runner(Consumer<Throwable> errorReporter) {

        Runner {
            Objects.requireNonNull(errorReporter);
        }

        int run(Supplier<? extends Collection<? extends Exception>> r) {
            final var exceptions = runIt(r);
            if (exceptions.isEmpty()) {
                return 0;
            } else {
                exceptions.forEach(errorReporter);
                return 1;
            }
        }

        private static Collection<? extends Exception> runIt(Supplier<? extends Collection<? extends Exception>> r) {
            try {
                return r.get();
            } catch (RuntimeException ex) {
                return List.of(ex);
            }
        }
    }

    private static String getVersion() {
        return System.getProperty("java.version");
    }

    private static PrintWriter toPrintWriter(PrintStream ps) {
        return new PrintWriter(ps, true, ps.charset());
    }

    private enum DefaultBundlingEnvironmentLoader implements Supplier<CliBundlingEnvironment> {
        INSTANCE;

        @Override
        public CliBundlingEnvironment get() {
            return ServiceLoader.load(
                    CliBundlingEnvironment.class,
                    CliBundlingEnvironment.class.getClassLoader()).findFirst().orElseThrow();
        }
    }
}
