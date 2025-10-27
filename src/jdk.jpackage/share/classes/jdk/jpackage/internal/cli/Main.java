/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import jdk.internal.opt.CommandLine;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Log;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * Main jpackage entry point.
 */
public final class Main {

    public static final class Provider implements ToolProvider {

        @Override
        public String name() {
            return "jpackage";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return Main.run(out, err, args);
        }
    }


    private Main() {
    }

    public static void main(String... args) {
        var out = new PrintWriter(System.out, true);
        var err = new PrintWriter(System.err, true);
        System.exit(run(out, err, args));
    }

    public static int run(PrintWriter out, PrintWriter err, String... args) {
        Objects.requireNonNull(out);
        Objects.requireNonNull(err);

        Log.setPrintWriter(out, err);

        try {
            try {
                args = CommandLine.parse(args);
            } catch (FileNotFoundException|NoSuchFileException ex) {
                Log.fatalError(I18N.format("ERR_CannotParseOptions", ex.getMessage()));
                return 1;
            } catch (IOException ex) {
                throw ExceptionBox.rethrowUnchecked(ex);
            }

            final var bundlingEnv = ServiceLoader.load(CliBundlingEnvironment.class,
                    CliBundlingEnvironment.class.getClassLoader()).findFirst().orElseThrow();

            final var parseResult = Utils.buildParser(OperatingSystem.current(), bundlingEnv).create().apply(args);

            return new Runner().run(() -> {
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
                    Log.setVerbose();
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


    static final class Runner {

        int run(Supplier<? extends Collection<? extends Exception>> r) {
            final var exceptions = runIt(r);
            exceptions.forEach(this::reportError);
            if (exceptions.isEmpty()) {
                return 0;
            } else {
                return 1;
            }
        }

        private void reportError(Throwable t) {
            if (t instanceof ConfigException cfgEx) {
                printError(cfgEx, Optional.ofNullable(cfgEx.getAdvice()));
            } else if (t instanceof ExceptionBox ex) {
                reportError(ex.getCause());
            } else if (t instanceof UncheckedIOException ex) {
                reportError(ex.getCause());
            } else {
                printError(t);
            }
        }

        private void printError(Throwable t) {
            printError(t, Optional.empty());
        }

        private void printError(Throwable t, Optional<String> advice) {
            Log.verbose(t);
//            Log.fatalError(I18N.format("message.error-header", t.getMessage()));
            Log.fatalError(Optional.ofNullable(t.getMessage()).orElseGet(t::toString));
            advice.ifPresent(v -> Log.fatalError(I18N.format("message.advice-header", v)));
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
}
