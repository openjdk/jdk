/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.ref;

/**
 * An implementation of a ReferenceQueue that uses native monitors.
 * The use of java.util.concurrent.lock locks interacts with various mechanisms,
 * such as virtual threads and ForkJoinPool, that might not be appropriate for some
 * low-level mechanisms, in particular MethodType's weak intern set.
 */
final class NativeReferenceQueue<T> extends ReferenceQueue<T> {
    public NativeReferenceQueue() {
        super(0);
    }

    private static class Lock { };
    private final Lock lock = new Lock();

    @Override
    void signal() {
        lock.notifyAll();
    }
    @Override
    void await() throws InterruptedException {
        lock.wait();
    }

    @Override
    void await(long timeoutMillis) throws InterruptedException {
        lock.wait(timeoutMillis);
    }

    @Override
    boolean enqueue(Reference<? extends T> r) {
        synchronized(lock) {
            return enqueue0(r);
        }
    }

    @Override
    public Reference<? extends T> poll() {
        if (headIsNull())
            return null;

        synchronized(lock) {
            return poll0();
        }
    }

    @Override
    public Reference<? extends T> remove(long timeout)
            throws IllegalArgumentException, InterruptedException {
        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout value");
        if (timeout == 0)
            return remove();

        synchronized(lock) {
            return remove0(timeout);
        }
    }

    @Override
    public Reference<? extends T> remove() throws InterruptedException {
        synchronized(lock) {
            return remove0();
        }
    }
}
