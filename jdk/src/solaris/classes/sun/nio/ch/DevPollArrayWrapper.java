/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.util.BitSet;
import java.util.Map;
import java.util.HashMap;


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

    // Event masks
    static final short POLLIN       = 0x0001;
    static final short POLLPRI      = 0x0002;
    static final short POLLOUT      = 0x0004;
    static final short POLLRDNORM   = 0x0040;
    static final short POLLWRNORM   = POLLOUT;
    static final short POLLRDBAND   = 0x0080;
    static final short POLLWRBAND   = 0x0100;
    static final short POLLNORM     = POLLRDNORM;
    static final short POLLERR      = 0x0008;
    static final short POLLHUP      = 0x0010;
    static final short POLLNVAL     = 0x0020;
    static final short POLLREMOVE   = 0x0800;
    static final short POLLCONN     = POLLOUT;

    // Miscellaneous constants
    static final short SIZE_POLLFD   = 8;
    static final short FD_OFFSET     = 0;
    static final short EVENT_OFFSET  = 4;
    static final short REVENT_OFFSET = 6;

    // Special value to indicate that an update should be ignored
    static final byte  CANCELLED     = (byte)-1;

    // Maximum number of open file descriptors
    static final int   OPEN_MAX      = fdLimit();

    // Number of pollfd structures to create.
    // dpwrite/ioctl(DP_POLL) allows up to OPEN_MAX-1
    static final int   NUM_POLLFDS   = Math.min(OPEN_MAX-1, 8192);

    // Initial size of arrays for fd registration changes
    private final int INITIAL_PENDING_UPDATE_SIZE = 64;

    // maximum size of updatesLow
    private final int MAX_UPDATE_ARRAY_SIZE = Math.min(OPEN_MAX, 64*1024);

    // The pollfd array for results from devpoll driver
    private final AllocatedNativeObject pollArray;

    // Base address of the native pollArray
    private final long pollArrayAddress;

    // The fd of the devpoll driver
    private int wfd;

    // The fd of the interrupt line going out
    private int outgoingInterruptFD;

    // The fd of the interrupt line coming in
    private int incomingInterruptFD;

    // The index of the interrupt FD
    private int interruptedIndex;

    // Number of updated pollfd entries
    int updated;

    // object to synchronize fd registration changes
    private final Object updateLock = new Object();

    // number of file descriptors with registration changes pending
    private int updateCount;

    // file descriptors with registration changes pending
    private int[] updateDescriptors = new int[INITIAL_PENDING_UPDATE_SIZE];

    // events for file descriptors with registration changes pending, indexed
    // by file descriptor and stored as bytes for efficiency reasons. For
    // file descriptors higher than MAX_UPDATE_ARRAY_SIZE (unlimited case at
    // least then the update is stored in a map.
    private final byte[] eventsLow = new byte[MAX_UPDATE_ARRAY_SIZE];
    private Map<Integer,Byte> eventsHigh;

    // Used by release and updateRegistrations to track whether a file
    // descriptor is registered with /dev/poll.
    private final BitSet registered = new BitSet();

    DevPollArrayWrapper() {
        int allocationSize = NUM_POLLFDS * SIZE_POLLFD;
        pollArray = new AllocatedNativeObject(allocationSize, true);
        pollArrayAddress = pollArray.address();
        wfd = init();
        if (OPEN_MAX > MAX_UPDATE_ARRAY_SIZE)
            eventsHigh = new HashMap<>();
    }

    void initInterrupt(int fd0, int fd1) {
        outgoingInterruptFD = fd1;
        incomingInterruptFD = fd0;
        register(wfd, fd0, POLLIN);
    }

    void putReventOps(int i, int revent) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        pollArray.putShort(offset, (short)revent);
    }

    int getEventOps(int i) {
        int offset = SIZE_POLLFD * i + EVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    int getReventOps(int i) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        return pollArray.getShort(offset);
    }

    int getDescriptor(int i) {
        int offset = SIZE_POLLFD * i + FD_OFFSET;
        return pollArray.getInt(offset);
    }

    private void setUpdateEvents(int fd, byte events) {
        if (fd < MAX_UPDATE_ARRAY_SIZE) {
            eventsLow[fd] = events;
        } else {
            eventsHigh.put(Integer.valueOf(fd), Byte.valueOf(events));
        }
    }

    private byte getUpdateEvents(int fd) {
        if (fd < MAX_UPDATE_ARRAY_SIZE) {
            return eventsLow[fd];
        } else {
            Byte result = eventsHigh.get(Integer.valueOf(fd));
            // result should never be null
            return result.byteValue();
        }
    }

    void setInterest(int fd, int mask) {
        synchronized (updateLock) {
            // record the file descriptor and events, expanding the
            // respective arrays first if necessary.
            int oldCapacity = updateDescriptors.length;
            if (updateCount == oldCapacity) {
                int newCapacity = oldCapacity + INITIAL_PENDING_UPDATE_SIZE;
                int[] newDescriptors = new int[newCapacity];
                System.arraycopy(updateDescriptors, 0, newDescriptors, 0, oldCapacity);
                updateDescriptors = newDescriptors;
            }
            updateDescriptors[updateCount++] = fd;

            // events are stored as bytes for efficiency reasons
            byte b = (byte)mask;
            assert (b == mask) && (b != CANCELLED);
            setUpdateEvents(fd, b);
        }
    }

    void release(int fd) {
        synchronized (updateLock) {
            // cancel any pending update for this file descriptor
            setUpdateEvents(fd, CANCELLED);

            // remove from /dev/poll
            if (registered.get(fd)) {
                register(wfd, fd, POLLREMOVE);
                registered.clear(fd);
            }
        }
    }

    void closeDevPollFD() throws IOException {
        FileDispatcherImpl.closeIntFD(wfd);
        pollArray.free();
    }

    int poll(long timeout) throws IOException {
        updateRegistrations();
        updated = poll0(pollArrayAddress, NUM_POLLFDS, timeout, wfd);
        for (int i=0; i<updated; i++) {
            if (getDescriptor(i) == incomingInterruptFD) {
                interruptedIndex = i;
                interrupted = true;
                break;
            }
        }
        return updated;
    }

    void updateRegistrations() throws IOException {
        synchronized (updateLock) {
            // Populate pollfd array with updated masks
            int j = 0;
            int index = 0;
            while (j < updateCount) {
                int fd = updateDescriptors[j];
                short events = getUpdateEvents(fd);
                boolean isRegistered = registered.get(fd);

                // events = 0 => POLLREMOVE or do-nothing
                if (events != CANCELLED) {
                    if (events == 0) {
                        if (isRegistered) {
                            events = POLLREMOVE;
                            registered.clear(fd);
                        } else {
                            events = CANCELLED;
                        }
                    } else {
                        if (!isRegistered) {
                            registered.set(fd);
                        }
                    }
                }

                // populate pollfd array with updated event
                if (events != CANCELLED) {
                    putPollFD(pollArray, index, fd, events);
                    index++;
                    if (index >= NUM_POLLFDS) {
                        registerMultiple(wfd, pollArray.address(), index);
                        index = 0;
                    }
                }
                j++;
            }

            // write any remaining updates
            if (index > 0)
                registerMultiple(wfd, pollArray.address(), index);

            updateCount = 0;
        }
    }

    private void putPollFD(AllocatedNativeObject array, int index, int fd,
                           short event)
    {
        int structIndex = SIZE_POLLFD * index;
        array.putInt(structIndex + FD_OFFSET, fd);
        array.putShort(structIndex + EVENT_OFFSET, event);
        array.putShort(structIndex + REVENT_OFFSET, (short)0);
    }

    boolean interrupted = false;

    public void interrupt() {
        interrupt(outgoingInterruptFD);
    }

    public int interruptedIndex() {
        return interruptedIndex;
    }

    boolean interrupted() {
        return interrupted;
    }

    void clearInterrupted() {
        interrupted = false;
    }

    private native int init();
    private native void register(int wfd, int fd, int mask);
    private native void registerMultiple(int wfd, long address, int len)
        throws IOException;
    private native int poll0(long pollAddress, int numfds, long timeout,
                             int wfd);
    private static native void interrupt(int fd);
    private static native int fdLimit();
}
