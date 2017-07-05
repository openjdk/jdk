/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;

class PushPromiseFrame extends HeaderFrame {

    int padLength;
    int promisedStream;

    PushPromiseFrame() {
        type = TYPE;
    }

    public static final int TYPE = 0x5;

    // Flags
    public static final int END_HEADERS = 0x4;
    public static final int PADDED = 0x8;

    @Override
    public String toString() {
        return super.toString() + " promisedStreamid: " + promisedStream
                + " headerLength: " + headerLength;
    }

    @Override
    String flagAsString(int flag) {
        switch (flag) {
        case PADDED:
            return "PADDED";
        case END_HEADERS:
            return "END_HEADERS";
        }
        return super.flagAsString(flag);
    }

    public void setPadLength(int padLength) {
        this.padLength = padLength;
        flags |= PADDED;
    }

    public void setPromisedStream(int stream) {
        this.promisedStream = stream;
    }

    public int getPromisedStream() {
        return promisedStream;
    }

    /**
     */
    @Override
    void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
        if ((flags & PADDED) != 0) {
            padLength = bc.getByte();
            headerLength = length - (padLength + 5);
        } else
            headerLength = length - 4;

        promisedStream = bc.getInt() & 0x7fffffff;
        headerBlocks = bc.getBuffers(headerLength);
    }

    @Override
    void computeLength() {
        int len = 0;
        if ((flags & PADDED) != 0) {
            len += (1 + padLength);
        }
        len += (4 + headerLength);
        this.length = len;
    }

    @Override
    void writeOutgoing(ByteBufferGenerator bg) {
        super.writeOutgoing(bg);
        ByteBuffer buf = bg.getBuffer(length);
        if ((flags & PADDED) != 0) {
            buf.put((byte)padLength);
        }
        buf.putInt(promisedStream);
        for (int i=0; i<headerBlocks.length; i++) {
            bg.addByteBuffer(headerBlocks[i]);
        }
        if ((flags & PADDED) != 0) {
            bg.addPadding(padLength);
        }
    }

    @Override
    public boolean endHeaders() {
        return getFlag(END_HEADERS);
    }
}
