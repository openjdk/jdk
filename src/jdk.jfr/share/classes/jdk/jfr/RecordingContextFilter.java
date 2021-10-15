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
import java.util.function.Predicate;

import jdk.jfr.internal.RecordingContextBinding;
import jdk.jfr.internal.RecordingContextEntry;
import jdk.jfr.internal.RecordingContextFilterEngine;

/**
 * @since 17
 */
public final class RecordingContextFilter {

    private final Predicate<RecordingContextBinding> predicate;

    RecordingContextFilter(Predicate<RecordingContextBinding> predicate) {
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

        private Predicate<RecordingContextBinding> predicate;

        Builder() {
            this.predicate = new NoopPredicate();
        }

        public Builder hasKey(RecordingContextKey key) {
            predicate = new AndPredicate(predicate, new HasKeyPredicate(key));
            return this;
        }

        private static record HasKeyPredicate(RecordingContextKey key)
                implements Predicate<RecordingContextBinding> {

            HasKeyPredicate {
                Objects.requireNonNull(key);
            }

            @Override
            public boolean test(RecordingContextBinding b) {
                for (RecordingContextEntry e : b.entries()) {
                    if (Objects.equals(e.key(), key)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return String.format("[HasKeyPredicate: key=%s]", key.name());
            }
        }

        public Builder hasEntry(RecordingContextKey key, String value) {
            predicate = new AndPredicate(predicate, new HasEntryPredicate(key, value));
            return this;
        }

        private static record HasEntryPredicate(RecordingContextKey key, String value)
                implements Predicate<RecordingContextBinding> {

            HasEntryPredicate {
                Objects.requireNonNull(key);
            }

            @Override
            public boolean test(RecordingContextBinding b) {
                for (RecordingContextEntry e : b.entries()) {
                    if (Objects.equals(e.key(), key) && Objects.equals(e.value(), value)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return String.format("[HasEntryPredicate: key=%s, value=%s]", key.name(), value);
            }
        }

        private static record AndPredicate(Predicate<RecordingContextBinding> predicateLeft, Predicate<RecordingContextBinding> predicateRight)
                implements Predicate<RecordingContextBinding> {

            AndPredicate {
                Objects.requireNonNull(predicateLeft);
                Objects.requireNonNull(predicateRight);
            }

            @Override
            public boolean test(RecordingContextBinding b) {
                return predicateLeft.test(b) && predicateRight.test(b);
            }

            @Override
            public String toString() {
                return String.format("[AndPredicate: %s AND %s]", predicateLeft.toString(), predicateRight.toString());
            }
        }

        private static record NoopPredicate()
                implements Predicate<RecordingContextBinding> {

            @Override
            public boolean test(RecordingContextBinding b) {
                return true;
            }

            @Override
            public String toString() {
                return String.format("[NoopPredicate]");
            }
        }

        public RecordingContextFilter build() {
            return new RecordingContextFilter(predicate);
        }
    }
}
