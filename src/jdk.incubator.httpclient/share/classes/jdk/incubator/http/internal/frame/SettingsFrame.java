/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.frame;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class SettingsFrame extends Http2Frame {

    private final int[] parameters;

    public static final int TYPE = 0x4;

    // Flags
    public static final int ACK = 0x1;

    @Override
    public String flagAsString(int flag) {
        switch (flag) {
        case ACK:
            return "ACK";
        }
        return super.flagAsString(flag);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
          .append(" Settings: ");

        for (int i = 0; i < MAX_PARAM; i++) {
            if (parameters[i] != -1) {
                sb.append(name(i))
                  .append("=")
                  .append(Integer.toString(parameters[i]))
                  .append(' ');
            }
        }
        return sb.toString();
    }

    // Parameters
    public static final int HEADER_TABLE_SIZE = 0x1;
    public static final int ENABLE_PUSH = 0x2;
    public static final int MAX_CONCURRENT_STREAMS = 0x3;
    public static final int INITIAL_WINDOW_SIZE = 0x4;
    public static final int MAX_FRAME_SIZE = 0x5;
    public static final int MAX_HEADER_LIST_SIZE = 0x6;

    private String name(int i) {
        switch (i+1) {
        case HEADER_TABLE_SIZE:
            return "HEADER_TABLE_SIZE";
        case ENABLE_PUSH:
            return "ENABLE_PUSH";
        case MAX_CONCURRENT_STREAMS:
            return "MAX_CONCURRENT_STREAMS";
        case INITIAL_WINDOW_SIZE:
            return "INITIAL_WINDOW_SIZE";
        case MAX_FRAME_SIZE:
            return "MAX_FRAME_SIZE";
        case MAX_HEADER_LIST_SIZE:
            return "MAX_HEADER_LIST_SIZE";
        }
        return "unknown parameter";
    }
    public static final int MAX_PARAM = 0x6;

    public SettingsFrame(int flags) {
        super(0, flags);
        parameters = new int [MAX_PARAM];
        Arrays.fill(parameters, -1);
    }

    public SettingsFrame() {
        this(0);
    }

    public SettingsFrame(SettingsFrame other) {
        super(0, other.flags);
        parameters = Arrays.copyOf(other.parameters, MAX_PARAM);
    }

    @Override
    public int type() {
        return TYPE;
    }

    public int getParameter(int paramID) {
        if (paramID > MAX_PARAM) {
            throw new IllegalArgumentException("illegal parameter");
        }
        return parameters[paramID-1];
    }

    public SettingsFrame setParameter(int paramID, int value) {
        if (paramID > MAX_PARAM) {
            throw new IllegalArgumentException("illegal parameter");
        }
        parameters[paramID-1] = value;
        return this;
    }

    int length() {
        int len = 0;
        for (int i : parameters) {
            if (i != -1) {
                len  += 6;
            }
        }
        return len;
    }

    void toByteBuffer(ByteBuffer buf) {
        for (int i = 0; i < MAX_PARAM; i++) {
            if (parameters[i] != -1) {
                buf.putShort((short) (i + 1));
                buf.putInt(parameters[i]);
            }
        }
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[length()];
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        toByteBuffer(buf);
        return bytes;
    }

    private static final int K = 1024;

    public static SettingsFrame getDefaultSettings() {
        SettingsFrame f = new SettingsFrame();
        // TODO: check these values
        f.setParameter(ENABLE_PUSH, 1);
        f.setParameter(HEADER_TABLE_SIZE, 4 * K);
        f.setParameter(MAX_CONCURRENT_STREAMS, 35);
        f.setParameter(INITIAL_WINDOW_SIZE, 64 * K - 1);
        f.setParameter(MAX_FRAME_SIZE, 16 * K);
        return f;
    }
}
