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
package jdk.incubator.http.internal.common;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The class provides reuse of ByteBuffers.
 * It is supposed that all requested buffers have the same size for a long period of time.
 * That is why there is no any logic splitting buffers into different buckets (by size). It's unnecessary.
 *
 * At the same moment it is allowed to change requested buffers size (all smaller buffers will be discarded).
 * It may be needed for example, if after rehandshaking netPacketBufferSize was changed.
 */
public class ByteBufferPool {

    private final java.util.Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public ByteBufferPool() {
    }

    public ByteBufferReference get(int size) {
        ByteBuffer buffer;
        while ((buffer = pool.poll()) != null) {
            if (buffer.capacity() >= size) {
                return ByteBufferReference.of(buffer, this);
            }
        }
        return ByteBufferReference.of(ByteBuffer.allocate(size), this);
    }

    public void release(ByteBuffer buffer) {
        buffer.clear();
        pool.offer(buffer);
    }

}
