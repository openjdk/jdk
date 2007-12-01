/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import sun.misc.*;


/**
 * An implementation of Selector for Solaris.
 */
class DevPollSelectorImpl
    extends SelectorImpl
{

    // File descriptors used for interrupt
    protected int fd0;
    protected int fd1;

    // The poll object
    DevPollArrayWrapper pollWrapper;

    // The number of valid channels in this Selector's poll array
    private int totalChannels;

    // Maps from file descriptors to keys
    private HashMap fdToKey;

    // True if this Selector has been closed
    private boolean closed = false;

    // Lock for interrupt triggering and clearing
    private Object interruptLock = new Object();
    private boolean interruptTriggered = false;

    /**
     * Package private constructor called by factory method in
     * the abstract superclass Selector.
     */
    DevPollSelectorImpl(SelectorProvider sp) {
        super(sp);
        int[] fdes = new int[2];
        IOUtil.initPipe(fdes, false);
        fd0 = fdes[0];
        fd1 = fdes[1];
        pollWrapper = new DevPollArrayWrapper();
        pollWrapper.initInterrupt(fd0, fd1);
        fdToKey = new HashMap();
        totalChannels = 1;
    }

    protected int doSelect(long timeout)
        throws IOException
    {
        if (closed)
            throw new ClosedSelectorException();
        processDeregisterQueue();
        try {
            begin();
            pollWrapper.poll(timeout);
        } finally {
            end();
        }
        processDeregisterQueue();
        int numKeysUpdated = updateSelectedKeys();
        if (pollWrapper.interrupted()) {
            // Clear the wakeup pipe
            pollWrapper.putReventOps(pollWrapper.interruptedIndex(), 0);
            synchronized (interruptLock) {
                pollWrapper.clearInterrupted();
                IOUtil.drain(fd0);
                interruptTriggered = false;
            }
        }
        return numKeysUpdated;
    }

    /**
     * Update the keys whose fd's have been selected by the devpoll
     * driver. Add the ready keys to the ready queue.
     */
    private int updateSelectedKeys() {
        int entries = pollWrapper.updated;
        int numKeysUpdated = 0;
        for (int i=0; i<entries; i++) {
            int nextFD = pollWrapper.getDescriptor(i);
            SelectionKeyImpl ski = (SelectionKeyImpl) fdToKey.get(
                new Integer(nextFD));
            // ski is null in the case of an interrupt
            if (ski != null) {
                int rOps = pollWrapper.getReventOps(i);
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
            }
        }
        return numKeysUpdated;
    }

    protected void implClose() throws IOException {
        if (!closed) {
            closed = true;

            // prevent further wakeup
            synchronized (interruptLock) {
                interruptTriggered = true;
            }

            FileDispatcher.closeIntFD(fd0);
            FileDispatcher.closeIntFD(fd1);
            if (pollWrapper != null) {

                pollWrapper.release(fd0);
                pollWrapper.closeDevPollFD();
                pollWrapper = null;
                selectedKeys = null;

                // Deregister channels
                Iterator i = keys.iterator();
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
        int i = ski.getIndex();
        assert (i >= 0);
        int fd = ski.channel.getFDVal();
        fdToKey.remove(new Integer(fd));
        pollWrapper.release(fd);
        totalChannels--;
        ski.setIndex(-1);
        keys.remove(ski);
        selectedKeys.remove(ski);
        deregister((AbstractSelectionKey)ski);
        SelectableChannel selch = ski.channel();
        if (!selch.isOpen() && !selch.isRegistered())
            ((SelChImpl)selch).kill();
    }

    void putEventOps(SelectionKeyImpl sk, int ops) {
        int fd = IOUtil.fdVal(sk.channel.getFD());
        pollWrapper.setInterest(fd, ops);
    }

    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                pollWrapper.interrupt();
                interruptTriggered = true;
            }
        }
        return this;
    }

    static {
        Util.load();
    }

}
