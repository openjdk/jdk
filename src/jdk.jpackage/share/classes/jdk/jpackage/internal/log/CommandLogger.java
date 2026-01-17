/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.log;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public interface CommandLogger extends Logger {

    void beforeCommandExecuted(boolean quiet, String cmdline);

    void afterCommandExecuted(boolean quiet,
            String cmdline, Optional<Long> pid, Optional<Integer> exitCode, String printableOutput);

    enum CommandLoggerTrait implements LoggerTrait {
        PRINT_COMMAND_RESULT,
        PRINT_QUIET_COMMANDS,
    }

    static CommandLogger create(ConsoleLogger sink, boolean printQuietCommands, boolean printResult) {
        var theSink = sink.addTimestampsToOut().out();
        return new Details.DefaultLogger(
                theSink,
                printQuietCommands ? theSink : Utils.DISCARDER,
                printResult);
    }

    static CommandLogger create(System.Logger sink) {
        var consumer = Utils.toStringConsumer(sink, System.Logger.Level.DEBUG);
        return new Details.DefaultLogger(consumer, consumer, true);
    }

    final static class Details {

        private Details() {
        }

        private record DefaultLogger(
                Consumer<String> sink,
                Consumer<String> quietCommandSink,
                boolean printResult) implements CommandLogger {

            DefaultLogger {
                Objects.requireNonNull(sink);
                Objects.requireNonNull(quietCommandSink);
            }

            @Override
            public void beforeCommandExecuted(boolean quiet, String cmdline) {

                Objects.requireNonNull(cmdline);

                sink(quiet).accept(String.format("Running %s", cmdline));
            }

            @Override
            public void afterCommandExecuted(boolean quiet,
                    String cmdline, Optional<Long> pid, Optional<Integer> exitCode, String printableOutput) {

                Objects.requireNonNull(cmdline);
                Objects.requireNonNull(pid);
                Objects.requireNonNull(exitCode);
                Objects.requireNonNull(printableOutput);

                if (!printResult) {
                    return;
                }

                var theSink = sink(quiet);

                var sb = new StringBuilder();
                sb.append("Command");
                pid.ifPresent(p -> {
                    sb.append(" [PID: ").append(p).append("]");
                });
                sb.append(":").append(System.lineSeparator()).append("    ").append(cmdline);
                theSink.accept(sb.toString());

                if (!printableOutput.isEmpty()) {
                    sb.delete(0, sb.length());
                    sb.append("Output:");
                    try (var lines = new BufferedReader(new StringReader(printableOutput)).lines()) {
                        lines.forEach(line -> {
                            sb.append(System.lineSeparator()).append("    ").append(line);
                        });
                    }
                    theSink.accept(sb.toString());
                }

                exitCode.ifPresentOrElse(v -> {
                    theSink.accept("Returned: " + v);
                }, () -> {
                    theSink.accept("Aborted: timed-out");
                });
            }

            private Consumer<String> sink(boolean quietCommand) {
                return quietCommand ? quietCommandSink : sink;
            }

        }
    }

    static final CommandLogger DISCARDING_LOGGER = Utils.discardingLogger(CommandLogger.class);
}
