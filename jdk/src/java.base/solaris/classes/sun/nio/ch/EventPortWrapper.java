/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AccessController;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import jdk.internal.misc.Unsafe;
import sun.security.action.GetIntegerAction;
import static sun.nio.ch.SolarisEventPort.*;

/**
 * Manages a Solaris event port and manipulates a native array of pollfd structs
 * on Solaris.
 */

class EventPortWrapper {
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final int addressSize = unsafe.addressSize();

    // Maximum number of open file descriptors
    static final int   OPEN_MAX     = IOUtil.fdLimit();

    // Maximum number of events to retrive in one call to port_getn
    static final int   POLL_MAX     =  Math.min(OPEN_MAX-1, 1024);

    // initial size of the array to hold pending updates
    private final int INITIAL_PENDING_UPDATE_SIZE = 256;

    // maximum size of updateArray
    private static final int MAX_UPDATE_ARRAY_SIZE = AccessController.doPrivileged(
        new GetIntegerAction("sun.nio.ch.maxUpdateArraySize", Math.min(OPEN_MAX, 64*1024)));

    // special update status to indicate that it should be ignored
    private static final byte IGNORE = -1;

    // port file descriptor
    private final int pfd;

    // the poll array (populated by port_getn)
    private final long pollArrayAddress;
    private final AllocatedNativeObject pollArray;

    // required when accessing the update* fields
    private final Object updateLock = new Object();

    // the number of pending updates
    private int updateCount;

    // queue of file descriptors with updates pending
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

    // bit set to indicate if a file descriptor has been visited when
    // processing updates (used to avoid duplicates calls to port_associate)
    private BitSet visited = new BitSet();

    EventPortWrapper() throws IOException {
        int allocationSize = POLL_MAX * SIZEOF_PORT_EVENT;
        pollArray = new AllocatedNativeObject(allocationSize, true);
        pollArrayAddress = pollArray.address();
        this.pfd = port_create();
        if (OPEN_MAX > MAX_UPDATE_ARRAY_SIZE)
            eventsHigh = new HashMap<>();
    }

    void close() throws IOException {
        port_close(pfd);
        pollArray.free();
    }

    private short getSource(int i) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_SOURCE;
        return pollArray.getShort(offset);
    }

    int getEventOps(int i) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_EVENTS;
        return pollArray.getInt(offset);
    }

    int getDescriptor(int i) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_OBJECT;
        if (addressSize == 4) {
            return pollArray.getInt(offset);
        } else {
            return (int) pollArray.getLong(offset);
        }
    }

    private void setDescriptor(int i, int fd) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_OBJECT;
        if (addressSize == 4) {
            pollArray.putInt(offset, fd);
        } else {
            pollArray.putLong(offset, fd);
        }
    }

    private void setUpdate(int fd, byte events) {
        if (fd < MAX_UPDATE_ARRAY_SIZE) {
            eventsLow[fd] = events;
        } else {
            eventsHigh.put(Integer.valueOf(fd), Byte.valueOf(events));
        }
    }

    private byte getUpdate(int fd) {
        if (fd < MAX_UPDATE_ARRAY_SIZE) {
            return eventsLow[fd];
        } else {
            Byte result = eventsHigh.get(Integer.valueOf(fd));
            // result should never be null
            return result.byteValue();
        }
    }

    int poll(long timeout) throws IOException {
        // update registrations prior to poll
        synchronized (updateLock) {

            // process newest updates first
            int i = updateCount - 1;
            while (i >= 0) {
                int fd = updateDescriptors[i];
                if (!visited.get(fd)) {
                    short ev = getUpdate(fd);
                    if (ev != IGNORE) {
                        if (ev == 0) {
                            if (registered.get(fd)) {
                                port_dissociate(pfd, PORT_SOURCE_FD, (long)fd);
                                registered.clear(fd);
                            }
                        } else {
                            if (port_associate(pfd, PORT_SOURCE_FD, (long)fd, ev)) {
                                registered.set(fd);
                            }
                        }

                    }
                    visited.set(fd);
                }
                i--;
            }
            updateCount = 0;
        }

        // poll for events
        int updated = port_getn(pfd, pollArrayAddress, POLL_MAX, timeout);

        // after polling we need to queue all polled file descriptors as they
        // are candidates to register for the next poll.
        synchronized (updateLock) {
            for (int i=0; i<updated; i++) {
                if (getSource(i) == PORT_SOURCE_USER) {
                    interrupted = true;
                    setDescriptor(i, -1);
                } else {
                    // the default is to re-associate for the next poll
                    int fd = getDescriptor(i);
                    registered.clear(fd);
                    setInterest(fd);
                }
            }
        }

        return updated;
    }

    private void setInterest(int fd) {
        assert Thread.holdsLock(updateLock);

        // record the file descriptor and events, expanding the
        // respective arrays first if necessary.
        int oldCapacity = updateDescriptors.length;
        if (updateCount >= oldCapacity) {
            int newCapacity = oldCapacity + INITIAL_PENDING_UPDATE_SIZE;
            int[] newDescriptors = new int[newCapacity];
            System.arraycopy(updateDescriptors, 0, newDescriptors, 0, oldCapacity);
            updateDescriptors = newDescriptors;
        }
        updateDescriptors[updateCount++] = fd;
        visited.clear(fd);
    }

    void setInterest(int fd, int mask) {
        synchronized (updateLock) {
            setInterest(fd);
            setUpdate(fd, (byte)mask);
            assert getUpdate(fd) == mask;
        }
    }

    void release(int fd) {
        synchronized (updateLock) {
            if (registered.get(fd)) {
                try {
                    port_dissociate(pfd, PORT_SOURCE_FD, (long)fd);
                } catch (IOException ioe) {
                    throw new InternalError(ioe);
                }
                registered.clear(fd);
            }
            setUpdate(fd, IGNORE);
        }
    }

    // -- wakeup support --

    private boolean interrupted;

    public void interrupt() {
        try {
            port_send(pfd, 0);
        } catch (IOException ioe) {
            throw new InternalError(ioe);
        }
    }

    boolean interrupted() {
        return interrupted;
    }

    void clearInterrupted() {
        interrupted = false;
    }
}
