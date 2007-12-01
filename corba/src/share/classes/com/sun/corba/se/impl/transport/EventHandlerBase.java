/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.transport;

import java.nio.channels.SelectionKey;

import org.omg.CORBA.INTERNAL;

import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.EventHandler;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchThreadPoolException;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.Work;

import com.sun.corba.se.impl.orbutil.ORBUtility;

public abstract class EventHandlerBase
    implements
        EventHandler
{
    protected ORB orb;
    protected Work work;
    protected boolean useWorkerThreadForEvent;
    protected boolean useSelectThreadToWait;
    protected SelectionKey selectionKey;

    ////////////////////////////////////////////////////
    //
    // EventHandler methods
    //

    public void setUseSelectThreadToWait(boolean x)
    {
        useSelectThreadToWait = x;
    }

    public boolean shouldUseSelectThreadToWait()
    {
        return useSelectThreadToWait;
    }

    public void setSelectionKey(SelectionKey selectionKey)
    {
        this.selectionKey = selectionKey;
    }

    public SelectionKey getSelectionKey()
    {
        return selectionKey;
    }

    /*
     * NOTE:
     * This is not thread-safe by design.
     * Only one thread should call it - a reader/listener/select thread.
     * Not stateless: interest ops, registration.
     */
    public void handleEvent()
    {
        if (orb.transportDebugFlag) {
            dprint(".handleEvent->: " + this);
        }
        getSelectionKey().interestOps(getSelectionKey().interestOps() &
                                      (~ getInterestOps()));
        if (shouldUseWorkerThreadForEvent()) {
            Throwable throwable = null;
            try {
                if (orb.transportDebugFlag) {
                    dprint(".handleEvent: addWork to pool: " + 0);
                }
                orb.getThreadPoolManager().getThreadPool(0)
                    .getWorkQueue(0).addWork(getWork());
            } catch (NoSuchThreadPoolException e) {
                throwable = e;
            } catch (NoSuchWorkQueueException e) {
                throwable = e;
            }
            // REVISIT: need to close connection.
            if (throwable != null) {
                if (orb.transportDebugFlag) {
                    dprint(".handleEvent: " + throwable);
                }
                INTERNAL i = new INTERNAL("NoSuchThreadPoolException");
                i.initCause(throwable);
                throw i;
            }
        } else {
            if (orb.transportDebugFlag) {
                dprint(".handleEvent: doWork");
            }
            getWork().doWork();
        }
        if (orb.transportDebugFlag) {
            dprint(".handleEvent<-: " + this);
        }
    }

    public boolean shouldUseWorkerThreadForEvent()
    {
        return useWorkerThreadForEvent;
    }

    public void setUseWorkerThreadForEvent(boolean x)
    {
        useWorkerThreadForEvent = x;
    }

    public void setWork(Work work)
    {
        this.work = work;
    }

    public Work getWork()
    {
        return work;
    }

    private void dprint(String msg)
    {
        ORBUtility.dprint("EventHandlerBase", msg);
    }
}

// End of file.
