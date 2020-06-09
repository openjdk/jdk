/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package jdk.internal.vm.compiler.libgraal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Represents a single libgraal isolate. All {@link LibGraalObject}s have a {@link LibGraalIsolate}
 * context.
 */
public final class LibGraalIsolate {

    final long address;

    private static final Map<Long, LibGraalIsolate> isolates = new HashMap<>();

    static synchronized LibGraalIsolate forAddress(long isolateAddress) {
        return isolates.computeIfAbsent(isolateAddress, a -> new LibGraalIsolate(a));
    }

    private LibGraalIsolate(long address) {
        this.address = address;
    }

    /**
     * Gets the isolate associated with the current thread. The current thread must be in an
     * {@linkplain LibGraalScope opened} scope.
     *
     * @throws IllegalStateException if the current thread is not attached to libgraal
     */
    public static LibGraalIsolate current() {
        return LibGraalScope.current().getIsolate();
    }

    /**
     * Gets the value corresponding to {@code key} from a key-value store of singleton objects. If
     * no value corresponding to {@code key} currently exists, then it is computed with
     * {@code supplier} and inserted in the store.
     *
     * This is used to obtain objects whose lifetime is bound to the isolate represented by this
     * object.
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T getSingleton(Class<T> key, Supplier<T> supplier) {
        // Cannot use HahsMap.computeIfAbsent as it will throw a ConcurrentModificationException
        // if supplier recurses here to compute another singleton.
        if (!singletons.containsKey(key)) {
            singletons.put(key, supplier.get());
        }
        return (T) singletons.get(key);
    }

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    /**
     * Strong references to the {@link WeakReference} objects.
     */
    private final Set<LibGraalIsolate.Cleaner> cleaners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    void register(LibGraalObject obj, long handle) {
        cleanHandles();
        Cleaner cref = new Cleaner(cleanersQueue, obj, handle);
        cleaners.add(cref);
    }

    /**
     * Processes {@link #cleanersQueue} to release any handles whose {@link LibGraalObject} objects
     * are now unreachable.
     */
    private void cleanHandles() {
        Cleaner cleaner;
        while ((cleaner = (Cleaner) cleanersQueue.poll()) != null) {
            cleaners.remove(cleaner);
            if (!cleaner.clean()) {
                new Exception(String.format("Error releasing handle %d in isolate 0x%x", cleaner.handle, address)).printStackTrace(System.out);
            }
        }
    }

    /**
     * Queue into which a {@link Cleaner} is enqueued when its {@link LibGraalObject} referent
     * becomes unreachable.
     */
    private final ReferenceQueue<LibGraalObject> cleanersQueue = new ReferenceQueue<>();

    private static final class Cleaner extends WeakReference<LibGraalObject> {
        private final long handle;

        Cleaner(ReferenceQueue<LibGraalObject> cleanersQueue, LibGraalObject referent, long handle) {
            super(referent, cleanersQueue);
            this.handle = handle;
        }

        boolean clean() {
            return LibGraalObject.releaseHandle(LibGraalScope.getIsolateThread(), handle);
        }
    }

    /**
     * Notifies that the {@code JavaVM} associated with {@code isolate} has been destroyed. All
     * subsequent accesses to objects in the isolate will throw an {@link IllegalArgumentException}.
     */
    static synchronized void remove(LibGraalIsolate isolate) {
        isolate.destroyed = true;
        LibGraalIsolate removed = isolates.remove(isolate.address);
        assert removed == isolate : "isolate already removed or overwritten: " + isolate;
    }

    @Override
    public String toString() {
        return String.format("%s[0x%x]", getClass().getSimpleName(), address);
    }

    private boolean destroyed;

    public boolean isValid() {
        return !destroyed;
    }

}
