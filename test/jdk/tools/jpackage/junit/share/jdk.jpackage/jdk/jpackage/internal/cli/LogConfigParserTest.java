/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.log.AllLoggers;
import jdk.jpackage.internal.log.CommandLogger;
import jdk.jpackage.internal.log.ConsoleLogger;
import jdk.jpackage.internal.log.ErrorLogger;
import jdk.jpackage.internal.log.LogEnvironment;
import jdk.jpackage.internal.log.LogEnvironment.LogSink;
import jdk.jpackage.internal.log.Logger;
import jdk.jpackage.internal.log.LoggerRole;
import jdk.jpackage.internal.log.ProgressLogger;
import jdk.jpackage.internal.log.ResourceLogger;
import jdk.jpackage.internal.log.SummaryLogger;
import jdk.jpackage.internal.log.TraceLogger;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.summary.StandardProperty;
import jdk.jpackage.internal.summary.Summary;
import jdk.jpackage.internal.summary.Warning;
import jdk.jpackage.internal.util.SetBuilder;
import jdk.jpackage.internal.util.function.ExceptionBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LogConfigParserTest {

    @ParameterizedTest
    @MethodSource
    void test_valueOf(TestSpec spec) {
        spec.run();
    }

    @ParameterizedTest
    @MethodSource
    void test_valueOf_negative(String logEnvStr) {
        assertThrowsExactly(IllegalArgumentException.class, () -> {
            LogConfigParser.valueOf(logEnvStr);
        });
    }

    @Test
    void test_defaultVerbose() {
        test(LogConfigParser.defaultVerbose(), MessageCategory.toLogRecords(
                SetBuilder.<MessageCategory>build()
                        .add(MessageCategory.values())
                        .remove(MessageCategory.SYSTEM_LOGGER, MessageCategory.TRACE)
                        .create()));
    }

    @Test
    void test_quiet() {
        test(LogConfigParser.quiet(), MessageCategory.toLogRecords(
                MessageCategory.ERRORS,
                MessageCategory.WARNINGS));
    }

    private static void test(LogEnvironment.Builder logEnvBuilder, Set<LogRecord> expectedLogRecords) {
        Objects.requireNonNull(logEnvBuilder);
        Objects.requireNonNull(expectedLogRecords);

        var consoleSink = new StringWriter();
        var systemLoggerSink = new StringWriter();

        var logEnv = logEnvBuilder
                .out(new PrintWriter(consoleSink, true))
                .err(new PrintWriter(consoleSink, true))
                .consoleTimestampClock(FIXED_CONSOLE_TIMESTAMP)
                .systemLoggerFactory(_ -> {
                    return new SimpleSystemLogger(new PrintWriter(systemLoggerSink, true)::println);
                })
                .create();

        var logger = AllLoggers.create(logEnv);

        Stream.of(LogMessage.values()).sorted(Comparator.comparing(Enum::name)).forEach(logRecordSrc -> {

            var logRecordSink = new StringWriter();

            var logRecords = LogRecord.findLogRecords(logRecordSrc, expectedLogRecords);
            var logRecordLogger = AllLoggers.teeLogger(logRecords.stream().map(logRecord -> {
                return logRecord.createLogger(logRecordSink);
            }).toList());

            List.of(consoleSink, systemLoggerSink).forEach(sink -> {
                var buffer = sink.getBuffer();
                buffer.delete(0, buffer.length());
            });

            // Record log messages for the logger constructed from the string value
            // and for the logger constructed from the expected log record from
            // the same line of code to ensure recorded stack traces will be equal.
            // Don't use "Collection#forEach()" as the optimizations in the immutable list implementation in JDK27
            // produce different stack traces for the first and the last items in the two-item list.
            for (var l : List.of(logRecordLogger, logger)) {
                logRecordSrc.applyTo(l);
            }

            switch (logRecords.size()) {
                case 0 -> {
                    assertEquals("", logRecordSink.toString());
                    assertEquals("", consoleSink.toString());
                    assertEquals("", systemLoggerSink.toString());
                }
                case 1 -> {
                    StringWriter nonEmptySink;
                    StringWriter emptySink;
                    if (logRecords.getFirst().isSystemLogger()) {
                        nonEmptySink = systemLoggerSink;
                        emptySink = consoleSink;
                    } else {
                        nonEmptySink = consoleSink;
                        emptySink = systemLoggerSink;
                    }
                    assertEquals(logRecordSink.toString(), nonEmptySink.toString());
                    assertEquals("", emptySink.toString());
                }
                case 2 -> {
                    var sink = new StringBuilder();
                    if (logRecords.getFirst().isSystemLogger()) {
                        sink.append(systemLoggerSink.toString()).append(consoleSink.toString());
                    } else {
                        sink.append(consoleSink.toString()).append(systemLoggerSink.toString());
                    }
                    assertEquals(logRecordSink.toString(), sink.toString());
                }
                default -> {
                    throw ExceptionBox.reachedUnreachable();
                }
            }

        });
    }

    private static Collection<TestSpec> test_valueOf() {

        var testCases = new ArrayList<TestSpec>();

        var allCategories = MessageCategory.values();

        IntStream.range(0, 2 << (MessageCategory.values().length - 1)).parallel().mapToObj(i -> {

            var bitset = BitSet.valueOf(new long[] {i});
            var categories = bitset.stream().mapToObj(ordinal -> {
                return allCategories[ordinal];
            }).toList();

            var sb = new StringBuilder();
            categories.forEach(category -> {
                switch (category) {
                    case SYSTEM_LOGGER -> {
                        sb.insert(0, category.asStringValue() + ":");
                    }
                    default -> {
                        sb.append(category.asStringValue()).append(',');
                    }
                }
            });

            var logRecords = MessageCategory.toLogRecords(categories);

            return new TestSpec(sb.toString(), logRecords);

        }).toList().forEach(testCases::add);

        testCases.addAll(test_valueOf_manual_test_cases());

        return testCases;
    }

    private static Collection<TestSpec> test_valueOf_manual_test_cases() {
        return List.of(
                new TestSpec("log", MessageCategory.SYSTEM_LOGGER),
                new TestSpec("log!", MessageCategory.values()),
                new TestSpec("log:!", MessageCategory.values()),
                new TestSpec("!", MessageCategory.toLogRecords(
                        SetBuilder.<MessageCategory>build()
                                .add(MessageCategory.values())
                                .remove(MessageCategory.SYSTEM_LOGGER)
                                .create()
                )),
                new TestSpec(","),
                new TestSpec(""),
                new TestSpec(",errors,,errors,", MessageCategory.ERRORS)
        );
    }

    private static Collection<String> test_valueOf_negative() {
        return List.of(
                "logerrors",
                "log:error",
                "log!:"
        );
    }

    record TestSpec(String logEnvStr, Set<LogRecord> expectedLogRecords) {

        TestSpec {
            Objects.requireNonNull(logEnvStr);
            Objects.requireNonNull(expectedLogRecords);
        }

        TestSpec(String logEnvStr, MessageCategory... categories) {
            this(logEnvStr, MessageCategory.toLogRecords(categories));
        }

        void run() {
            test(LogConfigParser.valueOf(logEnvStr), expectedLogRecords);
        }

        @Override
        public String toString() {
            return String.format("<%s> => %s", logEnvStr, expectedLogRecords.stream().map(Enum::name).sorted().toList());
        }
    }

    /**
     * Log message. Each enum item should wrap an invocation of one method
     * from an interface inherited from {@link Logger}.
     */
    private enum LogMessage {

        TRACE_STRING((TraceLogger logger) -> {
            logger.trace("Ecart foo");
        }),
        TRACE_THROWABLE((TraceLogger logger) -> {
            logger.trace(new Exception("Trace foo exception!"));
        }),
        TRACE_STRING_AND_THROWABLE((TraceLogger logger) -> {
            logger.trace(new Exception("Trace bar exception!"), "Ecart bar");
        }),
        TRACE_FORMAT((TraceLogger logger) -> {
            logger.trace("Ecart %s", "it");
        }),
        TRACE_FORMAT_AND_THROWABLE((TraceLogger logger) -> {
            logger.trace(new Exception("Trace exception again!"), "Ecart %s with", "it");
        }),

        SUMMARY((SummaryLogger logger) -> {
            var summary = new Summary();
            summary.put(StandardProperty.OUTPUT_BUNDLE, "sample");
            logger.summary(summary);
        }),
        SUMMARY_WARNING((SummaryLogger logger) -> {
            var summary = new Summary();
            summary.put(new Warning() {

                @Override
                public int ordinal() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Optional<String> valueFormatter() {
                    throw new UnsupportedOperationException();
                }

            }, "Foo configuration warning");
            logger.summary(summary);
        }),

        ERROR((ErrorLogger logger) -> {
            logger.reportError(new Exception("Kaput!"));
        }),
        ERROR_SELF_CONTAINED((ErrorLogger logger) -> {
            logger.reportError(new JPackageException("Cooked!"));
        }),

        PROGRESS_MESSAGE((ProgressLogger logger) -> {
            logger.progress("Start operation #1");
        }),
        PROGRESS_WARNING_EXCEPTION((ProgressLogger logger) -> {
            logger.progressWarning(new Exception("Minor issue"));
        }),
        PROGRESS_WARNING_MESSAGE_AND_EXCEPTION((ProgressLogger logger) -> {
            logger.progressWarning(new Exception("Minor issue"), "Ignoring the exception");
        }),
        PROGRESS_WARNING_MESSAGE((ProgressLogger logger) -> {
            logger.progressWarning("Ignoring the problem");
        }),

        RESOURCE((ResourceLogger logger) -> {
            logger.useResource("Using the resource");
        }),

        COMMMAND_BEFORE((CommandLogger logger) -> {
            logger.beforeCommandExecuted(false, "before -abc");
        }),
        COMMMAND_AFTER((CommandLogger logger) -> {
            logger.afterCommandExecuted(false, "after -abc", Optional.of(67L), Optional.of(0), "Hello\nGoodbye");
        }),
        COMMMAND_BEFORE_QUIET((CommandLogger logger) -> {
            logger.beforeCommandExecuted(true, "before -x -y");
        }),
        COMMMAND_AFTER_QUIET((CommandLogger logger) -> {
            logger.afterCommandExecuted(true, "after -x -y", Optional.of(321L), Optional.of(7), "Monday\nSunday");
        }),

        ;

        LogMessage(Consumer<? super AllLoggers> useLogger) {
            this.useLogger = Objects.requireNonNull(useLogger);
        }

        void applyTo(AllLoggers logger) {
            useLogger.accept(logger);
        }

        private final Consumer<? super AllLoggers> useLogger;
    }

    /**
     * Log records. Each enum item binds one or more invocations of logging methods
     * to a logger with specific configuration parameters.
     */
    private enum LogRecord {

        TRACE_STRING(console(TraceLogger.class, TraceLogger::create)),
        TRACE_THROWABLE(TRACE_STRING),
        TRACE_STRING_AND_THROWABLE(TRACE_STRING),
        TRACE_FORMAT(TRACE_STRING),
        TRACE_FORMAT_AND_THROWABLE(TRACE_STRING),
        TRACE_SYSTEM_LOGGER(systemLogger(TraceLogger.class, TraceLogger::create),
                LogMessage.TRACE_FORMAT,
                LogMessage.TRACE_FORMAT_AND_THROWABLE,
                LogMessage.TRACE_STRING,
                LogMessage.TRACE_STRING_AND_THROWABLE,
                LogMessage.TRACE_THROWABLE),

        SUMMARY(console(SummaryLogger.class, sink -> {
            return SummaryLogger.create(sink, true, false);
        })),
        SUMMARY_WARNING(console(SummaryLogger.class, sink -> {
            return SummaryLogger.create(sink, false, true);
        })),
        SUMMARY_SYSTEM_LOGGER(systemLogger(SummaryLogger.class, SummaryLogger::create),
                LogMessage.SUMMARY,
                LogMessage.SUMMARY_WARNING),

        ERROR(console(ErrorLogger.class, sink -> {
            return ErrorLogger.create(sink, false, false);
        })),
        ERROR_SELF_CONTAINED(ERROR),
        ERROR_SELF_CONTAINED_ALWAYS_PRINT_STACKTRACE(console(ErrorLogger.class, sink -> {
            return ErrorLogger.create(sink, true, false);
        }), LogMessage.ERROR_SELF_CONTAINED),
        ERROR_SYSTEM_LOGGER(systemLogger(ErrorLogger.class, ErrorLogger::create),
                LogMessage.ERROR,
                LogMessage.ERROR_SELF_CONTAINED),

        PROGRESS_MESSAGE(console(ProgressLogger.class, sink -> {
            return ProgressLogger.create(sink, true, false);
        })),
        PROGRESS_WARNING_EXCEPTION(console(ProgressLogger.class, sink -> {
            return ProgressLogger.create(sink, false, true);
        })),
        PROGRESS_WARNING_MESSAGE_AND_EXCEPTION(PROGRESS_WARNING_EXCEPTION),
        PROGRESS_WARNING_MESSAGE(PROGRESS_WARNING_EXCEPTION),
        PROGRESS_SYSTEM_LOGGER(systemLogger(ProgressLogger.class, ProgressLogger::create),
                LogMessage.PROGRESS_MESSAGE,
                LogMessage.PROGRESS_WARNING_EXCEPTION,
                LogMessage.PROGRESS_WARNING_MESSAGE,
                LogMessage.PROGRESS_WARNING_MESSAGE_AND_EXCEPTION),

        RESOURCE(console(ResourceLogger.class, ResourceLogger::create)),
        RESOURCE_SYSTEM_LOGGER(systemLogger(ResourceLogger.class, ResourceLogger::create),
                LogMessage.RESOURCE),

        COMMMAND(console(CommandLogger.class, sink -> {
            return CommandLogger.create(sink, false, false);
        }), LogMessage.COMMMAND_BEFORE),
        COMMMAND_PRINT_QUIET_AND_RESULT(console(CommandLogger.class, sink -> {
            return CommandLogger.create(sink, true, true);
        }),     LogMessage.COMMMAND_BEFORE,
                LogMessage.COMMMAND_AFTER,
                LogMessage.COMMMAND_BEFORE_QUIET,
                LogMessage.COMMMAND_AFTER_QUIET),
        COMMMAND_SYSTEM_LOGGER(systemLogger(CommandLogger.class, CommandLogger::create),
                LogMessage.COMMMAND_BEFORE,
                LogMessage.COMMMAND_AFTER,
                LogMessage.COMMMAND_BEFORE_QUIET,
                LogMessage.COMMMAND_AFTER_QUIET),
        ;

        LogRecord(CannedLogger<? super AllLoggers> cannedLogger, LogMessage... logRecordSources) {
            this.cannedLogger = Objects.requireNonNull(cannedLogger);
            if (logRecordSources.length > 0) {
                this.logRecordSources = Set.of(logRecordSources);
            } else {
                this.logRecordSources = Set.of(LogMessage.valueOf(name()));
            }
        }

        LogRecord(LogRecord other) {
            this(other.cannedLogger);
        }

        AllLoggers createLogger(StringWriter sink) {
            return cannedLogger.createLoggerWithSink(sink);
        }

        boolean isSystemLogger() {
            return cannedLogger.sinkType() == LogSink.SYSTEM_LOGGER;
        }

        static List<LogRecord> findLogRecords(LogMessage logRecordSrc, Collection<LogRecord> logRecords) {
            Objects.requireNonNull(logRecordSrc);
            Objects.requireNonNull(logRecords);

            var filteredLogRecords = logRecords.stream().filter(logRecord -> {
                return logRecord.logRecordSources.contains(logRecordSrc);
            }).toList();

            switch (filteredLogRecords.size()) {
                case 0, 1 -> {
                    return filteredLogRecords;
                }
                case 2 -> {
                    if (filteredLogRecords.getFirst().isSystemLogger() != filteredLogRecords.getLast().isSystemLogger()) {
                        return filteredLogRecords;
                    }
                }
            }

            throw new IllegalStateException(String.format(
                    "Multiple %s log records map into the %s log record source", filteredLogRecords, logRecordSrc));
        }

        private record CannedLogger<T extends Logger>(Class<T> type, Function<PrintWriter, T> ctor, LogSink sinkType) {

            CannedLogger {
                Objects.requireNonNull(type);
                Objects.requireNonNull(ctor);
                Objects.requireNonNull(sinkType);
            }

            AllLoggers createLoggerWithSink(StringWriter sink) {
                Objects.requireNonNull(sink);

                var emptyLogEnv = Options.concat();

                return AllLoggers.create(Stream.of(LoggerRole.values()).map(LoggerRole::logger).map(ov -> {
                    return ov.getFrom(emptyLogEnv);
                }).map(discardingLogger -> {
                    if (type.isInstance(discardingLogger)) {
                        return ctor.apply(new PrintWriter(sink));
                    } else {
                        return discardingLogger;
                    }
                }).toList());
            }
        }

        private static <T extends Logger> CannedLogger<T> console(
                Class<T> type, Function<ConsoleLogger, T> ctor) {
            Objects.requireNonNull(ctor);
            return new CannedLogger<>(type, sink -> {
                var timestampClock = FIXED_CONSOLE_TIMESTAMP;
                return ctor.apply(new ConsoleLogger(sink::println, sink::println, timestampClock));
            }, LogSink.CONSOLE);
        }

        private static <T extends Logger> CannedLogger<T> systemLogger(
                Class<T> type, Function<System.Logger, T> ctor) {
            Objects.requireNonNull(ctor);
            return new CannedLogger<>(type, sink -> {
                return ctor.apply(new SimpleSystemLogger(sink::println));
            }, LogSink.SYSTEM_LOGGER);
        }

        private final CannedLogger<? super AllLoggers> cannedLogger;
        private final Set<LogMessage> logRecordSources;
    }

    private enum MessageCategory {
        SUMMARY(LogRecord.SUMMARY),
        WARNINGS(
                LogRecord.SUMMARY_WARNING,
                LogRecord.PROGRESS_WARNING_MESSAGE,
                LogRecord.PROGRESS_WARNING_EXCEPTION,
                LogRecord.PROGRESS_WARNING_MESSAGE_AND_EXCEPTION),
        ERRORS(LogRecord.ERROR, LogRecord.ERROR_SELF_CONTAINED),
        PROGRESS(LogRecord.PROGRESS_MESSAGE),
        TRACE(Stream.of(
                add(
                        LogRecord.TRACE_STRING,
                        LogRecord.TRACE_STRING_AND_THROWABLE,
                        LogRecord.TRACE_FORMAT,
                        LogRecord.TRACE_FORMAT_AND_THROWABLE,
                        LogRecord.TRACE_THROWABLE,
                        LogRecord.COMMMAND_PRINT_QUIET_AND_RESULT
                ),
                replace(
                        LogRecord.ERROR_SELF_CONTAINED,
                        LogRecord.ERROR_SELF_CONTAINED_ALWAYS_PRINT_STACKTRACE
                ),
                replace(
                        LogRecord.COMMMAND,
                        LogRecord.COMMMAND_PRINT_QUIET_AND_RESULT
                )
        ).flatMap(x -> x)),
        RESOURCES(LogRecord.RESOURCE),
        TOOLS(LogRecord.COMMMAND),
        SYSTEM_LOGGER(Stream.of(LogRecord.values()).filter(LogRecord::isSystemLogger).map(AddLogRecordsMutator::new)) {
            @Override
            String asStringValue() {
                return "log";
            }
        },
        ;

        MessageCategory(Stream<LogRecordsMutator> mutators) {
            this.mutators = mutators.toList();
            if (this.mutators.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        MessageCategory(LogRecord... logRecords) {
            this(add(logRecords));
        }

        String asStringValue() {
            return name().toLowerCase();
        }

        private Stream<LogRecordsMutator> filterMutators(Class<? extends LogRecordsMutator> mutatorType) {
            return mutators.stream().filter(mutatorType::isInstance);
        }

        static Set<LogRecord> toLogRecords(Collection<MessageCategory> categories) {

            var logRecords = new HashSet<LogRecord>();

            for (var mutatorType : List.of(AddLogRecordsMutator.class, ReplaceLogRecordsMutator.class)) {
                categories.stream().map(category -> {
                    return category.filterMutators(mutatorType);
                }).flatMap(x -> x).forEach(mutator -> {
                    mutator.mutate(logRecords);
                });
            }

            return logRecords;
        }

        static Set<LogRecord> toLogRecords(MessageCategory... categories) {
            return toLogRecords(Set.of(categories));
        }

        private sealed interface LogRecordsMutator {
            void mutate(Set<LogRecord> logRecords);
        }

        private record AddLogRecordsMutator(LogRecord value) implements LogRecordsMutator {
            AddLogRecordsMutator {
                Objects.requireNonNull(value);
            }

            @Override
            public void mutate(Set<LogRecord> logRecords) {
                logRecords.add(value);
            }
        }

        private record ReplaceLogRecordsMutator(LogRecord from, LogRecord to) implements LogRecordsMutator {
            ReplaceLogRecordsMutator {
                Objects.requireNonNull(from);
                Objects.requireNonNull(to);
            }

            @Override
            public void mutate(Set<LogRecord> logRecords) {
                if (logRecords.contains(from)) {
                    logRecords.remove(from);
                    logRecords.add(to);
                }
            }
        }

        private static Stream<LogRecordsMutator> add(LogRecord... values) {
            return Stream.of(values).map(AddLogRecordsMutator::new);
        }

        private static Stream<LogRecordsMutator> replace(LogRecord from, LogRecord to) {
            return Stream.of(new ReplaceLogRecordsMutator(from, to));
        }

        private final List<LogRecordsMutator> mutators;
    }

    private record SimpleSystemLogger(Consumer<String> sink) implements System.Logger {

        SimpleSystemLogger {
            Objects.requireNonNull(sink);
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            var buf = new StringWriter();
            thrown.printStackTrace(new PrintWriter(buf));
            logImpl(level, String.format("%s: %s", msg, buf));
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            logImpl(level, String.format(format, params));
        }

        private void logImpl(Level level, String msg) {
            sink.accept(String.format("%s: %s: %s", SimpleSystemLogger.class.getSimpleName(), level, msg));
        }

    }

    private static Clock FIXED_CONSOLE_TIMESTAMP = Clock.fixed(Clock.systemDefaultZone().instant(), ZoneId.systemDefault());

    static {

        // Assert log records are unique.
        Stream.of(LogRecord.values()).map(logRecord -> {
            var sink = new StringWriter();
            var logger = logRecord.createLogger(sink);
            logRecord.logRecordSources.forEach(logRecordSource -> {
                logRecordSource.applyTo(logger);
            });
            var str = sink.toString();
            if (!str.isBlank()) {
                return str;
            } else {
                throw new IllegalArgumentException(String.format(
                        "Source log record results into a blank %s log record", logRecord));
            }
        }).collect(Collectors.toMap(x -> x, x -> x));

    }
}
