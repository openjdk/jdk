/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;
import java.util.LinkedList;
import java.util.HashSet;

/**
 * Manipulates a native array of epoll_event structs on Linux:
 *
 * typedef union epoll_data {
 *     void *ptr;
 *     int fd;
 *     __uint32_t u32;
 *     __uint64_t u64;
 *  } epoll_data_t;
 *
 * struct epoll_event {
 *     __uint32_t events;
 *     epoll_data_t data;
 * };
 *
 * The system call to wait for I/O events is epoll_wait(2). It populates an
 * array of epoll_event structures that are passed to the call. The data
 * member of the epoll_event structure contains the same data as was set
 * when the file descriptor was registered to epoll via epoll_ctl(2). In
 * this implementation we set data.fd to be the file descriptor that we
 * register. That way, we have the file descriptor available when we
 * process the events.
 *
 * All file descriptors registered with epoll have the POLLHUP and POLLERR
 * events enabled even when registered with an event set of 0. To ensure
 * that epoll_wait doesn't poll an idle file descriptor when the underlying
 * connection is closed or reset then its registration is deleted from
 * epoll (it will be re-added again if the event set is changed)
 */

class EPollArrayWrapper {
    // EPOLL_EVENTS
    static final int EPOLLIN      = 0x001;

    // opcodes
    static final int EPOLL_CTL_ADD      = 1;
    static final int EPOLL_CTL_DEL      = 2;
    static final int EPOLL_CTL_MOD      = 3;

    // Miscellaneous constants
    static final int SIZE_EPOLLEVENT  = sizeofEPollEvent();
    static final int EVENT_OFFSET     = 0;
    static final int DATA_OFFSET      = offsetofData();
    static final int FD_OFFSET        = DATA_OFFSET;
    static final int NUM_EPOLLEVENTS  = Math.min(fdLimit(), 8192);

    // Base address of the native pollArray
    private final long pollArrayAddress;

    // Set of "idle" file descriptors
    private final HashSet<Integer> idleSet;

    EPollArrayWrapper() {
        // creates the epoll file descriptor
        epfd = epollCreate();

        // the epoll_event array passed to epoll_wait
        int allocationSize = NUM_EPOLLEVENTS * SIZE_EPOLLEVENT;
        pollArray = new AllocatedNativeObject(allocationSize, true);
        pollArrayAddress = pollArray.address();

        for (int i=0; i<NUM_EPOLLEVENTS; i++) {
            putEventOps(i, 0);
            putData(i, 0L);
        }

        // create idle set
        idleSet = new HashSet<Integer>();
    }

    // Used to update file description registrations
    private static class Updator {
        int opcode;
        int fd;
        int events;
        Updator(int opcode, int fd, int events) {
            this.opcode = opcode;
            this.fd = fd;
            this.events = events;
        }
    }

    private LinkedList<Updator> updateList = new LinkedList<Updator>();

    // The epoll_event array for results from epoll_wait
    private AllocatedNativeObject pollArray;

    // The fd of the epoll driver
    final int epfd;

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
        epollCtl(epfd, EPOLL_CTL_ADD, fd0, EPOLLIN);
    }

    void putEventOps(int i, int event) {
        int offset = SIZE_EPOLLEVENT * i + EVENT_OFFSET;
        pollArray.putInt(offset, event);
    }

    void putData(int i, long value) {
        int offset = SIZE_EPOLLEVENT * i + DATA_OFFSET;
        pollArray.putLong(offset, value);
    }

    void putDescriptor(int i, int fd) {
        int offset = SIZE_EPOLLEVENT * i + FD_OFFSET;
        pollArray.putInt(offset, fd);
    }

    int getEventOps(int i) {
        int offset = SIZE_EPOLLEVENT * i + EVENT_OFFSET;
        return pollArray.getInt(offset);
    }

    int getDescriptor(int i) {
        int offset = SIZE_EPOLLEVENT * i + FD_OFFSET;
        return pollArray.getInt(offset);
    }

    /**
     * Update the events for a given file descriptor.
     */
    void setInterest(int fd, int mask) {
        synchronized (updateList) {

            // if the interest events are 0 then add to idle set, and delete
            // from epoll if registered (or pending)
            if (mask == 0) {
                if (idleSet.add(fd)) {
                    updateList.add(new Updator(EPOLL_CTL_DEL, fd, 0));
                }
                return;
            }

            // if file descriptor is idle then add to epoll
            if (!idleSet.isEmpty() && idleSet.remove(fd)) {
                updateList.add(new Updator(EPOLL_CTL_ADD, fd, mask));
                return;
            }

            // if the previous pending operation is to add this file descriptor
            // to epoll then update its event set
            if (updateList.size() > 0) {
                Updator last = updateList.getLast();
                if (last.fd == fd && last.opcode == EPOLL_CTL_ADD) {
                    last.events = mask;
                    return;
                }
            }

            // update existing registration
            updateList.add(new Updator(EPOLL_CTL_MOD, fd, mask));
        }
    }

    /**
     * Add a new file descriptor to epoll
     */
    void add(int fd) {
        synchronized (updateList) {
            updateList.add(new Updator(EPOLL_CTL_ADD, fd, 0));
        }
    }

    /**
     * Remove a file descriptor from epoll
     */
    void release(int fd) {
        synchronized (updateList) {
            // if file descriptor is idle then remove from idle set, otherwise
            // delete from epoll
            if (!idleSet.remove(fd)) {
                updateList.add(new Updator(EPOLL_CTL_DEL, fd, 0));
            }
        }
    }

    /**
     * Close epoll file descriptor and free poll array
     */
    void closeEPollFD() throws IOException {
        FileDispatcher.closeIntFD(epfd);
        pollArray.free();
    }

    int poll(long timeout) throws IOException {
        updateRegistrations();
        updated = epollWait(pollArrayAddress, NUM_EPOLLEVENTS, timeout, epfd);
        for (int i=0; i<updated; i++) {
            if (getDescriptor(i) == incomingInterruptFD) {
                interruptedIndex = i;
                interrupted = true;
                break;
            }
        }
        return updated;
    }

    /**
     * Update the pending registrations.
     */
    void updateRegistrations() {
        synchronized (updateList) {
            Updator u = null;
            while ((u = updateList.poll()) != null) {
                epollCtl(epfd, u.opcode, u.fd, u.events);
            }
        }
    }

    // interrupt support
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

    static {
        init();
    }

    private native int epollCreate();
    private native void epollCtl(int epfd, int opcode, int fd, int events);
    private native int epollWait(long pollAddress, int numfds, long timeout,
                                 int epfd) throws IOException;
    private static native int sizeofEPollEvent();
    private static native int offsetofData();
    private static native int fdLimit();
    private static native void interrupt(int fd);
    private static native void init();
}
