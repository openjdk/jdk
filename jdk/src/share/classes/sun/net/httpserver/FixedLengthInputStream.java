/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.httpserver;

import java.io.*;
import java.net.*;
import com.sun.net.httpserver.*;
import com.sun.net.httpserver.spi.*;

/**
 * a class which allows the caller to read up to a defined
 * number of bytes off an underlying stream
 * close() does not close the underlying stream
 */

class FixedLengthInputStream extends LeftOverInputStream {
    private int remaining;

    FixedLengthInputStream (ExchangeImpl t, InputStream src, int len) {
        super (t, src);
        this.remaining = len;
    }

    protected int readImpl (byte[]b, int off, int len) throws IOException {

        eof = (remaining == 0);
        if (eof) {
            return -1;
        }
        if (len > remaining) {
            len = remaining;
        }
        int n = in.read(b, off, len);
        if (n > -1) {
            remaining -= n;
        }
        return n;
    }

    public int available () throws IOException {
        if (eof) {
            return 0;
        }
        int n = in.available();
        return n < remaining? n: remaining;
    }

    public boolean markSupported () {return false;}

    public void mark (int l) {
    }

    public void reset () throws IOException {
        throw new IOException ("mark/reset not supported");
    }
}
