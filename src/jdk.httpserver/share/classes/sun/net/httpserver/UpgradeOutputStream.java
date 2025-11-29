/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * a class which allows the caller to write an indefinite number of bytes to an underlying stream.
 * Used for upgraded connections only.
 */
class UpgradeOutputStream extends FilterOutputStream {
    private boolean closed = false;
    ExchangeImpl t;

    UpgradeOutputStream(ExchangeImpl t, OutputStream src) {
        super(src);
        this.t = t;
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return;
        }
        if (closed) {
            throw new IOException("stream closed");
        }
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        flush();
        LeftOverInputStream is = t.getOriginalInputStream();
        if (!is.isClosed()) {
            is.close();
        }
        Event e = new Event.WriteFinished(t);
        t.getHttpContext().getServerImpl().addEvent(e);
        out.close();
    }
}
