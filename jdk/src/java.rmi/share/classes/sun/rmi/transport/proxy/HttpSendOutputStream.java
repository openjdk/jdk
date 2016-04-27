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
package sun.rmi.transport.proxy;

import java.io.*;

/**
 * The HttpSendOutputStream class is used by the HttpSendSocket class as
 * a layer on the top of the OutputStream it returns so that it can be
 * notified of attempts to write to it.  This allows the HttpSendSocket
 * to know when it should construct a new message.
 */
class HttpSendOutputStream extends FilterOutputStream {

    /** the HttpSendSocket object that is providing this stream */
    HttpSendSocket owner;

    /**
     * Create new filter on a given output stream.
     * @param out the OutputStream to filter from
     * @param owner the HttpSendSocket that is providing this stream
     */
    public HttpSendOutputStream(OutputStream out, HttpSendSocket owner)
        throws IOException
    {
        super(out);

        this.owner = owner;
    }

    /**
     * Mark this stream as inactive for its owner socket, so the next time
     * a write is attempted, the owner will be notified and a new underlying
     * output stream obtained.
     */
    public void deactivate()
    {
        out = null;
    }

    /**
     * Write a byte of data to the stream.
     */
    public void write(int b) throws IOException
    {
        if (out == null)
            out = owner.writeNotify();
        out.write(b);
    }

    /**
     * Write a subarray of bytes.
     * @param b the buffer from which the data is to be written
     * @param off the start offset of the data
     * @param len the number of bytes to be written
     */
    public void write(byte b[], int off, int len) throws IOException
    {
        if (len == 0)
            return;
        if (out == null)
            out = owner.writeNotify();
        out.write(b, off, len);
    }

    /**
     * Flush the stream.
     */
    public void flush() throws IOException
    {
        if (out != null)
            out.flush();
    }

    /**
     * Close the stream.
     */
    public void close() throws IOException
    {
        flush();
        owner.close();
    }
}
