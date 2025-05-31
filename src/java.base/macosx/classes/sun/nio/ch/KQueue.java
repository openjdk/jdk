/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.ffi.generated.errno.errno_h;
import jdk.internal.ffi.generated.timespec.timespec;
import jdk.internal.ffi.generated.kqueue.kevent;
import jdk.internal.ffi.generated.kqueue.kqueue_h;
import jdk.internal.ffi.util.FFMUtils;

import java.io.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.MemorySegment.NULL;

/**
 * Provides access to the BSD kqueue facility.
 */

class KQueue {
    private KQueue() { }

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

    // filters
    static final int EVFILT_READ  = kqueue_h.EVFILT_READ();
    static final int EVFILT_WRITE = kqueue_h.EVFILT_WRITE();

    // flags
    static final int EV_ADD     = kqueue_h.EV_ADD();
    static final int EV_DELETE  = kqueue_h.EV_DELETE();
    static final int EV_ONESHOT = kqueue_h.EV_ONESHOT();
    static final int EV_CLEAR   = kqueue_h.EV_CLEAR();

    /**
     * Allocates a poll array to handle up to {@code count} events.
     */
    static MemorySegment allocatePollArray(int count) {
        return kevent.allocateArray(count, FFMUtils.SEGMENT_ALLOCATOR);
    }

    /**
     * Free a poll array
     */
    static void freePollArray(MemorySegment memorySegment){
        FFMUtils.free(memorySegment);
    }

    /**
     * Returns kevent[i].
     */
    static MemorySegment getEvent(MemorySegment memoryHandle, int i) {
        return kevent.asSlice(memoryHandle, i);
    }

    /**
     * Returns the file descriptor from a kevent (assuming it is in the ident field)
     */
    static long getDescriptor(MemorySegment memoryHandle) {
        return kevent.ident(memoryHandle);
    }

    static short getFilter(MemorySegment memoryHandle) {
        return kevent.filter(memoryHandle);
    }

    static short getFlags(MemorySegment memoryHandle) {
        return kevent.flags(memoryHandle);
    }

    // -- Native methods --

    static public int register(int kqfd, int fd, int filter, int flags) {
        int result;
        try (var arena = Arena.ofConfined()) {
            try {
                MemorySegment keventMS = arena.allocate(kevent.layout());
                kevent.ident(keventMS, fd);
                kevent.filter(keventMS, (short) filter);
                kevent.flags(keventMS, (short) flags);
                // rest default to zero

                // this do-while replaces restartable
                do {
                    result = kqueue_h.kevent(
                            kqfd, keventMS, 1, NULL,
                            0, NULL);
                } while ((result == -1));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    static public int poll(int kqfd, MemorySegment pollAddress, int nevents, long timeout) {
        int result;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment tsMS = arena.allocate(timespec.layout());
            MemorySegment tsp;

            if (timeout >= 0) {
                timespec.tv_sec(tsMS, timeout / 1000);
                timespec.tv_nsec(tsMS, (timeout % 1000) * 1000000);
                tsp = tsMS;
            } else {
                tsp = NULL;
            }

            result = kqueue_h.kevent(
                    kqfd, NULL, 0, pollAddress,
                    nevents, tsp);
            if (result < 0) {
                if (result == errno_h.EINTR()) {
                    return IOStatus.INTERRUPTED;
                } else {
                    throw new IOException("kqueue_poll failed");
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
