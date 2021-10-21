/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr;

import java.util.HashMap;
import java.util.Objects;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.jfr.internal.RecordingContextBinding;
import jdk.jfr.internal.RecordingContextFilterEngine;
import jdk.jfr.internal.RecordingContextPredicate;

/**
 * @since 17
 */
public final class RecordingContextFilter {

    private final Map<Long, RecordingContextPredicate> predicates;

    RecordingContextFilter(Map<Long, RecordingContextPredicate> predicates) {
        this.predicates = predicates;
    }

    public static class Config {

        Config() {}

        public static void setContextFilter(RecordingContextFilter filter) {
            RecordingContextFilterEngine.setContextFilter(filter, filter != null ? filter.predicates : null);
        }

        public static RecordingContextFilter contextFilter() {
            return RecordingContextFilterEngine.contextFilter();
        }

        public static RecordingContextFilter.Builder createFilter() {
            return new Builder();
        }
    }

    public static class Builder {

        private final Map<Long, PerTypeBuilder> builders;

        Builder() {
            this.builders = new HashMap<>();
            // There should be at least the default one
            this.builders.put(-1L, new PerTypeBuilder());
        }

        public Builder forAllTypes(Consumer<PerTypeBuilder> callback) {
            builders.putIfAbsent(-1L, new PerTypeBuilder());
            callback.accept(builders.get(-1L));
            return this;
        }

        public Builder forType(EventType type, Consumer<PerTypeBuilder> callback) {
            builders.putIfAbsent(type.getId(), new PerTypeBuilder());
            callback.accept(builders.get(type.getId()));
            return this;
        }

        public RecordingContextFilter build() {
            return new RecordingContextFilter(
                builders.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), e.getValue().predicate))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
    }

    public static class PerTypeBuilder {

        private RecordingContextPredicate predicate;

        PerTypeBuilder() {
            this.predicate = RecordingContextPredicate.NOOP;
        }

        public PerTypeBuilder reset() {
            predicate = RecordingContextPredicate.NOOP;
            return this;
        }

        public PerTypeBuilder hasContext() {
            predicate = predicate.and(
                "[HasContextPredicate]",
                b -> {
                    return b != null;
                });
            return this;
        }

        public PerTypeBuilder hasNoContext() {
            predicate = predicate.and(
                "[HasNoContextPredicate]",
                b -> {
                    return b == null;
                });
            return this;
        }

        public PerTypeBuilder hasKey(RecordingContextKey key) {
            Objects.requireNonNull(key);
            predicate = predicate.and(
                String.format("[HasKeyPredicate: key=%s]", key.name()),
                b -> {
                    return b != null && b.entries().containsKey(key);
                });
            return this;
        }

        public PerTypeBuilder hasEntry(RecordingContextKey key, String value) {
            Objects.requireNonNull(key);
            predicate = predicate.and(
                String.format("[HasEntryPredicate: key=%s, value=%s]", key.name(), value),
                b -> {
                    return b != null && b.entries().containsKey(key) && Objects.equals(b.entries().get(key), value);
                });
            return this;
        }
    }
}
