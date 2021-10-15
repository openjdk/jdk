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

package jdk.jfr.internal;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import jdk.jfr.RecordingContextKey;
import jdk.jfr.internal.JVM;

/**
 * @since 17
 */
public final class RecordingContextBinding implements AutoCloseable {

    private final static ThreadLocal<RecordingContextBinding> current = ThreadLocal.withInitial(() -> null);

    private final static Cleaner cleaner = Cleaner.create();

    // double linked-list of contexts
    private final RecordingContextBinding previous;
    private RecordingContextBinding next;

    private boolean matchesFilter;

    private final Set<RecordingContextEntry> entries;

    private final NativeBindingWrapper nativeWrapper;

    private final Cleanable closer;

    public RecordingContextBinding(Set<RecordingContextEntry> entries) {
        this.previous = current.get();
        if (this.previous != null) {
            if (this.previous.next != null) {
                // we didn't peel the onion properly, make sure any outer layer is closed properly
                this.previous.next.close();
            }
            this.previous.next = this;
        }

        this.entries = Collections.unmodifiableSet(entries);

        this.matchesFilter = RecordingContextFilterEngine.matches(this);

        this.nativeWrapper = new NativeBindingWrapper(
            this.previous != null ? this.previous.nativeWrapper : null, entries, matchesFilter);

        this.closer = cleaner.register(
            this, new NativeBindingCleaner(this.nativeWrapper));

        current.set(this);
    }

    /**
     * Used for snapshotting
     */
    public static void setCurrent(RecordingContextBinding context) {
        NativeBindingWrapper.setCurrent(context != null ? context.nativeWrapper : null);
        current.set(context);
    }

    public static RecordingContextBinding current() {
        return current.get();
    }

    private RecordingContextBinding previous() {
        return previous;
    }

    public Set<RecordingContextEntry> entries() {
        return entries;
    }

    public boolean containsKey(RecordingContextKey key) {
        return nativeWrapper.containsKey(Objects.requireNonNull(key).name());
    }

    @Override
    public void close() {
        // close any outer layer of the onion
        if (next != null) {
            // we didn't peel the onion properly, make sure any outer layer is closed properly
            next.close();
            next = null;
        }

        nativeWrapper.close();

        if (previous != null) {
            previous.next = null;
        }

        current.set(previous);
    }

    static class NativeBindingWrapper implements AutoCloseable {

        private final long id;
        private final NativeBindingWrapper previous;
        private final boolean matchesFilter;

        public NativeBindingWrapper(
                NativeBindingWrapper previous,
                Set<RecordingContextEntry> entries,
                boolean matchesFilter) {
            this.previous = previous;
            this.matchesFilter = matchesFilter;

            // convert entries to an array of contiguous pair of String
            //  [ "key1", "value1", "key2", "value2", ... ]
            String[] entriesAsStrings = new String[entries.size() * 2];
            int i = 0;
            for (RecordingContextEntry entry : entries) {
                entriesAsStrings[i * 2 + 0] = entry.key().name();
                entriesAsStrings[i * 2 + 1] = entry.value();
                i += 1;
            }

            this.id = JVM.getJVM().recordingContextNew(
                            Objects.requireNonNull(entriesAsStrings), matchesFilter);

            setCurrent(this);
        }

        public boolean containsKey(String key) {
            return JVM.getJVM().recordingContextContainsKey(id, Objects.requireNonNull(key));
        }

        public static void setCurrent(NativeBindingWrapper context) {
            JVM.getJVM().recordingContextSet(context != null ? context.id : 0);
        }

        @Override
        public void close() {
            setCurrent(previous);
        }

        public void delete() {
            JVM.getJVM().recordingContextDelete(id);
        }
    }

    static class NativeBindingCleaner implements Runnable {

        private final NativeBindingWrapper nativeWrapper;

        public NativeBindingCleaner(NativeBindingWrapper nativeWrapper) {
            this.nativeWrapper = nativeWrapper;
        }

        public void run() {
            this.nativeWrapper.delete();
        }
    }
}
