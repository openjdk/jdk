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
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.StringPool;

import static jdk.internal.javac.PreviewFeature.Feature.SCOPE_LOCALS;

/**
 * Provide a RecordingContext to store variables that are passed around synchronous
 * asynchronous invocations, and that gets dumped into the JFR recording.
 *
 * @since 17
 */
@jdk.internal.javac.PreviewFeature(feature=SCOPE_LOCALS)
public final class RecordingContext {

    // final static StringPool stringPool = new StringPool();

    final String name;
    final ScopeLocal<Entry> local;

    private RecordingContext(String name, boolean isInheritable) {
        this.name = name;
        this.local = isInheritable ? ScopeLocal.inheritableForType(Entry.class) : ScopeLocal.forType(Entry.class);
    }

    public final int hashCode() { return local.hashCode(); }

    private static class Entry {
        public final String name;
        public final String value;

        public Entry(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class Snapshot {

        final ScopeLocal.Snapshot snapshot;

        Snapshot(ScopeLocal.Snapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot);
        }

        public final void foreach(BiConsumer<String, String> consumer) {
            snapshot.foreach((v) -> {
                if (v instanceof Entry) {
                    Entry entry = (Entry)v;
                    consumer.accept(entry.name, entry.value);
                }
            });
        }
    }

    public static final class Carrier {

        final ScopeLocal.Carrier carrier;

        Carrier(ScopeLocal.Carrier carrier) {
            this.carrier = carrier;
        }

        public final Carrier where(RecordingContext key, String value) {
            return new Carrier(carrier.where(key.local, new Entry(key.name, value)));
        }

        public final String get(RecordingContext key) {
            return carrier.get(key.local).value;
        }

        public final <R> R call(Callable<R> op) throws Exception {
            return carrier.call(op);
        }

        public final <R> R callOrElse(Callable<R> op,
                                      Function<? super Exception, ? extends R> handler) {
            return carrier.callOrElse(op, handler);
        }

        public final void run(Runnable op) {
            carrier.run(op);
        }
    }

    public static Carrier where(RecordingContext key, String value) {
        return new Carrier(ScopeLocal.where(key.local, new Entry(key.name, value)));
    }

    public static <U> U where(RecordingContext key, String value, Callable<U> op) throws Exception {
        return ScopeLocal.where(key.local, new Entry(key.name, value), op);
    }

    public static void where(RecordingContext key, String value, Runnable op) {
        ScopeLocal.where(key.local, new Entry(key.name, value), op);
    }

    public static <R> R callWithSnapshot(Callable<R> op, Snapshot s) throws Exception {
        return ScopeLocal.callWithSnapshot(op, s.snapshot);
    }

    public static void runWithSnapshot(Runnable op, Snapshot s) {
        ScopeLocal.runWithSnapshot(op, s.snapshot);
    }

    public static RecordingContext forName(String name) {
        return new RecordingContext(name, false);
    }

    public static RecordingContext inheritableForName(String name) {
        return new RecordingContext(name, true);
    }

    public String get() {
        return local.get().value;
    }

    public boolean isBound() {
        return local.isBound();
    }

    public String orElse(String other) {
        return local.orElse(new Entry(name, other)).value;
    }

    public <X extends Throwable> String orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return local.orElseThrow(exceptionSupplier).value;
    }

    public static Snapshot snapshot() {
        var snapshot = ScopeLocal.snapshot();
        if (snapshot == null) {
            return null;
        }
        return new Snapshot(snapshot);
    }

    // Called by JVM
    private static void walkSnapshot(long callback) {
        var snapshot = snapshot();
        if (snapshot == null) {
            return;
        }
        snapshot.foreach((k, v) -> {
            JVM.getJVM().invokeWalkSnapshotCallback(callback, StringPool.addStringWithoutPreCache(k), StringPool.addStringWithoutPreCache(v));
        });
    }
}
