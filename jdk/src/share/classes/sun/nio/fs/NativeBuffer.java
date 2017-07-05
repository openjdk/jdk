/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import sun.misc.Unsafe;
import sun.misc.Cleaner;

/**
 * A light-weight buffer in native memory.
 */

class NativeBuffer {
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private final long address;
    private final int size;
    private final Cleaner cleaner;

    // optional "owner" to avoid copying
    // (only safe for use by thread-local caches)
    private Object owner;

    private static class Deallocator implements Runnable {
        private final long address;
        Deallocator(long address) {
            this.address = address;
        }
        public void run() {
            unsafe.freeMemory(address);
        }
    }

    NativeBuffer(int size) {
        this.address = unsafe.allocateMemory(size);
        this.size = size;
        this.cleaner = Cleaner.create(this, new Deallocator(address));
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

    Cleaner cleaner() {
        return cleaner;
    }

    // not synchronized; only safe for use by thread-local caches
    void setOwner(Object owner) {
        this.owner = owner;
    }

    // not synchronized; only safe for use by thread-local caches
    Object owner() {
        return owner;
    }
}
