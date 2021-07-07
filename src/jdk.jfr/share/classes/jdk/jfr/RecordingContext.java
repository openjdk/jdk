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
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Callable;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.InheritableRecordingContextBinding;
import jdk.jfr.internal.NonInheritableRecordingContextBinding;
import jdk.jfr.internal.RecordingContextBinding;
import jdk.jfr.internal.RecordingContextEntry;

/**
 * Provide a RecordingContext to store variables that are passed around synchronous
 * asynchronous invocations, and that gets dumped into the JFR recording.
 *
 * @since 17
 */
public final class RecordingContext implements AutoCloseable {

    final RecordingContextBinding inheritableBinding;
    final RecordingContextBinding noninheritableBinding;

    RecordingContext(RecordingContextBinding inheritableBinding, RecordingContextBinding noninheritableBinding) {
        this.inheritableBinding = inheritableBinding;
        this.noninheritableBinding = noninheritableBinding;
    }

    // snapshot + run
    public static class Snapshot {
        final InheritableRecordingContextBinding inheritableRecordingContextBindings;

        Snapshot() {
            this.inheritableRecordingContextBindings = InheritableRecordingContextBinding.current();
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot();
    }

    public static <R> R callWithSnapshot(Callable<R> op, Snapshot s) throws Exception {
        InheritableRecordingContextBinding prev = InheritableRecordingContextBinding.current();
        if (prev == s.inheritableRecordingContextBindings) {
            return op.call();
        }
        try {
            InheritableRecordingContextBinding.setCurrent(s.inheritableRecordingContextBindings);
            return op.call();
        } finally {
            InheritableRecordingContextBinding.setCurrent(prev);
        }
    }

    public static void runWithSnapshot(Runnable op, Snapshot s) {
        InheritableRecordingContextBinding prev = InheritableRecordingContextBinding.current();
        if (prev == s.inheritableRecordingContextBindings) {
            op.run();
        }
        try {
            InheritableRecordingContextBinding.setCurrent(s.inheritableRecordingContextBindings);
            op.run();
        } finally {
            InheritableRecordingContextBinding.setCurrent(prev);
        }
    }

    // initialize
    public static class Builder {

        Builder() {}

        final Set<RecordingContextEntry> inheritableEntries = new HashSet<>();
        final Set<RecordingContextEntry> noninheritableEntries = new HashSet<>();

        // build and set current context
        public Builder where(RecordingContextKey key, String value) {
            Set<RecordingContextEntry> set = key.isInheritable() ?
                inheritableEntries : noninheritableEntries;
            
            if (set.contains(key)) {
                set.remove(key);
            }
            set.add(new RecordingContextEntry(key, value));

            return this;
        }

        public RecordingContext build() {
            return new RecordingContext(
                new InheritableRecordingContextBinding(inheritableEntries),
                new NonInheritableRecordingContextBinding(noninheritableEntries));
        }
    }

    public static Builder where(RecordingContextKey key, String value) {
        return new Builder().where(key, value);
    }

    // close
    @Override
    public void close() {
        inheritableBinding.close();
        noninheritableBinding.close();
    }
}
