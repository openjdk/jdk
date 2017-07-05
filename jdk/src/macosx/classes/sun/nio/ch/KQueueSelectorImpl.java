/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Derived from Sun's DevPollSelectorImpl
 */

package sun.nio.ch;

import java.io.IOException;
import java.io.FileDescriptor;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import sun.misc.*;

class KQueueSelectorImpl
    extends SelectorImpl
{
    // File descriptors used for interrupt
    protected int fd0;
    protected int fd1;

    // The kqueue manipulator
    KQueueArrayWrapper kqueueWrapper;

    // Count of registered descriptors (including interrupt)
    private int totalChannels;

    // Map from file descriptors to selection keys
    private HashMap<Integer,SelectionKeyImpl> fdToKey;

    // True if this Selector has been closed
    private boolean closed = false;

    // Lock for interrupt triggering and clearing
    private Object interruptLock = new Object();
    private boolean interruptTriggered = false;

    /**
     * Package private constructor called by factory method in
     * the abstract superclass Selector.
     */
    KQueueSelectorImpl(SelectorProvider sp) {
        super(sp);
        long fds = IOUtil.makePipe(false);
        fd0 = (int)(fds >>> 32);
        fd1 = (int)fds;
        kqueueWrapper = new KQueueArrayWrapper();
        kqueueWrapper.initInterrupt(fd0, fd1);
        fdToKey = new HashMap<>();
        totalChannels = 1;
    }


    protected int doSelect(long timeout)
        throws IOException
    {
        int entries = 0;
        if (closed)
            throw new ClosedSelectorException();
        processDeregisterQueue();
        if (timeout == 0  &&  totalChannels == 1)
            return 0;
        try {
            begin();
            entries = kqueueWrapper.poll(timeout);
        } finally {
            end();
        }
        processDeregisterQueue();
        return updateSelectedKeys(entries);
    }


    /**
     * Update the keys whose fd's have been selected by the devpoll
     * driver. Add the ready keys to the ready queue.
     * If the interrupt fd has been selected, drain it and clear the interrupt.
     */
    private int updateSelectedKeys(int entries)
        throws IOException
    {
        int numKeysUpdated = 0;
        boolean interrupted = false;

        for (int i = 0; i < entries; i++) {
            int nextFD = kqueueWrapper.getDescriptor(i);
            if (nextFD == fd0) {
                interrupted = true;
            } else {
                SelectionKeyImpl ski = fdToKey.get(new Integer(nextFD));
                // ski is null in the case of an interrupt
                if (ski != null) {
                    int rOps = kqueueWrapper.getReventOps(i);
                    if (selectedKeys.contains(ski)) {
                        if (ski.channel.translateAndSetReadyOps(rOps, ski)) {
                            numKeysUpdated++;
                        }
                    } else {
                        ski.channel.translateAndSetReadyOps(rOps, ski);
                        if ((ski.readyOps() & ski.interestOps()) != 0) {
                            selectedKeys.add(ski);
                            numKeysUpdated++;
                        }
                    }
                }
            }
        }

        if (interrupted) {
            // Clear the wakeup pipe
            synchronized (interruptLock) {
                IOUtil.drain(fd0);
                interruptTriggered = false;
            }
        }

        return numKeysUpdated;
    }


    protected void implClose() throws IOException {
        if (!closed) {
            closed = true;
            FileDispatcherImpl.closeIntFD(fd0);
            FileDispatcherImpl.closeIntFD(fd1);
            if (kqueueWrapper != null) {
                kqueueWrapper.release(fd0);
                kqueueWrapper.close();
                kqueueWrapper = null;
                selectedKeys = null;

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
                totalChannels = 0;
            }
            fd0 = -1;
            fd1 = -1;
        }
    }


    protected void implRegister(SelectionKeyImpl ski) {
        int fd = IOUtil.fdVal(ski.channel.getFD());
        fdToKey.put(new Integer(fd), ski);
        totalChannels++;
        keys.add(ski);
    }


    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        int fd = ski.channel.getFDVal();
        fdToKey.remove(new Integer(fd));
        kqueueWrapper.release(fd);
        totalChannels--;
        keys.remove(ski);
        selectedKeys.remove(ski);
        deregister((AbstractSelectionKey)ski);
        SelectableChannel selch = ski.channel();
        if (!selch.isOpen() && !selch.isRegistered())
            ((SelChImpl)selch).kill();
    }


    public void putEventOps(SelectionKeyImpl ski, int ops) {
        int fd = IOUtil.fdVal(ski.channel.getFD());
        kqueueWrapper.setInterest(fd, ops);
    }


    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                kqueueWrapper.interrupt();
                interruptTriggered = true;
            }
        }
        return this;
    }


    static {
        Util.load();
    }
}

