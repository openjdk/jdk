/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Allows different platforms to call different native methods
 * for read and write operations.
 */

abstract class NativeDispatcher {

    abstract int read(FileDescriptor fd, long address, int len)
        throws IOException;

    /**
     * Returns {@code true} if pread/pwrite needs to be synchronized with
     * position sensitive methods.
     */
    boolean needsPositionLock() {
        return false;
    }

    int pread(FileDescriptor fd, long address, int len, long position)
        throws IOException
    {
        throw new IOException("Operation Unsupported");
    }

    abstract long readv(FileDescriptor fd, long address, int len)
        throws IOException;

    abstract int write(FileDescriptor fd, long address, int len)
        throws IOException;

    int pwrite(FileDescriptor fd, long address, int len, long position)
        throws IOException
    {
        throw new IOException("Operation Unsupported");
    }

    abstract long writev(FileDescriptor fd, long address, int len)
        throws IOException;

    abstract void close(FileDescriptor fd) throws IOException;

    /**
     * Prepare the given file descriptor for closing. If a virtual thread is blocked
     * on the file descriptor then it is unparked so that it stops polling. On Unix systems,
     * if a platform thread is blocked on the file descriptor then the file descriptor is
     * dup'ed to a special fd and the thread signalled so that the syscall fails with EINTR.
     */
    final void preClose(FileDescriptor fd, Thread reader, Thread writer) throws IOException {
        if (reader != null && reader.isVirtual()) {
            NativeThread.signal(reader);  // unparks virtual thread
            reader = null;
        }
        if (writer != null && writer.isVirtual()) {
            NativeThread.signal(writer);  // unparks virtual thread
            writer = null;
        }
        // dup2 and signal platform threads
        if (reader != null || writer != null) {
            implPreClose(fd, reader, writer);
        }
    }

    /**
     * This method does nothing by default. On Unix systems the file descriptor is dup'ed
     * to a special fd and native threads signalled.
     */
    void implPreClose(FileDescriptor fd, Thread reader, Thread writer) throws IOException {
        // Do nothing by default; this is only needed on Unix
    }

    /**
     * Duplicates a file descriptor.
     * @param fd1 the file descriptor to duplicate
     * @param fd2 the new file descriptor, the socket or file that it is connected
     *            to will be closed by this method
     */
    void dup(FileDescriptor fd1, FileDescriptor fd2) throws IOException {
        throw new UnsupportedOperationException();
    }
}
