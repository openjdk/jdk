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
package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.InputStream;
import java.io.IOException;

/**
 * Constructs a InputStream from a linked list of {@link Chunk}s.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
final class ChunkInputStream extends InputStream {
    Chunk current;
    int offset;
    int len;
    final MIMEMessage msg;
    final MIMEPart part;
    byte[] buf;

    public ChunkInputStream(MIMEMessage msg, MIMEPart part, Chunk startPos) {
        this.current = startPos;
        len = current.data.size();
        buf = current.data.read();
        this.msg = msg;
        this.part = part;
    }

    @Override
    public int read(byte b[], int off, int sz) throws IOException {
        if(!fetch())    return -1;

        sz = Math.min(sz, len-offset);
        System.arraycopy(buf,offset,b,off,sz);
        return sz;
    }

    public int read() throws IOException {
        if(!fetch()) return -1;
        return (buf[offset++] & 0xff);
    }

    /**
     * Gets to the next chunk if we are done with the current one.
     * @return
     */
    private boolean fetch() {
        if (current == null) {
            throw new IllegalStateException("Stream already closed");
        }
        while(offset==len) {
            while(!part.parsed && current.next == null) {
                msg.makeProgress();
            }
            current = current.next;

            if (current == null) {
                return false;
            }
            this.offset = 0;
            this.buf = current.data.read();
            this.len = current.data.size();
        }
        return true;
    }

    public void close() throws IOException {
        super.close();
        current = null;
    }
}
