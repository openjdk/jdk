/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @(#)LineInputStream.java   1.7 03/01/07
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.util;

import java.io.*;

/**
 * This class is to support reading CRLF terminated lines that
 * contain only US-ASCII characters from an input stream. Provides
 * functionality that is similar to the deprecated
 * <code>DataInputStream.readLine()</code>. Expected use is to read
 * lines as String objects from a RFC822 stream.
 *
 * It is implemented as a FilterInputStream, so one can just wrap
 * this class around any input stream and read bytes from this filter.
 *
 * @author John Mani
 */

public final class LineInputStream extends FilterInputStream {

    private char[] lineBuffer = null; // reusable byte buffer

    public LineInputStream(InputStream in) {
        super(in);
    }

    /**
     * Read a line containing only ASCII characters from the input
     * stream. A line is terminated by a CR or NL or CR-NL sequence.
     * A common error is a CR-CR-NL sequence, which will also terminate
     * a line.
     * The line terminator is not returned as part of the returned
     * String. Returns null if no data is available. <p>
     *
     * This class is similar to the deprecated
     * <code>DataInputStream.readLine()</code>
     *
     * @return line.
     *
     * @throws IOException if an I/O error occurs.
     */
    public String readLine() throws IOException {
        InputStream in = this.in;
        char[] buf = lineBuffer;

        if (buf == null)
            buf = lineBuffer = new char[128];

        int c1;
        int room = buf.length;
        int offset = 0;

        while ((c1 = in.read()) != -1) {
            if (c1 == '\n') // Got NL, outa here.
                break;
            else if (c1 == '\r') {
                // Got CR, is the next char NL ?
                int c2 = in.read();
                if (c2 == '\r')         // discard extraneous CR
                    c2 = in.read();
                if (c2 != '\n') {
                    // If not NL, push it back
                    if (!(in instanceof PushbackInputStream))
                        in = this.in = new PushbackInputStream(in);
                    ((PushbackInputStream)in).unread(c2);
                }
                break; // outa here.
            }

            // Not CR, NL or CR-NL ...
            // .. Insert the byte into our byte buffer
            if (--room < 0) { // No room, need to grow.
                buf = new char[offset + 128];
                room = buf.length - offset - 1;
                System.arraycopy(lineBuffer, 0, buf, 0, offset);
                lineBuffer = buf;
            }
            buf[offset++] = (char)c1;
        }

        if ((c1 == -1) && (offset == 0))
            return null;

        return String.copyValueOf(buf, 0, offset);
    }
}
