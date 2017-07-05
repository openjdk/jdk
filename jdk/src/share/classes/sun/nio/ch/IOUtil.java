/*
 * Copyright (c) 2000, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;


/**
 * File-descriptor based I/O utilities that are shared by NIO classes.
 */

class IOUtil {

    private IOUtil() { }                // No instantiation

    /*
     * Returns the index of first buffer in bufs with remaining,
     * or -1 if there is nothing left
     */
    private static int remaining(ByteBuffer[] bufs) {
        int numBufs = bufs.length;
        for (int i=0; i<numBufs; i++) {
            if (bufs[i].hasRemaining()) {
                return i;
            }
        }
        return -1;
    }

    /*
     * Returns a new ByteBuffer array with only unfinished buffers in it
     */
    private static ByteBuffer[] skipBufs(ByteBuffer[] bufs,
                                         int nextWithRemaining)
    {
        int newSize = bufs.length - nextWithRemaining;
        ByteBuffer[] temp = new ByteBuffer[newSize];
        for (int i=0; i<newSize; i++) {
            temp[i] = bufs[i + nextWithRemaining];
        }
        return temp;
    }

    static int write(FileDescriptor fd, ByteBuffer src, long position,
                     NativeDispatcher nd, Object lock)
        throws IOException
    {
        if (src instanceof DirectBuffer)
            return writeFromNativeBuffer(fd, src, position, nd, lock);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        ByteBuffer bb = null;
        try {
            bb = Util.getTemporaryDirectBuffer(rem);
            bb.put(src);
            bb.flip();
            // Do not update src until we see how many bytes were written
            src.position(pos);

            int n = writeFromNativeBuffer(fd, bb, position, nd, lock);
            if (n > 0) {
                // now update src
                src.position(pos + n);
            }
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                           long position, NativeDispatcher nd,
                                             Object lock)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        int written = 0;
        if (rem == 0)
            return 0;
        if (position != -1) {
            written = nd.pwrite(fd,
                                ((DirectBuffer)bb).address() + pos,
                                rem, position, lock);
        } else {
            written = nd.write(fd, ((DirectBuffer)bb).address() + pos, rem);
        }
        if (written > 0)
            bb.position(pos + written);
        return written;
    }

    static long write(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        int nextWithRemaining = remaining(bufs);
        // if all bufs are empty we should return immediately
        if (nextWithRemaining < 0)
            return 0;
        // If some bufs are empty we should skip them
        if (nextWithRemaining > 0)
            bufs = skipBufs(bufs, nextWithRemaining);

        int numBufs = bufs.length;

        // Create shadow to ensure DirectByteBuffers are used
        ByteBuffer[] shadow = new ByteBuffer[numBufs];
        try {
            for (int i=0; i<numBufs; i++) {
                if (!(bufs[i] instanceof DirectBuffer)) {
                    int pos = bufs[i].position();
                    int lim = bufs[i].limit();
                    assert (pos <= lim);
                    int rem = (pos <= lim ? lim - pos : 0);

                    ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
                    shadow[i] = bb;
                    // Leave slow buffer position untouched; it will be updated
                    // after we see how many bytes were really written out
                    bb.put(bufs[i]);
                    bufs[i].position(pos);
                    bb.flip();
                } else {
                    shadow[i] = bufs[i];
                }
            }

            IOVecWrapper vec = null;
            long bytesWritten = 0;
            try {
                // Create a native iovec array
                vec= new IOVecWrapper(numBufs);

                // Fill in the iovec array with appropriate data
                for (int i=0; i<numBufs; i++) {
                    ByteBuffer nextBuffer = shadow[i];
                    // put in the buffer addresses
                    long pos = nextBuffer.position();
                    long len = nextBuffer.limit() - pos;
                    vec.putBase(i, ((DirectBuffer)nextBuffer).address() + pos);
                    vec.putLen(i, len);
                }

                // Invoke native call to fill the buffers
                bytesWritten = nd.writev(fd, vec.address, numBufs);
            } finally {
                vec.free();
            }
            long returnVal = bytesWritten;

            // Notify the buffers how many bytes were taken
            for (int i=0; i<numBufs; i++) {
                ByteBuffer nextBuffer = bufs[i];
                int pos = nextBuffer.position();
                int lim = nextBuffer.limit();
                assert (pos <= lim);
                int len = (pos <= lim ? lim - pos : lim);
                if (bytesWritten >= len) {
                    bytesWritten -= len;
                    int newPosition = pos + len;
                    nextBuffer.position(newPosition);
                } else { // Buffers not completely filled
                    if (bytesWritten > 0) {
                        assert(pos + bytesWritten < (long)Integer.MAX_VALUE);
                        int newPosition = (int)(pos + bytesWritten);
                        nextBuffer.position(newPosition);
                    }
                    break;
                }
            }
            return returnVal;
        } finally {
            // return any substituted buffers to cache
            for (int i=0; i<numBufs; i++) {
                ByteBuffer bb = shadow[i];
                if (bb != null && bb != bufs[i]) {
                    Util.releaseTemporaryDirectBuffer(bb);
                }
            }
        }
    }

