/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Manipulates a native array of pollfd structs on Solaris:
 *
 * typedef struct pollfd {
 *    int fd;
 *    short events;
 *    short revents;
 * } pollfd_t;
 *
 * @author Mike McCloskey
 * @since 1.4
 */

class DevPollArrayWrapper {

    // special event to remove a file descriptor from the driver
    static final short POLLREMOVE   = 0x0800;

    // struct pollfd constants
    static final short SIZE_POLLFD   = 8;
    static final short FD_OFFSET     = 0;
    static final short EVENT_OFFSET  = 4;
    static final short REVENT_OFFSET = 6;

    // maximum number of pollfd structure to poll or update at a time
    // dpwrite/ioctl(DP_POLL) allows up to file descriptor limit minus 1
    static final int NUM_POLLFDS = Math.min(IOUtil.fdLimit()-1, 1024);

    // The pollfd array for results from devpoll driver
    private final AllocatedNativeObject pollArray;

    // Base address of the native pollArray
    private final long pollArrayAddress;

    // The fd of the devpoll driver
    private int wfd;

    DevPollArrayWrapper() throws IOException {
        this.wfd = init();

        int allocationSize = NUM_POLLFDS * SIZE_POLLFD;
        this.pollArray = new AllocatedNativeObject(allocationSize, true);
        this.pollArrayAddress = pollArray.address();
    }

    void close() throws IOException {
        FileDispatcherImpl.closeIntFD(wfd);
        pollArray.free();
    }

    void register(int fd, int ops) throws IOException {
        register(wfd, fd, ops);
    }

    void registerMultiple(int numfds) throws IOException {
        registerMultiple(wfd, pollArrayAddress, numfds);
    }

    int poll(long timeout) throws IOException {
        return poll0(pollArrayAddress, NUM_POLLFDS, timeout, wfd);
    }

    int getDescriptor(int i) {
        int offset = SIZE_POLLFD * i + FD_OFFSET;
        return pollArray.getInt(offset);
    }

    short getEventOps(int i) {
        int offset = SIZE_POLLFD * i + EVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    short getReventOps(int i) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    /**
     * Updates the pollfd structure at the given index
     */
    void putPollFD(int index, int fd, short event) {
        int structIndex = SIZE_POLLFD * index;
        pollArray.putInt(structIndex + FD_OFFSET, fd);
        pollArray.putShort(structIndex + EVENT_OFFSET, event);
        pollArray.putShort(structIndex + REVENT_OFFSET, (short)0);
    }

    private native int init() throws IOException;
    private native void register(int wfd, int fd, int mask) throws IOException;
    private native void registerMultiple(int wfd, long address, int len)
        throws IOException;
    private native int poll0(long pollAddress, int numfds, long timeout, int wfd)
        throws IOException;

    static {
        IOUtil.load();
    }
}
