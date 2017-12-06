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
import java.util.Objects;
import java.util.function.Supplier;

public class ByteBufferReference  implements Supplier<ByteBuffer> {

    private ByteBuffer buffer;
    private final ByteBufferPool pool;

    public static ByteBufferReference of(ByteBuffer buffer) {
        return of(buffer, null);
    }

    public static ByteBufferReference of(ByteBuffer buffer, ByteBufferPool pool) {
        Objects.requireNonNull(buffer);
        return new ByteBufferReference(buffer, pool);
    }

    public static ByteBuffer[] toBuffers(ByteBufferReference... refs) {
        ByteBuffer[] bufs = new ByteBuffer[refs.length];
        for (int i = 0; i < refs.length; i++) {
            bufs[i] = refs[i].get();
        }
        return bufs;
    }

    public static ByteBufferReference[] toReferences(ByteBuffer... buffers) {
        ByteBufferReference[] refs = new ByteBufferReference[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            refs[i] = of(buffers[i]);
        }
        return refs;
    }


    public static void clear(ByteBufferReference[] refs) {
        for(ByteBufferReference ref : refs) {
            ref.clear();
        }
    }

    private ByteBufferReference(ByteBuffer buffer, ByteBufferPool pool) {
        this.buffer = buffer;
        this.pool = pool;
    }

    @Override
    public ByteBuffer get() {
        ByteBuffer buf = this.buffer;
        assert buf!=null : "getting ByteBuffer after clearance";
        return buf;
    }

    public void clear() {
        ByteBuffer buf = this.buffer;
        assert buf!=null : "double ByteBuffer clearance";
        this.buffer = null;
        if (pool != null) {
            pool.release(buf);
        }
    }
}
