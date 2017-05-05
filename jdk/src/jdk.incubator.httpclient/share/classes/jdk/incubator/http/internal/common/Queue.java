/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import java.io.IOException;
import java.util.LinkedList;
import java.util.stream.Stream;

// Each stream has one of these for input. Each Http2Connection has one
// for output. Can be used blocking or asynchronously.

public class Queue<T> implements ExceptionallyCloseable  {

    private final LinkedList<T> q = new LinkedList<>();
    private volatile boolean closed = false;
    private volatile Throwable exception = null;
    private Runnable callback;
    private boolean forceCallback;
    private int waiters; // true if someone waiting

    public synchronized void putAll(T[] objs) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        boolean wasEmpty = q.isEmpty();

        for (T obj : objs) {
            q.add(obj);
        }

        if (waiters > 0) {
            notifyAll();
        }

        if (wasEmpty || forceCallback) {
            forceCallback = false;
            if (callback != null) {
                // Note: calling callback while holding the lock is
                // dangerous and may lead to deadlocks.
                callback.run();
           }
        }
    }

    public synchronized int size() {
        return q.size();
    }

    public synchronized boolean tryPut(T obj) throws IOException {
        if (closed) return false;
        put(obj);
        return true;
    }

    public synchronized void put(T obj) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }

        q.add(obj);
        if (waiters > 0) {
            notifyAll();
        }

        if (q.size() == 1 || forceCallback) {
            forceCallback = false;
            if (callback != null) {
                // Note: calling callback while holding the lock is
                // dangerous and may lead to deadlocks.
                callback.run();
            }
        }
    }

    /**
     * callback is invoked any time put is called where
     * the Queue was empty.
     */
    public synchronized void registerPutCallback(Runnable callback) {
        this.callback = callback;
        if (callback != null && q.size() > 0) {
            // Note: calling callback while holding the lock is
            // dangerous and may lead to deadlocks.
            callback.run();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    @Override
    public synchronized void closeExceptionally(Throwable t) {
        if (exception == null) exception = t;
        else if (t != null && t != exception) {
            if (!Stream.of(exception.getSuppressed())
                .filter(x -> x == t)
                .findFirst()
                .isPresent())
            {
                exception.addSuppressed(t);
            }
        }
        close();
    }

    public synchronized T take() throws IOException {
        if (closed) {
            throw newIOException("stream closed");
        }
        try {
            while (q.size() == 0) {
                waiters++;
                wait();
                if (closed) {
                    throw newIOException("Queue closed");
                }
                waiters--;
            }
            return q.removeFirst();
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
    }

    public synchronized T poll() throws IOException {
        if (closed) {
            throw newIOException("stream closed");
        }

        if (q.isEmpty()) {
            return null;
        }
        T res = q.removeFirst();
        return res;
    }

    public synchronized T[] pollAll(T[] type) throws IOException {
        T[] ret = q.toArray(type);
        q.clear();
        return ret;
    }

    public synchronized void pushback(T v) {
        forceCallback = true;
        q.addFirst(v);
    }

    public synchronized void pushbackAll(T[] v) {
        forceCallback = true;
        for (int i=v.length-1; i>=0; i--) {
            q.addFirst(v[i]);
        }
    }

    private IOException newIOException(String msg) {
        if (exception == null) {
            return new IOException(msg);
        } else {
            return new IOException(msg, exception);
        }
    }

}
