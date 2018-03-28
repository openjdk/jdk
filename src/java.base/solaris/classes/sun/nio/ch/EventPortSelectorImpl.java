/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jdk.internal.misc.Unsafe;

import static sun.nio.ch.SolarisEventPort.PORT_SOURCE_FD;
import static sun.nio.ch.SolarisEventPort.PORT_SOURCE_USER;
import static sun.nio.ch.SolarisEventPort.SIZEOF_PORT_EVENT;
import static sun.nio.ch.SolarisEventPort.OFFSETOF_EVENTS;
import static sun.nio.ch.SolarisEventPort.OFFSETOF_SOURCE;
import static sun.nio.ch.SolarisEventPort.OFFSETOF_OBJECT;
import static sun.nio.ch.SolarisEventPort.port_create;
import static sun.nio.ch.SolarisEventPort.port_close;
import static sun.nio.ch.SolarisEventPort.port_associate;
import static sun.nio.ch.SolarisEventPort.port_dissociate;
import static sun.nio.ch.SolarisEventPort.port_getn;
import static sun.nio.ch.SolarisEventPort.port_send;

/**
 * Selector implementation based on the Solaris event port mechanism.
 */

class EventPortSelectorImpl
    extends SelectorImpl
{
    // maximum number of events to retrive in one call to port_getn
    static final int MAX_EVENTS = Math.min(IOUtil.fdLimit()-1, 1024);

    // port file descriptor
    private final int pfd;

    // the poll array (populated by port_getn)
    private final long pollArrayAddress;
    private final AllocatedNativeObject pollArray;

    // a registration of a file descriptor with a selector
    private static class RegEntry {
        final SelectionKeyImpl ski;
        int registeredOps;
        int lastUpdate;
        RegEntry(SelectionKeyImpl ski) {
            this.ski = ski;
        }
    }

    // maps a file descriptor to registration entry, synchronize on selector
    private final Map<Integer, RegEntry> fdToRegEntry = new HashMap<>();

    // the last update operation, incremented by processUpdateQueue
    private int lastUpdate;

    // pending new registrations/updates, queued by implRegister, putEventOps,
    // and updateSelectedKeys
    private final Object updateLock = new Object();
    private final Deque<SelectionKeyImpl> newKeys = new ArrayDeque<>();
    private final Deque<SelectionKeyImpl> updateKeys = new ArrayDeque<>();
    private final Deque<Integer> updateOps = new ArrayDeque<>();

    // interrupt triggering and clearing
    private final Object interruptLock = new Object();
    private boolean interruptTriggered;

    EventPortSelectorImpl(SelectorProvider sp) throws IOException {
        super(sp);

        this.pfd = port_create();

        int allocationSize = MAX_EVENTS * SIZEOF_PORT_EVENT;
        this.pollArray = new AllocatedNativeObject(allocationSize, false);
        this.pollArrayAddress = pollArray.address();
    }

    private void ensureOpen() {
        if (!isOpen())
            throw new ClosedSelectorException();
    }

    @Override
    protected int doSelect(long timeout) throws IOException {
        assert Thread.holdsLock(this);

        int numEvents;
        processUpdateQueue();
        processDeregisterQueue();
        try {
            begin();

            long to = timeout;
            boolean timedPoll = (to > 0);
            do {
                long startTime = timedPoll ? System.nanoTime() : 0;
                numEvents = port_getn(pfd, pollArrayAddress, MAX_EVENTS, to);
                if (numEvents == IOStatus.INTERRUPTED && timedPoll) {
                    // timed poll interrupted so need to adjust timeout
                    long adjust = System.nanoTime() - startTime;
                    to -= TimeUnit.MILLISECONDS.convert(adjust, TimeUnit.NANOSECONDS);
                    if (to <= 0) {
                        // timeout also expired so no retry
                        numEvents = 0;
                    }
                }
            } while (numEvents == IOStatus.INTERRUPTED);
            assert IOStatus.check(numEvents);

        } finally {
            end();
        }
        processDeregisterQueue();
        return processPortEvents(numEvents);
    }

    /**
     * Process new registrations and changes to the interest ops.
     */
    private void processUpdateQueue() throws IOException {
        assert Thread.holdsLock(this);

        // bump lastUpdate to ensure that the interest ops are changed at most
        // once per bulk update
        lastUpdate++;

        synchronized (updateLock) {
            SelectionKeyImpl ski;

            // new registrations
            while ((ski = newKeys.pollFirst()) != null) {
                if (ski.isValid()) {
                    SelChImpl ch = ski.channel;
                    int fd = ch.getFDVal();
                    RegEntry previous = fdToRegEntry.put(fd, new RegEntry(ski));
                    assert previous == null;
                }
            }

            // changes to interest ops
            assert updateKeys.size() == updateOps.size();
            while ((ski = updateKeys.pollFirst()) != null) {
                int ops = updateOps.pollFirst();
                int fd = ski.channel.getFDVal();
                RegEntry e = fdToRegEntry.get(fd);
                if (ski.isValid() && (e != null) && (e.lastUpdate != lastUpdate)) {
                    assert e.ski == ski;
                    if ((ops != e.registeredOps)) {
                        if (ops == 0) {
                            port_dissociate(pfd, PORT_SOURCE_FD, fd);
                        } else {
                            port_associate(pfd, PORT_SOURCE_FD, fd, ops);
                        }
                        e.registeredOps = ops;
                    }
                    e.lastUpdate = lastUpdate;
                }
            }
        }
    }

    /**
     * Process the port events. This method updates the keys of file descriptors
     * that were polled. It also re-queues the key so that the file descriptor
     * is re-associated at the next select operation.
     *
     * @return the number of selection keys updated.
     */
    private int processPortEvents(int numEvents) throws IOException {
        assert Thread.holdsLock(this);
        assert Thread.holdsLock(nioSelectedKeys());

        int numKeysUpdated = 0;
        boolean interrupted = false;

        synchronized (updateLock) {
            for (int i = 0; i < numEvents; i++) {
                short source = getSource(i);
                if (source == PORT_SOURCE_FD) {
                    int fd = getDescriptor(i);
                    RegEntry e = fdToRegEntry.get(fd);
                    if (e != null) {
                        SelectionKeyImpl ski = e.ski;
                        int rOps = getEventOps(i);
                        if (selectedKeys.contains(ski)) {
                            if (ski.channel.translateAndSetReadyOps(rOps, ski)) {
                                numKeysUpdated++;
                            }
                        } else {
                            ski.channel.translateAndSetReadyOps(rOps, ski);
                            if ((ski.nioReadyOps() & ski.nioInterestOps()) != 0) {
                                selectedKeys.add(ski);
                                numKeysUpdated++;
                            }
                        }

                        // queue selection key so that it is re-associated at
                        // next select. Push to end of deque so that changes to
                        // the interest ops are processed first
                        updateKeys.addLast(ski);
                        updateOps.addLast(e.registeredOps);
                        e.registeredOps = 0;
                    }
                } else if (source == PORT_SOURCE_USER) {
                    interrupted = true;
                } else {
                    assert false;
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
        assert !isOpen();
        assert Thread.holdsLock(this);
        assert Thread.holdsLock(nioKeys());

        // prevent further wakeup
        synchronized (interruptLock) {
            interruptTriggered = true;
        }

        port_close(pfd);
        pollArray.free();

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

    @Override
    protected void implRegister(SelectionKeyImpl ski) {
        assert Thread.holdsLock(nioKeys());
        ensureOpen();
        synchronized (updateLock) {
            newKeys.addLast(ski);
        }
        keys.add(ski);
    }

    @Override
    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        assert !ski.isValid();
        assert Thread.holdsLock(this);
        assert Thread.holdsLock(nioKeys());
        assert Thread.holdsLock(nioSelectedKeys());

        int fd = ski.channel.getFDVal();
        RegEntry e = fdToRegEntry.remove(fd);
        if (e != null && e.registeredOps != 0) {
            port_dissociate(pfd, PORT_SOURCE_FD, fd);
        }

        selectedKeys.remove(ski);
        keys.remove(ski);

        // remove from channel's key set
        deregister(ski);

        SelectableChannel selch = ski.channel();
        if (!selch.isOpen() && !selch.isRegistered())
            ((SelChImpl) selch).kill();
    }

    @Override
    public void putEventOps(SelectionKeyImpl ski, int ops) {
        ensureOpen();
        synchronized (updateLock) {
            // push to front of deque so that it processed before other
            // updates for the same key.
            updateOps.addFirst(ops);
            updateKeys.addFirst(ski);
        }
    }

    @Override
    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                try {
                    port_send(pfd, 0);
                } catch (IOException ioe) {
                    throw new InternalError(ioe);
                }
                interruptTriggered = true;
            }
        }
        return this;
    }

    private void clearInterrupt() throws IOException {
        synchronized (interruptLock) {
            interruptTriggered = false;
        }
    }

    private short getSource(int i) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_SOURCE;
        return pollArray.getShort(offset);
    }

    private int getEventOps(int i) {
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_EVENTS;
        return pollArray.getInt(offset);
    }

    private int getDescriptor(int i) {
        //assert Unsafe.getUnsafe().addressSize() == 8;
        int offset = SIZEOF_PORT_EVENT * i + OFFSETOF_OBJECT;
        return (int) pollArray.getLong(offset);
    }
}
