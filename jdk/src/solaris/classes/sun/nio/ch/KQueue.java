/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.IOException;
import sun.misc.Unsafe;

/**
 * Provides access to the BSD kqueue facility.
 */

class KQueue {
    private KQueue() { }

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * struct kevent {
     *        uintptr_t       ident;          // identifier for this event, usually the fd
     *        int16_t         filter;         // filter for event
     *        uint16_t        flags;          // general flags
     *        uint32_t        fflags;         // filter-specific flags
     *        intptr_t        data;           // filter-specific data
     *        void            *udata;         // opaque user data identifier
     * };
     */
    private static final int SIZEOF_KQUEUEEVENT    = keventSize();
    private static final int OFFSET_IDENT          = identOffset();
    private static final int OFFSET_FILTER         = filterOffset();
    private static final int OFFSET_FLAGS          = flagsOffset();

    // filters
    static final int EVFILT_READ  = -1;
    static final int EVFILT_WRITE = -2;

    // flags
    static final int EV_ADD     = 0x0001;
    static final int EV_ONESHOT = 0x0010;
    static final int EV_CLEAR   = 0x0020;

    /**
     * Allocates a poll array to handle up to {@code count} events.
     */
    static long allocatePollArray(int count) {
        return unsafe.allocateMemory(count * SIZEOF_KQUEUEEVENT);
    }

    /**
     * Free a poll array
     */
    static void freePollArray(long address) {
        unsafe.freeMemory(address);
    }

    /**
     * Returns kevent[i].
     */
    static long getEvent(long address, int i) {
        return address + (SIZEOF_KQUEUEEVENT*i);
    }

    /**
     * Returns the file descriptor from a kevent (assuming to be in ident field)
     */
    static int getDescriptor(long address) {
        return unsafe.getInt(address + OFFSET_IDENT);
    }

    static int getFilter(long address) {
        return unsafe.getShort(address + OFFSET_FILTER);
    }

    static int getFlags(long address) {
        return unsafe.getShort(address + OFFSET_FLAGS);
    }

    // -- Native methods --

    private static native int keventSize();

    private static native int identOffset();

    private static native int filterOffset();

    private static native int flagsOffset();

    static native int kqueue() throws IOException;

    static native int keventRegister(int kqpfd, int fd, int filter, int flags);

    static native int keventPoll(int kqpfd, long pollAddress, int nevents)
        throws IOException;

    static {
        IOUtil.load();
    }
}
