/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * $Id: ByteOutputStream.java,v 1.7 2006/01/27 12:49:51 vj135062 Exp $
 * $Revision: 1.7 $ $Date: 2006/01/27 12:49:51 $
 */


package com.sun.xml.internal.messaging.saaj.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

/**
 * Customized {@link BufferedOutputStream}.
 *
 * <p>
 * Compared to {@link BufferedOutputStream},
 * this class:
 *
 * <ol>
 * <li>doesn't do synchronization
 * <li>allows access to the raw buffer
 * <li>almost no parameter check
 */
public final class ByteOutputStream extends OutputStream {
    /**
     * The buffer where data is stored.
     */
    protected byte[] buf;

    /**
     * The number of valid bytes in the buffer.
     */
    protected int count = 0;

    public ByteOutputStream() {
        this(1024);
    }

    public ByteOutputStream(int size) {
        buf = new byte[size];
    }

    /**
     * Copies all the bytes from this input into this buffer.
     */
    public void write(InputStream in) throws IOException {
        if (in instanceof ByteArrayInputStream) {
            int size = in.available();
            ensureCapacity(size);
            count += in.read(buf,count,size);
            return;
        }
        while(true) {
            int cap = buf.length-count;
            int sz = in.read(buf,count,cap);
            if(sz<0)    return;     // hit EOS

            count += sz;
            if(cap==sz)
                // the buffer filled up. double the buffer
                ensureCapacity(count);
        }
    }

    public void write(int b) {
        ensureCapacity(1);
        buf[count] = (byte) b;
        count++;
    }

    /**
     * Ensure that the buffer has at least this much space.
     */
    private void ensureCapacity(int space) {
        int newcount = space + count;
        if (newcount > buf.length) {
            byte[] newbuf = new byte[Math.max(buf.length << 1, newcount)];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }
    }

    public void write(byte[] b, int off, int len) {
        ensureCapacity(len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    /**
     * Writes a string as ASCII string.
     */
    public void writeAsAscii(String s) {
        int len = s.length();

        ensureCapacity(len);

        int ptr = count;
        for( int i=0; i<len; i++ )
            buf[ptr++] = (byte)s.charAt(i);
        count = ptr;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public void reset() {
        count = 0;
    }

    /**
     * Evil buffer reallocation method.
     * Don't use it unless you absolutely have to.
     *
     * @deprecated
     *      because this is evil!
     */
    public byte toByteArray()[] {
        byte[] newbuf = new byte[count];
        System.arraycopy(buf, 0, newbuf, 0, count);
        return newbuf;
    }

    public int size() {
        return count;
    }

    public ByteInputStream newInputStream() {
        return new ByteInputStream(buf,count);
    }

    /**
     * Converts the buffer's contents into a string, translating bytes into
     * characters according to the platform's default character encoding.
     *
     * @return String translated from the buffer's contents.
     * @since JDK1.1
     */
    public String toString() {
        return new String(buf, 0, count);
    }

    public void close() {
    }

    public byte[] getBytes() {
        return buf;
    }


    public int getCount() {
        return count;
    }
}
