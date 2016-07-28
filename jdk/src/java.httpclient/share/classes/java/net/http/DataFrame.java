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
 * questions.
 */

package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;

class DataFrame extends Http2Frame {

    public final static int TYPE = 0x0;

    DataFrame() {
        type = TYPE;
    }

    // Flags
    public static final int END_STREAM = 0x1;
    public static final int PADDED = 0x8;

    int padLength;
    int dataLength;
    ByteBuffer[] data;

    public void setData(ByteBuffer[] data) {
        this.data = data;
        setDataLength();
    }

    @Override
    String flagAsString(int flag) {
        switch (flag) {
        case END_STREAM:
            return "END_STREAM";
        case PADDED:
            return "PADDED";
        }
        return super.flagAsString(flag);
    }

    public synchronized void setData(ByteBuffer data) {
        ByteBuffer[] bb;
        if (data == null) {
            bb = new ByteBuffer[0];
        } else {
            bb = new ByteBuffer[1];
            bb[0] = data;
        }
        setData(bb);
    }

    public synchronized ByteBuffer[] getData() {
        return data;
    }

    private void setDataLength() {
        int len = 0;
        for (ByteBuffer buf : data) {
            len += buf.remaining();
        }
        dataLength = len;
    }

    @Override
    void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
        if ((flags & PADDED) != 0) {
            padLength = bc.getByte();
            dataLength = length - (padLength + 1);
        } else {
            dataLength = length;
        }
        data = bc.getBuffers(dataLength);
    }

    int getPadLength() {
        return padLength;
    }

    int getDataLength() {
        return dataLength;
    }

    @Override
    void writeOutgoing(ByteBufferGenerator bg) {
        super.writeOutgoing(bg);
        if ((flags & PADDED) != 0) {
            ByteBuffer buf = bg.getBuffer(1);
            buf.put((byte)getPadLength());
        }
        for (int i=0; i<data.length; i++) {
            bg.addByteBuffer(data[i]);
        }
        if ((flags & PADDED) != 0) {
            bg.addPadding(padLength);
        }
    }

    @Override
    void computeLength() {
        length = dataLength;
        if ((flags & PADDED) != 0) {
            length += (1 + padLength);
        }
    }
}
