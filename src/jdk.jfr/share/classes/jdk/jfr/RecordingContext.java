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
import java.util.Collections;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.RecordingContextBinding;
import jdk.jfr.internal.RecordingContextEntry;
import jdk.jfr.internal.RecordingContextFilterEngine;

/**
 * Provide a RecordingContext to store variables that are passed around synchronous
 * asynchronous invocations, and that gets dumped into the JFR recording.
 *
 * @since 17
 */
public final class RecordingContext implements AutoCloseable {

    private final RecordingContextBinding binding;

    RecordingContext(RecordingContextBinding binding) {
        this.binding = binding;
    }

    // snapshot + run
    public static class Snapshot {

        private final RecordingContextBinding binding;

        Snapshot() {
            this.binding = RecordingContextBinding.current();
        }

        public Activation activate() {
            return new Activation(this);
        }
    }

    public static class Activation implements AutoCloseable {

        private final RecordingContextBinding prev;
        private final boolean set;

        Activation(Snapshot snapshot) {
            this.prev = RecordingContextBinding.current();
            if (this.prev == snapshot.binding) {
                set = false;
            } else {
                RecordingContextBinding.setCurrent(snapshot.binding);
                set = true;
            }
        }

        @Override
        public void close() {
            if (set) {
                RecordingContextBinding.setCurrent(prev);
            }
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot();
    }

    @SuppressWarnings("try")
    public static <R> R callWithSnapshot(Callable<R> op, Snapshot s) throws Exception {
        try (Activation a = s.activate()) {
            return op.call();
        }
    }

    @SuppressWarnings("try")
    public static void runWithSnapshot(Runnable op, Snapshot s) {
        try (Activation a = s.activate()) {
            op.run();
        }
    }

    // initialize
    public static class Builder {

        final Set<RecordingContextEntry> entries;

        Builder() {
            entries = new LinkedHashSet<>();

            RecordingContextBinding current;
            if ((current = RecordingContextBinding.current()) != null) {
                entries.addAll(current.entries());
            }
        }

        // build and set current context
        public Builder where(RecordingContextKey key, String value) {
            RecordingContextEntry entry = new RecordingContextEntry(key, value);
            entries.remove(entry);
            entries.add(entry);

            return this;
        }

        public RecordingContext build() {
            return new RecordingContext(new RecordingContextBinding(entries));
        }
    }

    public static Builder where(RecordingContextKey key, String value) {
        return new Builder().where(key, value);
    }

    // close
    @Override
    public void close() {
        binding.close();
    }
}
