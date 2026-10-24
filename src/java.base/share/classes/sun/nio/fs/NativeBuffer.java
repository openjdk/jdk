/*
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import jdk.internal.misc.Unsafe;

/**
 * A light-weight buffer in native memory.
 */

class NativeBuffer implements AutoCloseable {
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private final long address;
    private final int size;

    // optional "owner" to avoid copying
    // (only safe for use by thread-local caches)
    private Object owner;

    // owner thread ID
    private long ownerTid;

    NativeBuffer(int size) {
        this.address = unsafe.allocateMemory(size);
        this.size = size;
    }

    @Override
    public void close() {
        release();
    }

    void release() {
        NativeBuffers.releaseNativeBuffer(this);
    }

    long address() {
        return address;
    }

    int size() {
        return size;
    }

    void free() {
        unsafe.freeMemory(address);
    }

    // not synchronized; only safe for use by thread-local caches
    void setOwner(Object owner) {
        Thread thread = Thread.currentThread();
        assert !thread.isVirtual();
        assert ownerTid == 0 || ownerTid == thread.threadId();
        this.owner = owner;
        this.ownerTid = (owner != null) ? thread.threadId() : 0;
    }

    // not synchronized; only safe for use by thread-local caches
    Object owner() {
        long tid = Thread.currentThread().threadId();
        assert ownerTid == 0 || ownerTid == tid;
        if (ownerTid == tid) {
            return owner;
        } else {
            return null;
        }
    }
}
