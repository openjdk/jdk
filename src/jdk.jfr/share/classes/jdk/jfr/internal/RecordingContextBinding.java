/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import jdk.jfr.RecordingContextKey;
import jdk.jfr.internal.JVM;

/**
 * @since 17
 */
public final class RecordingContextBinding implements AutoCloseable {

    private final static ThreadLocal<RecordingContextBinding> current = ThreadLocal.withInitial(() -> null);

    private final static Cleaner cleaner = Cleaner.create();

    private final static List<Consumer<RecordingContextBinding>> contextChangeListeners = new ArrayList<>();

    // double linked-list of contexts
    private final RecordingContextBinding previous;
    private RecordingContextBinding next;

    private final Map<RecordingContextKey, String> entries;

    private final NativeBindingWrapper nativeWrapper;

    private final Cleanable closer;

    public RecordingContextBinding(Map<RecordingContextKey, String> entries) {
        Objects.requireNonNull(entries);

        this.previous = current.get();
        if (this.previous != null) {
            if (this.previous.next != null) {
                // we didn't peel the onion properly, make sure any outer layer is closed properly
                this.previous.next.close();
            }
            this.previous.next = this;
        }

        this.entries = Collections.unmodifiableMap(entries);

        this.nativeWrapper = new NativeBindingWrapper(
            this.previous != null ? this.previous.nativeWrapper : null, entries);

        this.closer = cleaner.register(
            this, new NativeBindingCleaner(this.nativeWrapper));

        setCurrent(this);
    }

    public static RecordingContextBinding current() {
        return current.get();
    }

    public RecordingContextBinding previous() {
        return previous;
    }

    public Map<RecordingContextKey, String> entries() {
        return entries;
    }

    public boolean containsKey(RecordingContextKey key) {
        return entries.containsKey(Objects.requireNonNull(key));

    }

    public static void addContextChangeListener(Consumer<RecordingContextBinding> c) {
        contextChangeListeners.add(c);
    }

    public static void removeContextChangeListener(Consumer<RecordingContextBinding> c) {
        contextChangeListeners.remove(c);
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

        setCurrent(previous);
    }

    /**
     * Used for snapshotting
     */
    public static void setCurrent(RecordingContextBinding context) {
        NativeBindingWrapper.setCurrent(context != null ? context.nativeWrapper : null);
        current.set(context);

        for (Consumer<RecordingContextBinding> listener : contextChangeListeners) {
            listener.accept(context);
        }
    }

    static class NativeBindingWrapper implements AutoCloseable {

        private final long id;
        private final NativeBindingWrapper previous;

        private boolean closed = false;

        public NativeBindingWrapper(
                NativeBindingWrapper previous,
                Map<RecordingContextKey, String> entries) {
            this.previous = previous;

            // convert entries to an array of contiguous pair of String
            //  [ "key1", "value1", "key2", "value2", ... ]
            final IntegerHolder h = new IntegerHolder();
            String[] entriesAsStrings = new String[entries.size() * 2];
            entries.entrySet().stream()
                .forEach(e -> {
                    entriesAsStrings[h.i++] = e.getKey().name();
                    entriesAsStrings[h.i++] = e.getValue();
                });

            this.id = JVM.getJVM().recordingContextNew(
                            Objects.requireNonNull(entriesAsStrings));
        }

        private static class IntegerHolder {
            public int i = 0;
        }

        public boolean containsKey(String key) {
            return JVM.getJVM().recordingContextContainsKey(id, Objects.requireNonNull(key));
        }

        public static void setCurrent(NativeBindingWrapper context) {
            if (context == null) {
                JVM.getJVM().recordingContextSet(0);
            } else {
                if (context.closed) {
                    throw new IllegalStateException("context is closed");
                }
                JVM.getJVM().recordingContextSet(context.id);
            }
        }

        @Override
        public void close() {
            if (!closed) {
                JVM.getJVM().recordingContextDelete(id);
                closed = true;
            }
        }
    }

    static class NativeBindingCleaner implements Runnable {

        private final NativeBindingWrapper nativeWrapper;

        public NativeBindingCleaner(NativeBindingWrapper nativeWrapper) {
            this.nativeWrapper = nativeWrapper;
        }

        public void run() {
            this.nativeWrapper.close();
        }
    }
}
