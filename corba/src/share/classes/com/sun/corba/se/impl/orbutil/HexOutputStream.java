/*
 * Copyright (c) 1996, 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.io.StringWriter;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Writes each input byte as a 2 byte hexidecimal output pair making it
 * possible to turn arbitrary binary data into an ASCII format.
 * The high 4 bits of the byte is translated into the first byte.
 *
 * @author      Jeff Nisewanger
 */
public class HexOutputStream extends OutputStream
{
    static private final char hex[] = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private StringWriter writer;

    /**
     * Creates a new HexOutputStream.
     * @param w The underlying StringWriter.
     */
    public
        HexOutputStream(StringWriter w) {
        writer = w;
    }


    /**
     * Writes a byte. Will block until the byte is actually
     * written.
     * param b The byte to write out.
     * @exception java.io.IOException I/O error occurred.
     */
    public synchronized void write(int b) throws IOException {
        writer.write(hex[((b >> 4) & 0xF)]);
        writer.write(hex[((b >> 0) & 0xF)]);
    }

    public synchronized void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public synchronized void write(byte[] b, int off, int len)
        throws IOException
    {
        for(int i=0; i < len; i++) {
            write(b[off + i]);
        }
    }
}
