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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Generic help formatter.
 */
final class HelpFormatter {

    private HelpFormatter(List<OptionGroup> optionGroups, OptionGroupFormatter formatter) {
        this.optionGroups = Objects.requireNonNull(optionGroups);
        this.formatter = Objects.requireNonNull(formatter);
    }

    void format(Consumer<CharSequence> sink) {
        for (var group : optionGroups) {
            formatter.format(group, sink);
        }
    }

    static Builder build() {
        return new Builder();
    }


    static final class Builder {

        private Builder() {
        }

        HelpFormatter create() {
            return new HelpFormatter(groups, validatedGroupFormatter());
        }

        Builder groups(Collection<OptionGroup> v) {
            groups.addAll(v);
            return this;
        }

        Builder groups(OptionGroup... v) {
            return groups(List.of(v));
        }

        Builder groupFormatter(OptionGroupFormatter v) {
            groupFormatter = v;
            return this;
        }

        private OptionGroupFormatter validatedGroupFormatter() {
            return Optional.ofNullable(groupFormatter).orElseGet(Builder::createConsoleFormatter);
        }

        private static OptionGroupFormatter createConsoleFormatter() {
            return new ConsoleOptionGroupFormatter(new ConsoleOptionFormatter(2, 10));
        }

        private final List<OptionGroup> groups = new ArrayList<>();
        private OptionGroupFormatter groupFormatter;
    }


    interface OptionFormatter {

        public default void format(OptionSpec<?> optionSpec, Consumer<CharSequence> sink) {
            format(optionSpec.names().stream().map(OptionName::formatForCommandLine).collect(Collectors.joining(" ")),
                    optionSpec.valuePattern(),
                    optionSpec.description(), sink);
        }

        void format(String optionNames, Optional<String> valuePattern, String description, Consumer<CharSequence> sink);
    }

    interface OptionGroupFormatter {

        default void format(OptionGroup group, Consumer<CharSequence> sink) {
            formatHeader(group.name(), sink);
            formatBody(group.options(), sink);
        }

        void formatHeader(String gropName, Consumer<CharSequence> sink);

        void formatBody(Iterable<? extends OptionSpec<?>> optionSpecs, Consumer<CharSequence> sink);
    }


    record ConsoleOptionFormatter(int nameOffset, int descriptionOffset) implements OptionFormatter {

        @Override
        public void format(String optionNames, Optional<String> valuePattern, String description, Consumer<CharSequence> sink) {
            sink.accept(" ".repeat(nameOffset));
            sink.accept(optionNames);
            valuePattern.map(v -> " " + v).ifPresent(sink);
            eol(sink);
            final var descriptionOffsetStr = " ".repeat(descriptionOffset);
            Stream.of(description.split("\\R")).map(line -> {
                return descriptionOffsetStr + line;
            }).forEach(line -> {
                sink.accept(line);
                eol(sink);
            });
        }
    }


    record ConsoleOptionGroupFormatter(OptionFormatter optionFormatter) implements OptionGroupFormatter {

        ConsoleOptionGroupFormatter {
            Objects.requireNonNull(optionFormatter);
        }

        @Override
        public void formatHeader(String groupName, Consumer<CharSequence> sink) {
            Objects.requireNonNull(groupName);
            eol(sink);
            sink.accept(groupName + ":");
            eol(sink);
        }

        @Override
        public void formatBody(Iterable<? extends OptionSpec<?>> optionSpecs, Consumer<CharSequence> sink) {
            optionSpecs.forEach(optionSpec -> {
                optionFormatter.format(optionSpec, sink);
            });
        }
    }


    record OptionGroup(String name, List<? extends OptionSpec<?>> options) {

        OptionGroup {
            Objects.requireNonNull(name);
            Objects.requireNonNull(options);
        }
    }


    static Consumer<CharSequence> eol(Consumer<CharSequence> sink) {
        sink.accept(System.lineSeparator());
        return sink;
    }


    private final List<OptionGroup> optionGroups;
    private final OptionGroupFormatter formatter;
}
