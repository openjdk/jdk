/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import sun.misc.*;
import java.io.IOException;
import java.util.LinkedList;


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

    // Maximum number of open file descriptors
    static final int   OPEN_MAX      = fdLimit();

    // Number of pollfd structures to create.
    // DP_POLL ioctl allows up to OPEN_MAX-1
    static final int   NUM_POLLFDS   = Math.min(OPEN_MAX-1, 8192);

    // Base address of the native pollArray
    private long pollArrayAddress;

    // Maximum number of POLL_FD structs to update at once
    private int MAX_UPDATE_SIZE = 10000;

    DevPollArrayWrapper() {
        int allocationSize = NUM_POLLFDS * SIZE_POLLFD;
        pollArray = new AllocatedNativeObject(allocationSize, true);
        pollArrayAddress = pollArray.address();
        wfd = init();

        for (int i=0; i<NUM_POLLFDS; i++) {
            putDescriptor(i, 0);
            putEventOps(i, 0);
            putReventOps(i, 0);
        }
    }

    // Machinery for remembering fd registration changes
    // A hashmap could be used but the number of changes pending
    // is expected to be small
    private static class Updator {
        int fd;
        int mask;
        Updator(int fd, int mask) {
            this.fd = fd;
            this.mask = mask;
        }
    }
    private LinkedList<Updator> updateList = new LinkedList<Updator>();

    // The pollfd array for results from devpoll driver
    private AllocatedNativeObject pollArray;

    // The fd of the devpoll driver
    int wfd;

    // The fd of the interrupt line going out
    int outgoingInterruptFD;

    // The fd of the interrupt line coming in
    int incomingInterruptFD;

    // The index of the interrupt FD
    int interruptedIndex;

    // Number of updated pollfd entries
    int updated;

    void initInterrupt(int fd0, int fd1) {
        outgoingInterruptFD = fd1;
        incomingInterruptFD = fd0;
        register(wfd, fd0, POLLIN);
    }

    void putEventOps(int i, int event) {
        int offset = SIZE_POLLFD * i + EVENT_OFFSET;
        pollArray.putShort(offset, (short)event);
    }

    void putReventOps(int i, int revent) {
        int offset = SIZE_POLLFD * i + REVENT_OFFSET;
        pollArray.putShort(offset, (short)revent);
    }

    void putDescriptor(int i, int fd) {
        int offset = SIZE_POLLFD * i + FD_OFFSET;
        pollArray.putInt(offset, fd);
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

    void setInterest(int fd, int mask) {
        synchronized (updateList) {
            updateList.add(new Updator(fd, mask));
        }
    }

    void release(int fd) {
        synchronized (updateList) {
            updateList.add(new Updator(fd, POLLREMOVE));
        }
    }

    void closeDevPollFD() throws IOException {
        FileDispatcher.closeIntFD(wfd);
        pollArray.free();
    }

    int poll(long timeout) {
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

    void updateRegistrations() {
        // take snapshot of the updateList size to see if there are
        // any registrations to update
        int updateSize;
        synchronized (updateList) {
            updateSize = updateList.size();
        }
        if (updateSize > 0) {
            // Construct a pollfd array with updated masks; we may overallocate
            // by some amount because if the events are already POLLREMOVE
            // then the second pollfd of that pair will not be needed. The
            // number of entries is limited to a reasonable number to avoid
            // allocating a lot of memory.
            int maxUpdates = Math.min(updateSize * 2, MAX_UPDATE_SIZE);
            int allocationSize =  maxUpdates * SIZE_POLLFD;
            AllocatedNativeObject updatePollArray =
                new AllocatedNativeObject(allocationSize, true);

            try {
                synchronized (updateList) {
                    while (updateList.size() > 0) {
                        // We have to insert a dummy node in between each
                        // real update to use POLLREMOVE on the fd first because
                        // otherwise the changes are simply OR'd together
                        int index = 0;
                        Updator u = null;
                        while ((u = updateList.poll()) != null) {
                            // First add pollfd struct to clear out this fd
                            putPollFD(updatePollArray, index, u.fd,
                                      (short)POLLREMOVE);
                            index++;
                            // Now add pollfd to update this fd, if necessary
                            if (u.mask != POLLREMOVE) {
                                putPollFD(updatePollArray, index, u.fd,
                                          (short)u.mask);
                                index++;
                            }

                            // Check against the max allocation size; these are
                            // all we will process. Valid index ranges from 0 to
                            // (maxUpdates - 1) and we can use up to 2 per loop
                            if (index > maxUpdates - 2)
                                break;
                        }
                        // Register the changes with /dev/poll
                        registerMultiple(wfd, updatePollArray.address(), index);
                     }
                }
            } finally {
                // Free the native array
                updatePollArray.free();
                // BUG: If an exception was thrown then the selector now believes
                // that the last set of changes was updated but it probably
                // was not. This should not be a likely occurrence.
            }
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
    private native void registerMultiple(int wfd, long address, int len);
    private native int poll0(long pollAddress, int numfds, long timeout,
                             int wfd);
    private static native void interrupt(int fd);
    private static native int fdLimit();

}