    static int read(FileDescriptor fd, ByteBuffer dst, long position,
                    NativeDispatcher nd, Object lock)
        throws IOException
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        if (dst instanceof DirectBuffer)
            return readIntoNativeBuffer(fd, dst, position, nd, lock);

        // Substitute a native buffer
        ByteBuffer bb = null;
        try {
            bb = Util.getTemporaryDirectBuffer(dst.remaining());
            int n = readIntoNativeBuffer(fd, bb, position, nd, lock);
            bb.flip();
            if (n > 0)
                dst.put(bb);
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private static int readIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                            long position, NativeDispatcher nd,
                                            Object lock)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        if (rem == 0)
            return 0;
        int n = 0;
        if (position != -1) {
            n = nd.pread(fd, ((DirectBuffer)bb).address() + pos,
                         rem, position, lock);
        } else {
            n = nd.read(fd, ((DirectBuffer)bb).address() + pos, rem);
        }
        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    static long read(FileDescriptor fd, ByteBuffer[] bufs, NativeDispatcher nd)
        throws IOException
    {
        int nextWithRemaining = remaining(bufs);
        // if all bufs are empty we should return immediately
        if (nextWithRemaining < 0)
            return 0;
        // If some bufs are empty we should skip them
        if (nextWithRemaining > 0)
            bufs = skipBufs(bufs, nextWithRemaining);

        int numBufs = bufs.length;

        // Read into the shadow to ensure DirectByteBuffers are used
        ByteBuffer[] shadow = new ByteBuffer[numBufs];
        boolean usingSlowBuffers = false;
        try {
            for (int i=0; i<numBufs; i++) {
                if (bufs[i].isReadOnly())
                    throw new IllegalArgumentException("Read-only buffer");
                if (!(bufs[i] instanceof DirectBuffer)) {
                    shadow[i] = Util.getTemporaryDirectBuffer(bufs[i].remaining());
                    usingSlowBuffers = true;
                } else {
                    shadow[i] = bufs[i];
                }
            }

            IOVecWrapper vec = null;
            long bytesRead = 0;
            try {
                // Create a native iovec array
                vec = new IOVecWrapper(numBufs);

                // Fill in the iovec array with appropriate data
                for (int i=0; i<numBufs; i++) {
                    ByteBuffer nextBuffer = shadow[i];
                    // put in the buffer addresses
                    long pos = nextBuffer.position();
                    long len = nextBuffer.remaining();
                    vec.putBase(i, ((DirectBuffer)nextBuffer).address() + pos);
                    vec.putLen(i, len);
                }

                // Invoke native call to fill the buffers
                bytesRead = nd.readv(fd, vec.address, numBufs);
            } finally {
                vec.free();
            }
            long returnVal = bytesRead;

            // Notify the buffers how many bytes were read
            for (int i=0; i<numBufs; i++) {
                ByteBuffer nextBuffer = shadow[i];
                // Note: should this have been cached from above?
                int pos = nextBuffer.position();
                int len = nextBuffer.remaining();
                if (bytesRead >= len) {
                    bytesRead -= len;
                    int newPosition = pos + len;
                    nextBuffer.position(newPosition);
                } else { // Buffers not completely filled
                    if (bytesRead > 0) {
                        assert(pos + bytesRead < (long)Integer.MAX_VALUE);
                        int newPosition = (int)(pos + bytesRead);
                        nextBuffer.position(newPosition);
                    }
                    break;
                }
            }

            // Put results from shadow into the slow buffers
            if (usingSlowBuffers) {
                for (int i=0; i<numBufs; i++) {
                    if (!(bufs[i] instanceof DirectBuffer)) {
                        shadow[i].flip();
                        bufs[i].put(shadow[i]);
                    }
                }
            }
            return returnVal;
        } finally {
            // return any substituted buffers to cache
            if (usingSlowBuffers) {
                for (int i=0; i<numBufs; i++) {
                    ByteBuffer bb = shadow[i];
                    if (bb != null && bb != bufs[i]) {
                        Util.releaseTemporaryDirectBuffer(bb);
                    }
                }
            }
        }
    }

    static FileDescriptor newFD(int i) {
        FileDescriptor fd = new FileDescriptor();
        setfdVal(fd, i);
        return fd;
    }

    static native boolean randomBytes(byte[] someBytes);

    static native void initPipe(int[] fda, boolean blocking);

    static native boolean drain(int fd) throws IOException;

    static native void configureBlocking(FileDescriptor fd, boolean blocking)
        throws IOException;

    static native int fdVal(FileDescriptor fd);

    static native void setfdVal(FileDescriptor fd, int value);

    static native void initIDs();

    static {
        // Note that IOUtil.initIDs is called from within Util.load.
        Util.load();
    }

}
