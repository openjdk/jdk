/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

// Special-purpose data structure for sets of native threads

class NativeThreadSet {
    private static final int OTHER_THREAD_INDEX = -99;

    private final int initialCapacity;
    private Thread[] threads;     // array of platform threads, created lazily
    private int used;             // number of elements in threads array
    private int otherThreads;     // additional threads that can't be signalled
    private boolean waitingToEmpty;

    NativeThreadSet(int n) {
        initialCapacity = n;
    }

    /**
     * Adds the current thread handle to this set, returning an index so that
     * it can efficiently be removed later.
     */
    int add() {
        synchronized (this) {
            final Thread t = NativeThread.threadToSignal();
            if (t == null || t.isVirtual()) {
                otherThreads++;
                return OTHER_THREAD_INDEX;
            }

            // add platform threads to array, creating or growing array if needed
            int start = 0;
            if (threads == null) {
                threads = new Thread[initialCapacity];
            } else if (used >= threads.length) {
                int on = threads.length;
                int nn = on * 2;
                Thread[] nthreads = new Thread[nn];
                System.arraycopy(threads, 0, nthreads, 0, on);
                threads = nthreads;
                start = on;
            }
            for (int i = start; i < threads.length; i++) {
                if (threads[i] == null) {
                    threads[i] = t;
                    used++;
                    return i;
                }
            }
            throw new InternalError();
        }
    }

    /**
     * Removes the thread at the given index. A no-op if index is -1.
     */
    void remove(int i) {
        synchronized (this) {
            if (i >= 0) {
                threads[i] = null;
                used--;
            } else if (i == OTHER_THREAD_INDEX) {
                otherThreads--;
            } else {
                assert i == -1;
                return;
            }
            if (used == 0 && otherThreads == 0 && waitingToEmpty) {
                notifyAll();
            }
        }
    }

    /**
     * Signals all native threads in the thread set and wait for the thread set to empty.
     */
    synchronized void signalAndWait() {
        boolean interrupted = false;
        while (used > 0 || otherThreads > 0) {
            int u = used, i = 0;
            while (u > 0 && i < threads.length) {
                Thread t = threads[i];
                if (t != null) {
                    NativeThread.signal(t);
                    u--;
                }
                i++;
            }
            waitingToEmpty = true;
            try {
                wait(50);
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                waitingToEmpty = false;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
