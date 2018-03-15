/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * KQueueSelectorImpl.java
 * Implementation of Selector using FreeBSD / Mac OS X kqueues
 */

package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.Iterator;

class KQueueSelectorImpl
    extends SelectorImpl
{
    // File descriptors used for interrupt
    private final int fd0;
    private final int fd1;

    // The kqueue manipulator
    private final KQueueArrayWrapper kqueueWrapper;

    // Map from a file descriptor to an entry containing the selection key
    private final HashMap<Integer, MapEntry> fdMap;

    // True if this Selector has been closed
    private boolean closed;

    // Lock for interrupt triggering and clearing
    private final Object interruptLock = new Object();
    private boolean interruptTriggered;

    // used by updateSelectedKeys to handle cases where the same file
    // descriptor is polled by more than one filter
    private long updateCount;

    // Used to map file descriptors to a selection key and "update count"
    // (see updateSelectedKeys for usage).
    private static class MapEntry {
        SelectionKeyImpl ski;
        long updateCount;
        MapEntry(SelectionKeyImpl ski) {
            this.ski = ski;
        }
    }

    /**
     * Package private constructor called by factory method in
     * the abstract superclass Selector.
     */
    KQueueSelectorImpl(SelectorProvider sp) throws IOException {
        super(sp);
        long fds = IOUtil.makePipe(false);
        fd0 = (int)(fds >>> 32);
        fd1 = (int)fds;
        try {
            kqueueWrapper = new KQueueArrayWrapper(fd0, fd1);
            fdMap = new HashMap<>();
        } catch (Throwable t) {
            try {
                FileDispatcherImpl.closeIntFD(fd0);
            } catch (IOException ioe0) {
                t.addSuppressed(ioe0);
            }
            try {
                FileDispatcherImpl.closeIntFD(fd1);
            } catch (IOException ioe1) {
                t.addSuppressed(ioe1);
            }
            throw t;
        }
    }

    private void ensureOpen() {
        if (closed)
            throw new ClosedSelectorException();
    }

    @Override
    protected int doSelect(long timeout)
        throws IOException
    {
        ensureOpen();
        int numEntries;
        processDeregisterQueue();
        try {
            begin();
            numEntries = kqueueWrapper.poll(timeout);
        } finally {
            end();
        }
        processDeregisterQueue();
        return updateSelectedKeys(numEntries);
    }

    /**
     * Update the keys whose fd's have been selected by kqueue.
     * Add the ready keys to the selected key set.
     * If the interrupt fd has been selected, drain it and clear the interrupt.
     */
    private int updateSelectedKeys(int numEntries)
        throws IOException
    {
        int numKeysUpdated = 0;
        boolean interrupted = false;

        // A file descriptor may be registered with kqueue with more than one
        // filter and so there may be more than one event for a fd. The update
        // count in the MapEntry tracks when the fd was last updated and this
        // ensures that the ready ops are updated rather than replaced by a
        // second or subsequent event.
        updateCount++;

        for (int i = 0; i < numEntries; i++) {
            int nextFD = kqueueWrapper.getDescriptor(i);
            if (nextFD == fd0) {
                interrupted = true;
            } else {
                MapEntry me = fdMap.get(Integer.valueOf(nextFD));
                if (me != null) {
                    int rOps = kqueueWrapper.getReventOps(i);
                    SelectionKeyImpl ski = me.ski;
                    if (selectedKeys.contains(ski)) {
                        // first time this file descriptor has been encountered on this
                        // update?
                        if (me.updateCount != updateCount) {
                            if (ski.channel.translateAndSetReadyOps(rOps, ski)) {
                                numKeysUpdated++;
                                me.updateCount = updateCount;
                            }
                        } else {
                            // ready ops have already been set on this update
                            ski.channel.translateAndUpdateReadyOps(rOps, ski);
                        }
                    } else {
                        ski.channel.translateAndSetReadyOps(rOps, ski);
                        if ((ski.nioReadyOps() & ski.nioInterestOps()) != 0) {
                            selectedKeys.add(ski);
                            numKeysUpdated++;
                            me.updateCount = updateCount;
                        }
                    }
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
        if (!closed) {
            closed = true;

            // prevent further wakeup
            synchronized (interruptLock) {
                interruptTriggered = true;
            }

            kqueueWrapper.close();
            FileDispatcherImpl.closeIntFD(fd0);
            FileDispatcherImpl.closeIntFD(fd1);

            // Deregister channels
            Iterator<SelectionKey> i = keys.iterator();
            while (i.hasNext()) {
                SelectionKeyImpl ski = (SelectionKeyImpl)i.next();
                deregister(ski);
                SelectableChannel selch = ski.channel();
                if (!selch.isOpen() && !selch.isRegistered())
                    ((SelChImpl)selch).kill();
                i.remove();
            }
        }
    }

    @Override
    protected void implRegister(SelectionKeyImpl ski) {
        ensureOpen();
        int fd = IOUtil.fdVal(ski.channel.getFD());
        fdMap.put(Integer.valueOf(fd), new MapEntry(ski));
        keys.add(ski);
    }

    @Override
    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        int fd = ski.channel.getFDVal();
        fdMap.remove(Integer.valueOf(fd));
        kqueueWrapper.release(ski.channel);
        keys.remove(ski);
        selectedKeys.remove(ski);
        deregister(ski);
        SelectableChannel selch = ski.channel();
        if (!selch.isOpen() && !selch.isRegistered())
            ((SelChImpl)selch).kill();
    }

    @Override
    public void putEventOps(SelectionKeyImpl ski, int ops) {
        ensureOpen();
        kqueueWrapper.setInterest(ski.channel, ops);
    }

    @Override
    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                kqueueWrapper.interrupt();
                interruptTriggered = true;
            }
        }
        return this;
    }

    private void clearInterrupt() throws IOException {
        synchronized (interruptLock) {
            IOUtil.drain(fd0);
            interruptTriggered = false;
        }
    }
}
