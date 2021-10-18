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

import java.util.Objects;

import jdk.jfr.internal.RecordingContextBinding;
import jdk.jfr.internal.RecordingContextEntry;
import jdk.jfr.internal.RecordingContextFilterEngine;
import jdk.jfr.internal.RecordingContextPredicate;

/**
 * @since 17
 */
public final class RecordingContextFilter {

    private final RecordingContextPredicate predicate;

    RecordingContextFilter(RecordingContextPredicate predicate) {
        this.predicate = predicate;
    }

    public static class Config {

        Config() {}

        public static void setContextFilter(RecordingContextFilter filter) {
            RecordingContextFilterEngine.setContextFilter(filter, filter != null ? filter.predicate : null);
        }

        public static RecordingContextFilter getContextFilter() {
            return RecordingContextFilterEngine.getContextFilter();
        }

        public static RecordingContextFilter.Builder createFilter() {
            return new Builder();
        }
    }

    public static class Builder {

        private RecordingContextPredicate predicate;

        Builder() {
            this.predicate = RecordingContextPredicate.NOOP;
        }

        public Builder hasContext() {
            predicate = predicate.and(
                "[HasContextPredicate]",
                b -> {
                    return b != null;
                });
            return this;
        }

        public Builder hasNoContext() {
            predicate = predicate.and(
                "[HasNoContextPredicate]",
                b -> {
                    return b == null;
                });
            return this;
        }

        public Builder hasKey(RecordingContextKey key) {
            Objects.requireNonNull(key);
            predicate = predicate.and(
                String.format("[HasKeyPredicate: key=%s]", key.name()),
                b -> {
                    if (b == null) {
                        // If there s no context, we do not filter on this predicate
                        return true;
                    }
                    for (RecordingContextEntry e : b.entries()) {
                        if (Objects.equals(e.key(), key)) {
                            return true;
                        }
                    }
                    return false;
                });
            return this;
        }

        public Builder hasEntry(RecordingContextKey key, String value) {
            Objects.requireNonNull(key);
            predicate = predicate.and(
                String.format("[HasEntryPredicate: key=%s, value=%s]", key.name(), value),
                b -> {
                    if (b == null) {
                        // If there s no context, we do not filter on this predicate
                        return true;
                    }
                    for (RecordingContextEntry e : b.entries()) {
                        if (Objects.equals(e.key(), key) && Objects.equals(e.value(), value)) {
                            return true;
                        }
                    }
                    return false;
                });
            return this;
        }

        public RecordingContextFilter build() {
            return new RecordingContextFilter(predicate);
        }
    }
}
