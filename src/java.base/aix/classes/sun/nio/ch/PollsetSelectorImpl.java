/*
 * Copyright (c) 2026 IBM Corp. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import java.util.function.Consumer;
import jdk.internal.misc.Blocker;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.TimeUnit;
import java.nio.channels.*;
import jdk.internal.misc.*;

/**
 * An implementation of Selector for AIX 5.3+ kernels that uses
 * the pollset event notification facility.
 */
class PollsetSelectorImpl
        extends SelectorImpl
{
    static {
        IOUtil.load();
        Pollset.init();
    }

    // maximum number of events to poll in one call to pollset
    private static final int NUM_POLLCTLEVENTS = getFDLimit();

    // file descriptors used for interrupt
    private final int fd0;
    private final int fd1;

    // pollset file descriptor
    private final int pfd;

    // address of poll array (event list) when polling for pending events
    private final long pollArrayAddress;

    // maps file descriptor to selection key, synchronize on selector
    private final Map<Integer, SelectionKeyImpl> fdToKey = new HashMap<>();

    // pending new registrations/updates, queued by implRegister and putEventOps
    private final Object updateLock = new Object();
    private final Deque<SelectionKeyImpl> updateKeys = new ArrayDeque<>();

    // Lock for interrupt triggering and clearing
    private final Object interruptLock = new Object();

    private boolean interruptTriggered;

    private int pollsetNumEvents = 0;

    // The pollfd array for results from pollset_poll
    private AllocatedNativeObject pollArray;

    // The pollset_ctl current array for adding the events into pollset
    private AllocatedNativeNode pollCtlArrayCurrent;

    // Points to the HEAD of the list
    private AllocatedNativeNode pollCtlArrayHead;

    private int pollsetUpdatorCount = 0;

    PollsetSelectorImpl(SelectorProvider sp) throws IOException {
        super(sp);

        this.pfd = Pollset.pollsetCreate();
        this.pollArrayAddress = Pollset.allocatePollArray(NUM_POLLCTLEVENTS);

        try {
            long pipeFds = IOUtil.makePipe(false);
            this.fd0 = (int) (pipeFds >>> 32);
            this.fd1 = (int) pipeFds;
        } catch (IOException ioe) {
            Pollset.freePollArray(pollArrayAddress);
            Pollset.pollsetDestroy(pfd);
            throw ioe;
        }
        Pollset.pollsetCtl(pfd, Pollset.PS_ADD, fd0, Net.POLLIN);

    }

    /*
     * on 64 bit machine it returns -1(when it exceeds int limit).
     * In this case the fd limit is set to default.
     */
    private static int getFDLimit() {
        int limit = Pollset.fdLimit();
        if (limit <=0) {
            return 8192;
        }
        return Math.min(limit, 8192);
    }

    /*
     * Selects a set of keys whose corresponding channels are ready for I/O
     * operations.
     */
    @Override
    protected int doSelect(Consumer<SelectionKey> action, long timeout)
            throws IOException
    {
        assert Thread.holdsLock(this);

        // pollset_poll timeout is int
        int to = (int) Math.min(timeout, Integer.MAX_VALUE);
        boolean blocking = (to != 0);

        int numEntries;
        processUpdateQueue();
        processDeregisterQueue();
        flushBulkPollCtlEvents();
        try {
            begin(blocking);
            boolean attempted = Blocker.begin(blocking);
            try {
                numEntries = Pollset.pollsetPoll(pfd, pollArrayAddress, NUM_POLLCTLEVENTS, to);
                } finally {
                Blocker.end(attempted);
            }
        } finally {
            end(blocking);
        }

        processDeregisterQueue();
        return processEvents(numEntries, action);
    }

    /**
    * Method to flush all buffered events to OS
    */
    private void flushBulkPollCtlEvents() {
        synchronized (updateLock) {
            // return if no events to flush
            if (pollsetNumEvents <= 0) {
                return;
            }

            int eventsToBeFlushed = pollsetNumEvents;
            int remainingEvents = pollsetNumEvents;
            AllocatedNativeNode pollCtlArrayToBeFlushed = pollCtlArrayHead;
            while ( remainingEvents > 0 ) {
                eventsToBeFlushed = (remainingEvents >= NUM_POLLCTLEVENTS) ? NUM_POLLCTLEVENTS : remainingEvents;
                remainingEvents -= eventsToBeFlushed;
                Pollset.pollsetBulkCtl(pfd, (pollCtlArrayToBeFlushed.address()), eventsToBeFlushed);
                pollCtlArrayToBeFlushed = pollCtlArrayToBeFlushed.getNext();
            }
            // Reset all the poll set variables
            pollsetNumEvents = pollsetUpdatorCount = 0;
            pollCtlArrayCurrent = pollCtlArrayHead;

        }
    }

    /**
     * Process changes to the interest ops.
     */
    private void processUpdateQueue() {
        assert Thread.holdsLock(this);

        synchronized (updateLock) {
            SelectionKeyImpl ski;

            while ((ski = updateKeys.pollFirst()) != null) {
                if (ski.isValid()) {
                    int fd = ski.getFDVal();
                    if (!ski.isValid()) {
                        Pollset.pollsetCtl(pfd, Pollset.PS_DELETE, fd, 0);
                        fdToKey.remove(fd);
                        ski.registeredEvents(0);
                        continue;
                    }
                    SelectionKeyImpl previous = fdToKey.putIfAbsent(fd, ski);
                    assert (previous == null) || (previous == ski);
                    int newEvents = ski.translateInterestOps();
                    int registeredEvents = ski.registeredEvents();
                    if (newEvents != registeredEvents) {
                        if (newEvents == 0) {
                            // remove from epoll
                            Pollset.pollsetCtl(pfd, Pollset.PS_DELETE, fd, 0);
                        } else {
                            if (registeredEvents == 0) {
                                // add events
                                Pollset.pollsetCtl(pfd, Pollset.PS_ADD, fd, newEvents);
                            } else {
                                // modify events
                                Pollset.pollsetCtl(pfd, Pollset.PS_DELETE, fd, 0);
                                Pollset.pollsetCtl(pfd, Pollset.PS_ADD, fd, newEvents);
                            }
                        }
                    ski.registeredEvents(newEvents);
                    }
                }
            }
        }
    }

    /**
     * Process the polled events.
     * Add the ready keys to the ready queue.
     */
    private int processEvents(int entries, Consumer<SelectionKey> action) throws IOException{
        assert Thread.holdsLock(this);
        int numKeysUpdated = 0;
        boolean interrupted = false;
        for (int i=0; i<entries; i++) {
            long event = Pollset.getEvent(pollArrayAddress, i);
            int fd = Pollset.getDescriptor(event);
            if (fd == fd0) {
                interrupted = true;
            } else {
                SelectionKeyImpl ski = fdToKey.get(fd);
                // ski is null in the case of an interrupt
                if (ski != null) {
                    int rOps = Pollset.getEvents(event);
                    numKeysUpdated += processReadyEvents(rOps, ski, action);
                }
            }
        }
        if (interrupted) {
            clearInterrupt();
        }
        return numKeysUpdated;
    }

    @Override
    protected void implClose() throws IOException {
        assert Thread.holdsLock(this);

        // prevent further wakeup
        synchronized (interruptLock) {
            interruptTriggered = true;
        }
        Pollset.freePollArray(pollArrayAddress);

        Pollset.close0(fd0);
        Pollset.close0(fd1);

        Pollset.pollsetDestroy(pfd);
    }

    @Override
    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        assert !ski.isValid();
        assert Thread.holdsLock(this);

        synchronized (updateLock) {
            updateKeys.remove(ski);
        }
        int fd = ski.getFDVal();

        if (fdToKey.remove(fd) != null) {
            if (ski.registeredEvents() != 0) {
                Pollset.pollsetCtl(pfd, Pollset.PS_DELETE, fd, 0);
                ski.registeredEvents(0);
            }
        } else {
            assert ski.registeredEvents() == 0;
        }
    }

    @Override
    public void setEventOps(SelectionKeyImpl ski) {
        synchronized (updateLock) {
            updateKeys.addLast(ski);
        }
    }

    /*
     * Causes the Earlier selection operation that has not yet returned to return
     * immediately
     */
    @Override
    public Selector wakeup() {
        synchronized (interruptLock) {
            // If close has started, do nothing
            if (interruptTriggered)
                return this;

            try {
                IOUtil.write1(fd1, (byte)0);
            } catch (IOException ioe) {
                throw new InternalError(ioe);
            }

            interruptTriggered = true;
        }
        return this;
    }

    private void clearInterrupt() throws IOException {
        synchronized (interruptLock) {
            IOUtil.drain(fd0);
            interruptTriggered = false;
        }
    }

    private static class AllocatedNativeNode extends AllocatedNativeObject {

        private AllocatedNativeNode nextNode = null;

        AllocatedNativeNode(int size, boolean pageAligned) {
            super(size, pageAligned);
        }

        void setNext(AllocatedNativeNode next) {
            this.nextNode = next;
        }

        AllocatedNativeNode getNext() {
            return this.nextNode;
        }

        static void free(AllocatedNativeNode node) {
            AllocatedNativeNode toBeFreed = node;
            while (toBeFreed != null) {
                AllocatedNativeNode next = toBeFreed.getNext();
                toBeFreed.free();
                toBeFreed = next;
            }
        }
    }
}
