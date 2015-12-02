/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.transport;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.EventHandler;
import com.sun.corba.se.pept.transport.ListenerThread;
import com.sun.corba.se.pept.transport.ReaderThread;

import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchThreadPoolException;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBUtility;

/**
 * @author Harold Carr
 */
class SelectorImpl
    extends
        sun.misc.ManagedLocalsThread
    implements
        com.sun.corba.se.pept.transport.Selector
{
    private ORB orb;
    private Selector selector;
    private long timeout;
    private List deferredRegistrations;
    private List interestOpsList;
    private HashMap listenerThreads;
    private Map readerThreads;
    private boolean selectorStarted;
    private volatile boolean closed;
    private ORBUtilSystemException wrapper;


    public SelectorImpl(ORB orb)
    {
        this.orb = orb;
        selector = null;
        selectorStarted = false;
        timeout = 60000;
        deferredRegistrations = new ArrayList();
        interestOpsList = new ArrayList();
        listenerThreads = new HashMap();
        readerThreads = java.util.Collections.synchronizedMap(new HashMap());
        closed = false;
        wrapper = ORBUtilSystemException.get(orb,CORBALogDomains.RPC_TRANSPORT);
    }

    public void setTimeout(long timeout)
    {
        this.timeout = timeout;
    }

    public long getTimeout()
    {
        return timeout;
    }

    public void registerInterestOps(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".registerInterestOps:-> " + eventHandler);
        }

        SelectionKey selectionKey = eventHandler.getSelectionKey();
        if (selectionKey.isValid()) {
            int ehOps = eventHandler.getInterestOps();
            SelectionKeyAndOp keyAndOp = new SelectionKeyAndOp(selectionKey, ehOps);
            synchronized(interestOpsList) {
                interestOpsList.add(keyAndOp);
            }
            // tell Selector Thread there's an update to a SelectorKey's Ops
            selector.wakeup();
        }
        else {
            wrapper.selectionKeyInvalid(eventHandler.toString());
            if (orb.transportDebugFlag) {
                dprint(".registerInterestOps: EventHandler SelectionKey not valid " + eventHandler);
            }
        }

        if (orb.transportDebugFlag) {
            dprint(".registerInterestOps:<- ");
        }
    }

    public void registerForEvent(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".registerForEvent: " + eventHandler);
        }

        if (isClosed()) {
            if (orb.transportDebugFlag) {
                dprint(".registerForEvent: closed: " + eventHandler);
            }
            return;
        }

        if (eventHandler.shouldUseSelectThreadToWait()) {
            synchronized (deferredRegistrations) {
                deferredRegistrations.add(eventHandler);
            }
            if (! selectorStarted) {
                startSelector();
            }
            selector.wakeup();
            return;
        }

        switch (eventHandler.getInterestOps()) {
        case SelectionKey.OP_ACCEPT :
            createListenerThread(eventHandler);
            break;
        case SelectionKey.OP_READ :
            createReaderThread(eventHandler);
            break;
        default:
            if (orb.transportDebugFlag) {
                dprint(".registerForEvent: default: " + eventHandler);
            }
            throw new RuntimeException(
                "SelectorImpl.registerForEvent: unknown interest ops");
        }
    }

    public void unregisterForEvent(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".unregisterForEvent: " + eventHandler);
        }

        if (isClosed()) {
            if (orb.transportDebugFlag) {
                dprint(".unregisterForEvent: closed: " + eventHandler);
            }
            return;
        }

        if (eventHandler.shouldUseSelectThreadToWait()) {
            SelectionKey selectionKey ;
            synchronized(deferredRegistrations) {
                selectionKey = eventHandler.getSelectionKey();
            }
            if (selectionKey != null) {
                selectionKey.cancel();
            }
            selector.wakeup();
            return;
        }

        switch (eventHandler.getInterestOps()) {
        case SelectionKey.OP_ACCEPT :
            destroyListenerThread(eventHandler);
            break;
        case SelectionKey.OP_READ :
            destroyReaderThread(eventHandler);
            break;
        default:
            if (orb.transportDebugFlag) {
                dprint(".unregisterForEvent: default: " + eventHandler);
            }
            throw new RuntimeException(
                "SelectorImpl.uregisterForEvent: unknown interest ops");
        }
    }

    public void close()
    {
        if (orb.transportDebugFlag) {
            dprint(".close");
        }

        if (isClosed()) {
            if (orb.transportDebugFlag) {
                dprint(".close: already closed");
            }
            return;
        }

        setClosed(true);

        Iterator i;

        // Kill listeners.

        i = listenerThreads.values().iterator();
        while (i.hasNext()) {
            ListenerThread listenerThread = (ListenerThread) i.next();
            listenerThread.close();
        }

        // Kill readers.

        i = readerThreads.values().iterator();
        while (i.hasNext()) {
            ReaderThread readerThread = (ReaderThread) i.next();
            readerThread.close();
        }

        // Selector

        try {
            if (selector != null) {
                // wakeup Selector thread to process close request
                selector.wakeup();
            }
        } catch (Throwable t) {
            if (orb.transportDebugFlag) {
                dprint(".close: selector.close: " + t);
            }
        }
    }

    ///////////////////////////////////////////////////
    //
    // Thread methods.
    //

    public void run()
    {
        setName("SelectorThread");
        while (!closed) {
            try {
                int n = 0;
                if (timeout == 0 && orb.transportDebugFlag) {
                    dprint(".run: Beginning of selection cycle");
                }
                handleDeferredRegistrations();
                enableInterestOps();
                try {
                    n = selector.select(timeout);
                } catch (IOException  e) {
                    if (orb.transportDebugFlag) {
                        dprint(".run: selector.select: " + e);
                    }
                }
                if (closed) {
                    selector.close();
                    if (orb.transportDebugFlag) {
                        dprint(".run: closed - .run return");
                    }
                    return;
                }
                /*
                  if (timeout == 0 && orb.transportDebugFlag) {
                  dprint(".run: selector.select() returned: " + n);
                  }
                  if (n == 0) {
                  continue;
                  }
                */
                Iterator iterator = selector.selectedKeys().iterator();
                if (orb.transportDebugFlag) {
                    if (iterator.hasNext()) {
                        dprint(".run: n = " + n);
                    }
                }
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();
                    iterator.remove();
                    EventHandler eventHandler = (EventHandler)
                        selectionKey.attachment();
                    try {
                        eventHandler.handleEvent();
                    } catch (Throwable t) {
                        if (orb.transportDebugFlag) {
                            dprint(".run: eventHandler.handleEvent", t);
                        }
                    }
                }
                if (timeout == 0 && orb.transportDebugFlag) {
                    dprint(".run: End of selection cycle");
                }
            } catch (Throwable t) {
                // IMPORTANT: ignore all errors so the select thread keeps running.
                // Otherwise a guaranteed hang.
                if (orb.transportDebugFlag) {
                    dprint(".run: ignoring", t);
                }
            }
        }
    }

    /////////////////////////////////////////////////////
    //
    // Implementation.
    //

    private synchronized boolean isClosed ()
    {
        return closed;
    }

    private synchronized void setClosed(boolean closed)
    {
        this.closed = closed;
    }

    private void startSelector()
    {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            if (orb.transportDebugFlag) {
                dprint(".startSelector: Selector.open: IOException: " + e);
            }
            // REVISIT - better handling/reporting
            RuntimeException rte =
                new RuntimeException(".startSelector: Selector.open exception");
            rte.initCause(e);
            throw rte;
        }
        setDaemon(true);
        start();
        selectorStarted = true;
        if (orb.transportDebugFlag) {
            dprint(".startSelector: selector.start completed.");
        }
    }

    private void handleDeferredRegistrations()
    {
        synchronized (deferredRegistrations) {
            int deferredListSize = deferredRegistrations.size();
            for (int i = 0; i < deferredListSize; i++) {
                EventHandler eventHandler =
                    (EventHandler)deferredRegistrations.get(i);
                if (orb.transportDebugFlag) {
                    dprint(".handleDeferredRegistrations: " + eventHandler);
                }
                SelectableChannel channel = eventHandler.getChannel();
                SelectionKey selectionKey = null;
                try {
                    selectionKey =
                        channel.register(selector,
                                         eventHandler.getInterestOps(),
                                         (Object)eventHandler);
                } catch (ClosedChannelException e) {
                    if (orb.transportDebugFlag) {
                        dprint(".handleDeferredRegistrations: " + e);
                    }
                }
                eventHandler.setSelectionKey(selectionKey);
            }
            deferredRegistrations.clear();
        }
    }

    private void enableInterestOps()
    {
        synchronized (interestOpsList) {
            int listSize = interestOpsList.size();
            if (listSize > 0) {
                if (orb.transportDebugFlag) {
                    dprint(".enableInterestOps:->");
                }
                SelectionKey selectionKey = null;
                SelectionKeyAndOp keyAndOp = null;
                int keyOp, selectionKeyOps = 0;
                for (int i = 0; i < listSize; i++) {
                    keyAndOp = (SelectionKeyAndOp)interestOpsList.get(i);
                    selectionKey = keyAndOp.selectionKey;

                    // Need to check if the SelectionKey is valid because a
                    // connection's SelectionKey could be put on the list to
                    // have its OP enabled and before it's enabled be reclaimed.
                    // Otherwise, the enabling of the OP will throw an exception
                    // here and exit this method an potentially not enable all
                    // registered ops.
                    //
                    // So, we ignore SelectionKeys that are invalid. They will get
                    // cleaned up on the next Selector.select() call.

                    if (selectionKey.isValid()) {
                        if (orb.transportDebugFlag) {
                            dprint(".enableInterestOps: " + keyAndOp);
                        }
                        keyOp = keyAndOp.keyOp;
                        selectionKeyOps = selectionKey.interestOps();
                        selectionKey.interestOps(selectionKeyOps | keyOp);
                    }
                }
                interestOpsList.clear();
                if (orb.transportDebugFlag) {
                    dprint(".enableInterestOps:<-");
                }
            }
        }
    }

    private void createListenerThread(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".createListenerThread: " + eventHandler);
        }
        Acceptor acceptor = eventHandler.getAcceptor();
        ListenerThread listenerThread =
            new ListenerThreadImpl(orb, acceptor, this);
        listenerThreads.put(eventHandler, listenerThread);
        Throwable throwable = null;
        try {
            orb.getThreadPoolManager().getThreadPool(0)
                .getWorkQueue(0).addWork((Work)listenerThread);
        } catch (NoSuchThreadPoolException e) {
            throwable = e;
        } catch (NoSuchWorkQueueException e) {
            throwable = e;
        }
        if (throwable != null) {
            RuntimeException rte = new RuntimeException(throwable.toString());
            rte.initCause(throwable);
            throw rte;
        }
    }

    private void destroyListenerThread(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".destroyListenerThread: " + eventHandler);
        }
        ListenerThread listenerThread = (ListenerThread)
            listenerThreads.get(eventHandler);
        if (listenerThread == null) {
            if (orb.transportDebugFlag) {
                dprint(".destroyListenerThread: cannot find ListenerThread - ignoring.");
            }
            return;
        }
        listenerThreads.remove(eventHandler);
        listenerThread.close();
    }

    private void createReaderThread(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".createReaderThread: " + eventHandler);
        }
        Connection connection = eventHandler.getConnection();
        ReaderThread readerThread =
            new ReaderThreadImpl(orb, connection, this);
        readerThreads.put(eventHandler, readerThread);
        Throwable throwable = null;
        try {
            orb.getThreadPoolManager().getThreadPool(0)
                .getWorkQueue(0).addWork((Work)readerThread);
        } catch (NoSuchThreadPoolException e) {
            throwable = e;
        } catch (NoSuchWorkQueueException e) {
            throwable = e;
        }
        if (throwable != null) {
            RuntimeException rte = new RuntimeException(throwable.toString());
            rte.initCause(throwable);
            throw rte;
        }
    }

    private void destroyReaderThread(EventHandler eventHandler)
    {
        if (orb.transportDebugFlag) {
            dprint(".destroyReaderThread: " + eventHandler);
        }
        ReaderThread readerThread = (ReaderThread)
            readerThreads.get(eventHandler);
        if (readerThread == null) {
            if (orb.transportDebugFlag) {
                dprint(".destroyReaderThread: cannot find ReaderThread - ignoring.");
            }
            return;
        }
        readerThreads.remove(eventHandler);
        readerThread.close();
    }

    private void dprint(String msg)
    {
        ORBUtility.dprint("SelectorImpl", msg);
    }

    protected void dprint(String msg, Throwable t)
    {
        dprint(msg);
        t.printStackTrace(System.out);
    }

    // Private class to contain a SelectionKey and a SelectionKey op.
    // Used only by SelectorImpl to register and enable SelectionKey
    // Op.
    // REVISIT - Could do away with this class and use the EventHanlder
    //           directly.
    private class SelectionKeyAndOp
    {
        // A SelectionKey.[OP_READ|OP_WRITE|OP_ACCEPT|OP_CONNECT]
        public int keyOp;
        public SelectionKey selectionKey;

        // constructor
        public SelectionKeyAndOp(SelectionKey selectionKey, int keyOp) {
            this.selectionKey = selectionKey;
            this.keyOp = keyOp;
        }
    }

// End of file.
}
