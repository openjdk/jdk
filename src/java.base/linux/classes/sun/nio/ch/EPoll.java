/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import jdk.internal.ffi.generated.epoll.epoll_data;
import jdk.internal.ffi.generated.epoll.epoll_event;
import jdk.internal.ffi.generated.epoll.epoll_h;
import jdk.internal.ffi.generated.errno.errno_h;
import jdk.internal.ffi.util.ErrnoTools;
import jdk.internal.ffi.util.FFMUtils;

import static jdk.internal.ffi.generated.epoll.epoll_h.epoll_create1;
import static jdk.internal.ffi.generated.epoll.epoll_h.epoll_ctl;
import static jdk.internal.ffi.generated.epoll.epoll_h.epoll_wait;

/**
 * Provides access to the Linux epoll facility.
 */

class EPoll {

    private EPoll() { }

    // opcodes
    static final int EPOLL_CTL_ADD = epoll_h.EPOLL_CTL_ADD();
    static final int EPOLL_CTL_DEL = epoll_h.EPOLL_CTL_DEL();
    static final int EPOLL_CTL_MOD = epoll_h.EPOLL_CTL_MOD();

    // events
    static final int EPOLLIN = epoll_h.EPOLLIN();
    static final int EPOLLOUT = epoll_h.EPOLLOUT();

    // flags
    static final int EPOLLONESHOT = epoll_h.EPOLLONESHOT();

    /**
     * Allocates a poll array to handle up to {@code count} events.
     */
    static MemorySegment allocatePollArray(int count) {
        return epoll_event.allocateArray(count, FFMUtils.SEGMENT_ALLOCATOR);
    }

    /**
     * Free a poll array
     */
    static void freePollArray(MemorySegment memoryHandle) {
        FFMUtils.free(memoryHandle);
    }

    /**
     * Returns event[i];
     */
    static MemorySegment getEvent(MemorySegment memoryHandle, int i) {
        return epoll_event.asSlice(memoryHandle, i);
    }

    /**
     * Returns event->data.fd
     */
    static int getDescriptor(MemorySegment eventMemory) {
        return epoll_data.fd(epoll_event.data(eventMemory));
    }

    /**
     * Returns event->events
     */
    static int getEvents(MemorySegment eventMemory) {
        return epoll_event.events(eventMemory);
    }

    static int create() throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errnoCaptureMS = ErrnoTools.allocateCaptureStateSegment(arena);
            int epfd = epoll_create1(errnoCaptureMS, epoll_h.EPOLL_CLOEXEC());
            if (epfd < 0) {
                int errno = ErrnoTools.errno(errnoCaptureMS);
                throw ErrnoTools.IOExceptionWithLastError(errno, "epoll_create1 failed.", arena);
            }
            return epfd;
        }
    }

    static int ctl(int epfd, int opcode, int fd, int events) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment epoll_eventMS = arena.allocate(epoll_event.layout());

            epoll_event.events(epoll_eventMS, events);
            var dataMS = epoll_event.data(epoll_eventMS);
            epoll_data.fd(dataMS, fd);
            MemorySegment errnoCaptureMS = ErrnoTools.allocateCaptureStateSegment(arena);
            int res = epoll_ctl(epfd, opcode, fd, epoll_eventMS, errnoCaptureMS);
            if (res == 0) {
                return 0;
            } else {
                return ErrnoTools.errno(errnoCaptureMS);
            }
        }
    }

    static int wait(int epfd, MemorySegment events, int numfds, int timeout)
            throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errnoCaptureMS = ErrnoTools.allocateCaptureStateSegment(arena);
            int res = epoll_wait(epfd, events, numfds, timeout, errnoCaptureMS);
            if (res < 0) {
                int errno = ErrnoTools.errno(errnoCaptureMS);
                if (errno == errno_h.EINTR()) {
                    return IOStatus.INTERRUPTED;
                } else {
                    throw ErrnoTools.IOExceptionWithLastError(errno, "epoll_wait failed.", arena);
                }
            }
            return res;
        }
    }
}
