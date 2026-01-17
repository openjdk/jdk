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

import static java.util.stream.Collectors.toMap;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.log.CommandLogger.CommandLoggerTrait;
import jdk.jpackage.internal.log.ConsoleLogger.ConsoleLoggerErrSink;
import jdk.jpackage.internal.log.ConsoleLogger.ConsoleLoggerOutSink;
import jdk.jpackage.internal.log.ConsoleLogger.ConsoleLoggerTimestampClock;
import jdk.jpackage.internal.log.ErrorLogger.ErrorLoggerTrait;
import jdk.jpackage.internal.log.ProgressLogger.ProgressLoggerTrait;
import jdk.jpackage.internal.log.SummaryLogger.SummaryLoggerTrait;

public final class LogEnvironment {

    public enum LogSink {
        CONSOLE,
        SYSTEM_LOGGER
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        public Options create() {

            var consoleTraits = new HashSet<LoggerTrait>();
            Optional.ofNullable(out).map(ConsoleLoggerOutSink::new).ifPresent(consoleTraits::add);
            Optional.ofNullable(err).map(ConsoleLoggerErrSink::new).ifPresent(consoleTraits::add);
            Optional.ofNullable(consoleTimestampClock).map(ConsoleLoggerTimestampClock::new).ifPresent(consoleTraits::add);

            var loggerTraits = enabledLoggers.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
                var enabledSinks = e.getValue();

                var traits = new ArrayList<LoggerTrait>(booleanTraits);

                if (enabledSinks.contains(LogSink.SYSTEM_LOGGER)) {
                    traits.add(new SystemLoggerTrait(
                            Optional.ofNullable(systemLoggerFactory).orElse(System::getLogger).apply("jdk.jpackage")));
                }

                if (enabledSinks.contains(LogSink.CONSOLE)) {
                    traits.addAll(consoleTraits);
                }

                return traits;
            }));

            return LogEnvironment.create(loggerTraits);
        }

        public Builder out(PrintWriter v) {
            out = v;
            return this;
        }

        public Builder err(PrintWriter v) {
            err = v;
            return this;
        }

        public Builder printErrorStackTraceInConsole(boolean v) {
            return setBooleanTrait(v, ErrorLoggerTrait.PRINT_STACK_TRACE_ALWAYS);
        }

        public Builder printCommandOutputInConsole(boolean v) {
            return setBooleanTrait(v, CommandLoggerTrait.PRINT_COMMAND_RESULT);
        }

        public Builder printFailedCommandOutputInConsole(boolean v) {
            return setBooleanTrait(v, ErrorLoggerTrait.PRINT_FAILED_COMMAND_OUTPUT);
        }

        public Builder printQuietCommands(boolean v) {
            return setBooleanTrait(v, CommandLoggerTrait.PRINT_QUIET_COMMANDS);
        }

        public Builder printProgressInConsole(boolean v) {
            return setBooleanTrait(v, ProgressLoggerTrait.PRINT_INFO);
        }

        public Builder printProgressWarningsInConsole(boolean v) {
            return setBooleanTrait(v, ProgressLoggerTrait.PRINT_WARNINGS);
        }

        public Builder printSummaryInConsole(boolean v) {
            return setBooleanTrait(v, SummaryLoggerTrait.PRINT_INFO);
        }

        public Builder printSummaryWarningsInConsole(boolean v) {
            return setBooleanTrait(v, SummaryLoggerTrait.PRINT_WARNINGS);
        }

        public Builder consoleTimestampClock(Clock v) {
            consoleTimestampClock = v;
            return this;
        }

        public Builder systemLoggerFactory(Function<String, System.Logger> v) {
            systemLoggerFactory = v;
            return this;
        }

        public Builder enable(LoggerRole role, LogSink... sinks) {
            Objects.requireNonNull(role);
            if (sinks.length == 0) {
                enabledLoggers.remove(role);
            } else {
                enabledLoggers.computeIfAbsent(role, _ -> {
                    return new HashSet<>();
                }).addAll(Set.of(sinks));
            }
            return this;
        }

        private Builder setBooleanTrait(boolean set, LoggerTrait trait) {
            Objects.requireNonNull(trait);
            if (set) {
                booleanTraits.add(trait);
            } else {
                booleanTraits.remove(trait);
            }
            return this;
        }

        private PrintWriter out;
        private PrintWriter err;
        private Clock consoleTimestampClock;
        private Function<String, System.Logger> systemLoggerFactory;
        private final Set<LoggerTrait> booleanTraits = new HashSet<>();
        private final Map<LoggerRole, Set<LogSink>> enabledLoggers = new HashMap<>();
    }

    static Options create(Map<LoggerRole, ? extends Collection<LoggerTrait>> loggerTraits) {
        return Options.of(loggerTraits.entrySet().stream().collect(toMap(e -> {
            return e.getKey().logger();
        }, e -> {
            return e.getKey().createLogger(e.getValue());
        })));
    }

    static CommandLogger createCommandLogger(Collection<LoggerTrait> traits) {
        return create(
                CommandLogger.class,
                traits,
                sink -> {
                    var printQuietCommands = traits.contains(CommandLoggerTrait.PRINT_QUIET_COMMANDS);
                    var printResult = traits.contains(CommandLoggerTrait.PRINT_COMMAND_RESULT);
                    return CommandLogger.create(sink, printQuietCommands, printResult);
                },
                CommandLogger::create).orElse(CommandLogger.DISCARDING_LOGGER);
    }

    static ProgressLogger createProgressLogger(Collection<LoggerTrait> traits) {
        return create(
                ProgressLogger.class,
                traits,
                sink -> {
                    var printInfo = traits.contains(ProgressLoggerTrait.PRINT_INFO);
                    var printWarnings = traits.contains(ProgressLoggerTrait.PRINT_WARNINGS);
                    return ProgressLogger.create(sink, printInfo, printWarnings);
                },
                ProgressLogger::create).orElse(ProgressLogger.DISCARDING_LOGGER);
    }

    static ErrorLogger createErrorLogger(Collection<LoggerTrait> traits) {
        return create(
                ErrorLogger.class,
                traits,
                sink -> {
                    var printStacktrace = traits.contains(ErrorLoggerTrait.PRINT_STACK_TRACE_ALWAYS);
                    var printCommandOutput = traits.contains(ErrorLoggerTrait.PRINT_FAILED_COMMAND_OUTPUT);
                    return ErrorLogger.create(sink, printStacktrace, printCommandOutput);
                },
                ErrorLogger::create).orElse(ErrorLogger.DISCARDING_LOGGER);
    }

    static TraceLogger createTraceLogger(Collection<LoggerTrait> traits) {
        return create(
                TraceLogger.class,
                traits,
                TraceLogger::create,
                TraceLogger::create).orElse(TraceLogger.DISCARDING_LOGGER);
    }

    static ResourceLogger createResourceLogger(Collection<LoggerTrait> traits) {
        return create(
                ResourceLogger.class,
                traits,
                ResourceLogger::create,
                ResourceLogger::create).orElse(ResourceLogger.DISCARDING_LOGGER);
    }

    static SummaryLogger createSummaryLogger(Collection<LoggerTrait> traits) {
        return create(SummaryLogger.class,
                traits,
                sink -> {
                    var printInfo = traits.contains(SummaryLoggerTrait.PRINT_INFO);
                    var printWarnings = traits.contains(SummaryLoggerTrait.PRINT_WARNINGS);
                    return SummaryLogger.create(sink, printInfo, printWarnings);
                },
                SummaryLogger::create).orElse(SummaryLogger.DISCARDING_LOGGER);
    }

    static Optional<ConsoleLogger> createConsoleLoggerSink(Collection<LoggerTrait> traits) {

        var out = filterTraitsOfType(ConsoleLoggerOutSink.class, traits.stream())
                .map(ConsoleLoggerOutSink::sink).findFirst();

        var err = filterTraitsOfType(ConsoleLoggerErrSink.class, traits.stream())
                .map(ConsoleLoggerErrSink::sink).findFirst();

        var timestampClock = filterTraitsOfType(ConsoleLoggerTimestampClock.class, traits.stream())
                .map(ConsoleLoggerTimestampClock::clock)
                .findFirst().orElseGet(new ConsoleLogger(Utils.DISCARDER, Utils.DISCARDER)::timestampClock);

        if (out.isEmpty() && err.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new ConsoleLogger(toStringConsumer(out), toStringConsumer(err), timestampClock));
        }
    }

    static Optional<System.Logger> createSystemLoggerSink(Collection<LoggerTrait> traits) {
        return filterTraitsOfType(SystemLoggerTrait.class, traits.stream()).map(SystemLoggerTrait::logger).findFirst();
    }

    private static <T extends Logger> Optional<T> create(
            Class<T> loggerType,
            Collection<LoggerTrait> traits,
            Function<ConsoleLogger, T> fromConsoleSinkCtor,
            Function<System.Logger, T> fromSystemLoggerSinkCtor) {

        Objects.requireNonNull(traits);
        Objects.requireNonNull(fromSystemLoggerSinkCtor);
        Objects.requireNonNull(fromConsoleSinkCtor);

        var consoleLogger = createConsoleLoggerSink(traits).map(fromConsoleSinkCtor);
        var systemLogger = createSystemLoggerSink(traits).map(fromSystemLoggerSinkCtor);

        if (consoleLogger.isEmpty() && systemLogger.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(Utils.teeLogger(loggerType, Stream.of(
                    consoleLogger.stream(),
                    systemLogger.stream()
            ).flatMap(x -> x).toList()));
        }
    }

    private static <T extends LoggerTrait> Stream<? extends T> filterTraitsOfType(
            Class<? extends T> type, Stream<? extends LoggerTrait> stream) {
        Objects.requireNonNull(type);
        return stream.filter(type::isInstance).map(type::cast);
    }

    private static Consumer<String> toStringConsumer(Optional<PrintWriter> pw) {
        return pw.<Consumer<String>>map(v -> {
            return v::println;
        }).orElse(Utils.DISCARDER);
    }

    private LogEnvironment() {
    }
}
