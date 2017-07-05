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

class PriorityFrame extends Http2Frame {

    int streamDependency;
    int weight;
    boolean exclusive;

    public final static int TYPE = 0x2;

    PriorityFrame() {
        type = TYPE;
    }

    public PriorityFrame(int streamDependency, boolean exclusive, int weight) {
        this.streamDependency = streamDependency;
        this.exclusive = exclusive;
        this.weight = weight;
        this.type = TYPE;
    }

    int streamDependency() {
        return streamDependency;
    }

    int weight() {
        return weight;
    }

    boolean exclusive() {
        return exclusive;
    }

    @Override
    void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
        int x = bc.getInt();
        exclusive = (x & 0x80000000) != 0;
        streamDependency = x & 0x7fffffff;
        weight = bc.getByte();
    }

    @Override
    void writeOutgoing(ByteBufferGenerator bg) {
        super.writeOutgoing(bg);
        ByteBuffer buf = bg.getBuffer(5);
        int x = exclusive ? (1 << 31) + streamDependency : streamDependency;
        buf.putInt(x);
        buf.put((byte)weight);
    }

    @Override
    void computeLength() {
        length = 5;
    }
}
