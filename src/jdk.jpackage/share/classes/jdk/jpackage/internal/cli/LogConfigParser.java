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
package jdk.jpackage.internal.cli;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.log.LogEnvironment;
import jdk.jpackage.internal.log.LogEnvironment.Builder;
import jdk.jpackage.internal.log.LogEnvironment.LogSink;
import jdk.jpackage.internal.log.LoggerRole;
import jdk.jpackage.internal.util.SetBuilder;

final public class LogConfigParser {

    static Builder valueOf(String str) {
        return buildFromCategories(tokenize(str));
    }

    public static Set<MessageCategory> tokenize(String str) {
        Objects.requireNonNull(str);

        var ex = new IllegalArgumentException(String.format("Inavlid value: [%s]", str));

        var categories = new HashSet<MessageCategory>();

        var idx = 0;

        if (str.startsWith(MessageCategory.SYSTEM_LOGGER.asStringValue())) {
            idx += MessageCategory.SYSTEM_LOGGER.asStringValue().length();
            categories.add(MessageCategory.SYSTEM_LOGGER);
            if (str.startsWith(":", idx)) {
                idx++;
            } else if (idx < str.length() && str.charAt(idx) != '!') {
                throw ex;
            }
        }

        Consumer<MessageCategory> categoryConsumer = categories::add;

        if (str.startsWith("!", idx)) {
            categories.addAll(CONSOLE_CATEGORIES.values());
            idx++;
            categoryConsumer = categories::remove;
        }

        Stream.of(str.substring(idx).split(",")).filter(Predicate.not(String::isEmpty)).map(v -> {
            var category = CONSOLE_CATEGORIES.get(v);
            if (category != null) {
                return category;
            } else {
                throw ex;
            }
        }).forEach(categoryConsumer);

        return categories;
    }

    static Builder defaultVerbose() {
        return buildFromCategories(SetBuilder.<MessageCategory>build()
                .add(MessageCategory.values())
                .remove(MessageCategory.TRACE, MessageCategory.SYSTEM_LOGGER)
                .create());
    }

    static Builder quiet() {
        return buildFromCategories(MessageCategory.ERRORS, MessageCategory.WARNINGS);
    }

    private LogConfigParser() {
    }

    public enum MessageCategory {
        SUMMARY {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.SUMMARY_LOGGER, LogSink.CONSOLE);
                builder.printSummaryInConsole(true);
            }
        },
        WARNINGS {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.SUMMARY_LOGGER, LogSink.CONSOLE);
                builder.enable(LoggerRole.PROGRESS_LOGGER, LogSink.CONSOLE);
                builder.printSummaryWarningsInConsole(true);
                builder.printProgressWarningsInConsole(true);
            }
        },
        ERRORS {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.ERROR_LOGGER, LogSink.CONSOLE);
                builder.printFailedCommandOutputInConsole(true);
            }
        },
        PROGRESS {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.PROGRESS_LOGGER, LogSink.CONSOLE);
                builder.printProgressInConsole(true);
            }
        },
        TRACE {
            protected void applyTo(Builder builder) {
                TOOLS.applyTo(builder);
                builder.enable(LoggerRole.TRACE_LOGGER, LogSink.CONSOLE);
                builder.printErrorStackTraceInConsole(true);
                builder.printCommandOutputInConsole(true);
                builder.printQuietCommands(true);
            }
        },
        RESOURCES {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.RESOURCE_LOGGER, LogSink.CONSOLE);
            }
        },
        TOOLS {
            protected void applyTo(Builder builder) {
                builder.enable(LoggerRole.COMMAND_LOGGER, LogSink.CONSOLE);
            }
        },
        SYSTEM_LOGGER {
            protected void applyTo(Builder builder) {
                for (var role : LoggerRole.values()) {
                    builder.enable(role, LogSink.SYSTEM_LOGGER);
                }
            }

            @Override
            String asStringValue() {
                return "log";
            }

            @Override
            boolean console() {
                return false;
            }
        },
        ;

        protected abstract void applyTo(Builder builder);

        String asStringValue() {
            return name().toLowerCase();
        }

        boolean console() {
            return true;
        }
    }

    private static Builder buildFromCategories(Set<MessageCategory> categories) {
        var builder = LogEnvironment.build();

        categories.forEach(c -> {
            c.applyTo(builder);
        });

        return builder;
    }

    private static Builder buildFromCategories(MessageCategory... categories) {
        return buildFromCategories(Set.of(categories));
    }

    private final static Map<String, MessageCategory> CONSOLE_CATEGORIES = Stream.of(MessageCategory.values())
            .filter(MessageCategory::console)
            .collect(Collectors.toUnmodifiableMap(MessageCategory::asStringValue, x -> x));
}
