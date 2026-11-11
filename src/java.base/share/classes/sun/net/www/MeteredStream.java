/*
 * Copyright (c) 1994, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www;

import java.io.*;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.util.Preconditions;

public class MeteredStream extends FilterInputStream {

    protected boolean closed = false;
    protected final long expected;
    protected long count = 0;
    protected long markedCount = 0;
    protected int markLimit = -1;
    private final ReentrantLock readLock = new ReentrantLock();

    public MeteredStream(InputStream is, long expected)
    {
        assert expected > 0 : "Invalid content length: " + expected;
        super(is);

        this.expected = expected;
    }

    private void justRead(long n) throws IOException {
        assert isLockHeldByCurrentThread();

        if (n == -1) {
            if (expected > count) {
                throw new IOException("Premature EOF");
            }
        } else {

            // Increment the position
            assert count <= (expected - n) : "Excessive read: " + n;
            count += n;

            // If read beyond the markLimit, invalidate the mark
            if (count - markedCount > markLimit) {
                markLimit = -1;
            }

        }

        // Auto-close if there are no marks and the expected length is consumed.
        //
        // We avoid auto-close while marked, since the user might invoke
        // `reset()` after reading the last byte.
        //
        // We auto-close to speed up the release of resources. For instance,
        // see `KeepAliveStream::close`.
        if (!isMarked() && (n == -1 || count >= expected)) {
            close();
        }

    }

    /**
     * Returns true if the mark is valid, false otherwise
     */
    private boolean isMarked() {
        assert isLockHeldByCurrentThread();

        if (markLimit < 0) {
            return false;
        }

        // mark is set, but is not valid anymore
        if (count - markedCount > markLimit) {
           return false;
        }

        // mark still holds
        return true;
    }

    public int read() throws java.io.IOException {
        lock();
        try {
            long remaining = expected - count;
            if (closed || remaining < 1) return -1;
            int c = in.read();
            if (c != -1) {
                justRead(1);
            } else {
                justRead(c);
            }
            return c;
        } finally {
            unlock();
        }
    }

    public int read(byte b[], int off, int len)
                throws java.io.IOException {
        Preconditions.checkFromIndexSize(
                off, len, b.length,     // Implicit null check on `b`
                Preconditions.AIOOBE_FORMATTER);
        if (len < 1) {
            return 0;
        }
        lock();
        try {
            long remaining = expected - count;
            if (closed || remaining < 1) return -1;
            int trimmedLength = (int) Math.min(len, remaining);
            int n = in.read(b, off, trimmedLength);
            justRead(n);
            return n;
        } finally {
            unlock();
        }
    }

    public long skip(long n) throws IOException {
        if (n < 1) {
            return 0;
        }
        lock();
        try {
            long remaining = expected - count;
            if (closed || remaining < 1) return 0;
            long maxSkip = Math.min(n, remaining);
            long skipped = in.skip(maxSkip);
            justRead(skipped);
            return skipped;
        } finally {
            unlock();
        }
    }

    public void close() throws IOException {
        lock();
        try {
            if (closed) return;

            closed = true;
            in.close();
        } finally {
            unlock();
        }
    }

    public int available() throws IOException {
        lock();
        try {
            if (closed) return 0;
            long remaining = expected - count;
            return (int) Math.min(remaining, in.available());
        } finally {
            unlock();
        }
    }

    public void mark(int readLimit) {
        lock();
        try {
            if (closed) return;
            super.mark(readLimit);

            /*
             * mark the count to restore upon reset
             */
            markedCount = count;
            markLimit = readLimit;
        } finally {
            unlock();
        }
    }

    public void reset() throws IOException {
        lock();
        try {
            if (closed) return;
            if (!isMarked()) {
                throw new IOException("Resetting to an invalid mark");
            }

            count = markedCount;
            super.reset();
        } finally {
            unlock();
        }
    }

    public boolean markSupported() {
        lock();
        try {
            if (closed) return false;
            return super.markSupported();
        } finally {
            unlock();
        }
    }

    public final void lock() {
        readLock.lock();
    }

    public final void unlock() {
        readLock.unlock();
    }

    public final boolean isLockHeldByCurrentThread() {
        return readLock.isHeldByCurrentThread();
    }
}
