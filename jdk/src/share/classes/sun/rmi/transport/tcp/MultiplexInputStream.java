/*
 * Copyright (c) 1996, 1997, Oracle and/or its affiliates. All rights reserved.
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
 * MultiplexInputStream manages receiving data over a connection managed
 * by a ConnectionMultiplexer object.  This object is responsible for
 * requesting more bytes of data as space in its internal buffer becomes
 * available.
 *
 * @author Peter Jones
 */
final class MultiplexInputStream extends InputStream {

    /** object managing multiplexed connection */
    private ConnectionMultiplexer manager;

    /** information about the connection this is the input stream for */
    private MultiplexConnectionInfo info;

    /** input buffer */
    private byte buffer[];

    /** number of real data bytes present in buffer */
    private int present = 0;

    /** current position to read from in input buffer */
    private int pos = 0;

    /** pending number of bytes this stream has requested */
    private int requested = 0;

    /** true if this connection has been disconnected */
    private boolean disconnected = false;

    /**
     * lock acquired to access shared variables:
     * buffer, present, pos, requested, & disconnected
     * WARNING:  Any of the methods manager.send*() should not be
     * invoked while this lock is held, since they could potentially
     * block if the underlying connection's transport buffers are
     * full, and the manager may need to acquire this lock to process
     * and consume data coming over the underlying connection.
     */
    private Object lock = new Object();

    /** level at which more data is requested when read past */
    private int waterMark;

    /** data structure for holding reads of one byte */
    private byte temp[] = new byte[1];

    /**
     * Create a new MultiplexInputStream for the given manager.
     * @param manager object that manages this connection
     * @param info structure for connection this stream reads from
     * @param bufferLength length of input buffer
     */
    MultiplexInputStream(
        ConnectionMultiplexer    manager,
        MultiplexConnectionInfo  info,
        int                      bufferLength)
    {
        this.manager = manager;
        this.info    = info;

        buffer = new byte[bufferLength];
        waterMark = bufferLength / 2;
    }

    /**
     * Read a byte from the connection.
     */
    public synchronized int read() throws IOException
    {
        int n = read(temp, 0, 1);
        if (n != 1)
            return -1;
        return temp[0] & 0xFF;
    }

    /**
     * Read a subarray of bytes from connection.  This method blocks for
     * at least one byte, and it returns the number of bytes actually read,
     * or -1 if the end of the stream was detected.
     * @param b array to read bytes into
     * @param off offset of beginning of bytes to read into
     * @param len number of bytes to read
     */
    public synchronized int read(byte b[], int off, int len) throws IOException
    {
        if (len <= 0)
            return 0;

        int moreSpace;
        synchronized (lock) {
            if (pos >= present)
                pos = present = 0;
            else if (pos >= waterMark) {
                System.arraycopy(buffer, pos, buffer, 0, present - pos);
                present -= pos;
                pos = 0;
            }
            int freeSpace = buffer.length - present;
            moreSpace = Math.max(freeSpace - requested, 0);
        }
        if (moreSpace > 0)
            manager.sendRequest(info, moreSpace);
        synchronized (lock) {
            requested += moreSpace;
            while ((pos >= present) && !disconnected) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
            if (disconnected && pos >= present)
                return -1;

            int available = present - pos;
            if (len < available) {
                System.arraycopy(buffer, pos, b, off, len);
                pos += len;
                return len;
            }
            else {
                System.arraycopy(buffer, pos, b, off, available);
                pos = present = 0;
                // could send another request here, if len > available??
                return available;
            }
        }
    }

    /**
     * Return the number of bytes immediately available for reading.
     */
    public int available() throws IOException
    {
        synchronized (lock) {
            return present - pos;
        }
    }

    /**
     * Close this connection.
     */
    public void close() throws IOException
    {
        manager.sendClose(info);
    }

    /**
     * Receive bytes transmitted from connection at remote endpoint.
     * @param length number of bytes transmitted
     * @param in input stream with those bytes ready to be read
     */
    void receive(int length, DataInputStream in)
        throws IOException
    {
        /* TO DO: Optimize so that data received from stream can be loaded
         * directly into user's buffer if there is a pending read().
         */
        synchronized (lock) {
            if ((pos > 0) && ((buffer.length - present) < length)) {
                System.arraycopy(buffer, pos, buffer, 0, present - pos);
                present -= pos;
                pos = 0;
            }
            if ((buffer.length - present) < length)
                throw new IOException("Receive buffer overflow");
            in.readFully(buffer, present, length);
            present += length;
            requested -= length;
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
}
