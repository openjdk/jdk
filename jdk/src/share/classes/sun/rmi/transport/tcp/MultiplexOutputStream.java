/*
 * Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.tcp;

import java.io.*;

/**
 * MultiplexOutputStream manages sending data over a conection managed
 * by a ConnectionMultiplexer object.  Data written is buffered until the
 * internal buffer is full or the flush() method is called, at which
 * point it attempts to push a packet of bytes through to the remote
 * endpoint.  This will never push more bytes than the amount already
 * requested by the remote endpoint (to prevent receive buffer from
 * overflowing), so if the write() and flush() methods will block
 * until their operation can complete if enough bytes cannot be
 * pushed immediately.
 *
 * @author Peter Jones
 */
final class MultiplexOutputStream extends OutputStream {

    /** object managing multiplexed connection */
    private ConnectionMultiplexer manager;

    /** information about the connection this is the output stream for */
    private MultiplexConnectionInfo info;

    /** output buffer */
    private byte buffer[];

    /** current position to write to in output buffer */
    private int pos = 0;

    /** pending number of bytes requested by remote endpoint */
    private int requested = 0;

    /** true if this connection has been disconnected */
    private boolean disconnected = false;

    /**
     * lock acquired to access shared variables:
     * requested & disconnected
     * WARNING:  Any of the methods manager.send*() should not be
     * invoked while this lock is held, since they could potentially
     * block if the underlying connection's transport buffers are
     * full, and the manager may need to acquire this lock to process
     * and consume data coming over the underlying connection.
     */
    private Object lock = new Object();

    /**
     * Create a new MultiplexOutputStream for the given manager.
     * @param manager object that manages this connection
     * @param info structure for connection this stream writes to
     * @param bufferLength length of output buffer
     */
    MultiplexOutputStream(
        ConnectionMultiplexer    manager,
        MultiplexConnectionInfo  info,
        int                      bufferLength)
    {
        this.manager = manager;
        this.info    = info;

        buffer = new byte[bufferLength];
        pos = 0;
    }

    /**
     * Write a byte over connection.
     * @param b byte of data to write
     */
    public synchronized void write(int b) throws IOException
    {
        while (pos >= buffer.length)
            push();
        buffer[pos ++] = (byte) b;
    }

    /**
     * Write a subarray of bytes over connection.
     * @param b array containing bytes to write
     * @param off offset of beginning of bytes to write
     * @param len number of bytes to write
     */
    public synchronized void write(byte b[], int off, int len)
        throws IOException
    {
        if (len <= 0)
            return;

        // if enough free space in output buffer, just copy into there
        int freeSpace = buffer.length - pos;
        if (len <= freeSpace) {
            System.arraycopy(b, off, buffer, pos, len);
            pos += len;
            return;
        }

        // else, flush buffer and send rest directly to avoid array copy
        flush();
        int local_requested;
        while (true) {
            synchronized (lock) {
                while ((local_requested = requested) < 1 && !disconnected) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                    }
                }
                if (disconnected)
                    throw new IOException("Connection closed");
            }

            if (local_requested < len) {
                manager.sendTransmit(info, b, off, local_requested);
                off += local_requested;
                len -= local_requested;
                synchronized (lock) {
                    requested -= local_requested;
                }
            }
            else {
                manager.sendTransmit(info, b, off, len);
                synchronized (lock) {
                    requested -= len;
                }
                // len = 0;
                break;
            }
        }
    }

    /**
     * Guarantee that all data written to this stream has been pushed
     * over and made available to the remote endpoint.
     */
    public synchronized void flush() throws IOException {
        while (pos > 0)
            push();
    }

    /**
     * Close this connection.
     */
    public void close() throws IOException
    {
        manager.sendClose(info);
    }

    /**
     * Take note of more bytes requested by conection at remote endpoint.
     * @param num number of additional bytes requested
     */
    void request(int num)
    {
        synchronized (lock) {
            requested += num;
            lock.notifyAll();
        }
    }

    /**
     * Disconnect this stream from all connection activity.
     */
    void disconnect()
    {
        synchronized (lock) {
            disconnected = true;
            lock.notifyAll();
        }
    }

    /**
     * Push bytes in output buffer to connection at remote endpoint.
     * This method blocks until at least one byte has been pushed across.
     */
    private void push() throws IOException
    {
        int local_requested;
        synchronized (lock) {
            while ((local_requested = requested) < 1 && !disconnected) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
            if (disconnected)
                throw new IOException("Connection closed");
        }

        if (local_requested < pos) {
            manager.sendTransmit(info, buffer, 0, local_requested);
            System.arraycopy(buffer, local_requested,
                             buffer, 0, pos - local_requested);
            pos -= local_requested;
            synchronized (lock) {
                requested -= local_requested;
            }
        }
        else {
            manager.sendTransmit(info, buffer, 0, pos);
            synchronized (lock) {
                requested -= pos;
            }
            pos = 0;
        }
    }
}
