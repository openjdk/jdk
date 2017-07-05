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

/**
 * When sending a frame, the length field must be set in sub-class
 * by calling computeLength()
 */
abstract class Http2Frame {

    int length = -1;
    int type;
    int streamid;
    int flags;

    // called when reading in only
    void initCommon(int length, int type, int streamid, int flags) {
        this.length = length;
        this.type = type;
        this.streamid = streamid;
        this.flags = flags;
    }

    public int length() {
        return length;
    }

    public int type() {
        return type;
    }

    public int streamid() {
        return streamid;
    }

    public void setFlag(int flag) {
        flags |= flag;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public boolean getFlag(int flag) {
        return (flags & flag) != 0;
    }

    public void clearFlag(int flag) {
        flags &= 0xffffffff ^ flag;
    }

    public void streamid(int streamid) {
        this.streamid = streamid;
    }

    abstract void readIncomingImpl(ByteBufferConsumer bc) throws IOException;

    /**
     * assume given array contains at least one complete frame.
     */
    static Http2Frame readIncoming(ByteBufferConsumer bc) throws IOException {
        int x = bc.getInt();
        int length = x >> 8;
        int type = x & 0xff;
        int flags = bc.getByte();
        int streamid = bc.getInt();
        Http2Frame f = null;
        switch (type) {
          case DataFrame.TYPE:
            f = new DataFrame();
            break;
          case HeadersFrame.TYPE:
            f = new HeadersFrame();
            break;
          case ContinuationFrame.TYPE:
            f = new ContinuationFrame();
            break;
          case ResetFrame.TYPE:
            f = new ResetFrame();
            break;
          case PriorityFrame.TYPE:
            f = new PriorityFrame();
            break;
          case SettingsFrame.TYPE:
            f = new SettingsFrame();
            break;
          case GoAwayFrame.TYPE:
            f = new GoAwayFrame();
            break;
          case PingFrame.TYPE:
            f = new PingFrame();
            break;
          case PushPromiseFrame.TYPE:
            f = new PushPromiseFrame();
            break;
          case WindowUpdateFrame.TYPE:
            f = new WindowUpdateFrame();
            break;
          default:
            String msg = Integer.toString(type);
            throw new IOException("unknown frame type " + msg);
        }
        f.initCommon(length, type, streamid, flags);
        f.readIncomingImpl(bc);
        return f;
    }

    public String typeAsString() {
        return asString(this.type);
    }

    public static String asString(int type) {
        switch (type) {
          case DataFrame.TYPE:
            return "DATA";
          case HeadersFrame.TYPE:
            return "HEADERS";
          case ContinuationFrame.TYPE:
            return "CONTINUATION";
          case ResetFrame.TYPE:
            return "RESET";
          case PriorityFrame.TYPE:
            return "PRIORITY";
          case SettingsFrame.TYPE:
            return "SETTINGS";
          case GoAwayFrame.TYPE:
            return "GOAWAY";
          case PingFrame.TYPE:
            return "PING";
          case PushPromiseFrame.TYPE:
            return "PUSH_PROMISE";
          case WindowUpdateFrame.TYPE:
            return "WINDOW_UPDATE";
          default:
            return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(typeAsString())
                .append(": length=")
                .append(Integer.toString(length))
                .append(", streamid=")
                .append(streamid)
                .append(", flags=");

        int f = flags;
        int i = 0;
        if (f == 0) {
            sb.append("0 ");
        } else {
            while (f != 0) {
                if ((f & 1) == 1) {
                    sb.append(flagAsString(1 << i))
                      .append(' ');
                }
                f = f >> 1;
                i++;
            }
        }
        return sb.toString();
    }

    // Override
    String flagAsString(int f) {
        return "unknown";
    }

    abstract void computeLength();

    void writeOutgoing(ByteBufferGenerator bg) {
        if (length == -1) {
            throw new InternalError("Length not set on outgoing frame");
        }
        ByteBuffer buf = bg.getBuffer(9);
        int x = (length << 8) + type;
        buf.putInt(x);
        buf.put((byte)flags);
        buf.putInt(streamid);
    }
}
