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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.log.LogEnvironment;
import jdk.jpackage.internal.log.LogEnvironment.Builder;
import jdk.jpackage.internal.log.LogEnvironment.LogSink;
import jdk.jpackage.internal.log.LoggerRole;
import jdk.jpackage.internal.util.SetBuilder;

public final class LogConfigParser {

    static Builder valueOf(String str) {
        return buildFromCategories(tokenize(str));
    }

    public static Set<MessageCategory> tokenize(String str) {
        Objects.requireNonNull(str);

        Supplier<IllegalArgumentException> ex = () -> {
            return new IllegalArgumentException(String.format("Invalid value: [%s]", str));
        };

        var groupCategories = new BitSet(MessageCategory.values().length);
        var enableCategories = new BitSet(MessageCategory.values().length);
        var disableCategories = new BitSet(MessageCategory.values().length);

        Stream.of(str.split("(?<=.),")).filter(Predicate.not(String::isEmpty)).forEach(v -> {
            if (v.charAt(0) == '-') {
                var category = CONSOLE_CATEGORIES.get(v.substring(1));
                if (category == null) {
                    throw ex.get();
                } else {
                    disableCategories.set(category.ordinal());
                }
            } else {
                Optional.ofNullable(GROUPS.get(v)).ifPresentOrElse(categoryGroup -> {
                    for (var category : categoryGroup) {
                        groupCategories.set(category.ordinal());
                    }
                }, () -> {
                    var category = CONSOLE_CATEGORIES.get(v);
                    if (category == null) {
                        throw ex.get();
                    } else {
                        enableCategories.set(category.ordinal());
                    }
                });
            }
        });

        var categories = new HashSet<MessageCategory>();

        for (var category : MessageCategory.values()) {
            if (enableCategories.get(category.ordinal()) ||
                    (groupCategories.get(category.ordinal()) && !disableCategories.get(category.ordinal()))) {
                categories.add(category);
            }
        }

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
        ERRORS {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.ERROR_LOGGER, LogSink.CONSOLE);
                builder.printFailedCommandOutputInConsole(true);
            }
        },
        PROGRESS {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.PROGRESS_LOGGER, LogSink.CONSOLE);
                builder.printProgressInConsole(true);
            }
        },
        RESOURCES {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.RESOURCE_LOGGER, LogSink.CONSOLE);
            }
        },
        SUMMARY {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.SUMMARY_LOGGER, LogSink.CONSOLE);
                builder.printSummaryInConsole(true);
            }
        },
        SYSTEM_LOGGER {
            public void applyTo(Builder builder) {
                for (var role : LoggerRole.values()) {
                    builder.enable(role, LogSink.SYSTEM_LOGGER);
                }
            }

            @Override
            String asStringValue() {
                return "log";
            }

            @Override
            boolean isConsole() {
                return false;
            }
        },
        TOOLS {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.COMMAND_LOGGER, LogSink.CONSOLE);
            }
        },
        TRACE {
            public void applyTo(Builder builder) {
                TOOLS.applyTo(builder);
                builder.enable(LoggerRole.TRACE_LOGGER, LogSink.CONSOLE);
                builder.printErrorStackTraceInConsole(true);
                builder.printCommandOutputInConsole(true);
                builder.printQuietCommands(true);
            }
        },
        WARNINGS {
            public void applyTo(Builder builder) {
                builder.enable(LoggerRole.SUMMARY_LOGGER, LogSink.CONSOLE);
                builder.enable(LoggerRole.PROGRESS_LOGGER, LogSink.CONSOLE);
                builder.printSummaryWarningsInConsole(true);
                builder.printProgressWarningsInConsole(true);
            }
        },
        ;

        public abstract void applyTo(Builder builder);

        String asStringValue() {
            return name().toLowerCase();
        }

        boolean isConsole() {
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

    private static final Map<String, MessageCategory> CONSOLE_CATEGORIES = Stream.of(MessageCategory.values())
            .collect(Collectors.toUnmodifiableMap(MessageCategory::asStringValue, x -> x));

    private static final Map<String, Set<MessageCategory>> GROUPS = Map.ofEntries(
            Map.entry("all", Set.of(MessageCategory.values())),
            Map.entry("console", Stream.of(MessageCategory.values())
                    .filter(MessageCategory::isConsole)
                    .collect(Collectors.toUnmodifiableSet()))
    );
}
