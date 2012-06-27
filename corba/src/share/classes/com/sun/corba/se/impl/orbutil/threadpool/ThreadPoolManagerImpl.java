/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil.threadpool;

import java.io.IOException;

import java.security.PrivilegedAction;
import java.security.AccessController;

import java.util.concurrent.atomic.AtomicInteger;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.orbutil.threadpool.NoSuchThreadPoolException;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPoolManager;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPoolChooser;

import com.sun.corba.se.impl.orbutil.threadpool.ThreadPoolImpl;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.spi.logging.CORBALogDomains;


public class ThreadPoolManagerImpl implements ThreadPoolManager
{
    private ThreadPool threadPool;
    private ThreadGroup threadGroup;

    private static final ORBUtilSystemException wrapper =
        ORBUtilSystemException.get(CORBALogDomains.RPC_TRANSPORT);

    public ThreadPoolManagerImpl() {
        threadGroup = getThreadGroup();
        threadPool = new ThreadPoolImpl(threadGroup,
            ORBConstants.THREADPOOL_DEFAULT_NAME);
    }

    private static AtomicInteger tgCount = new AtomicInteger();


    private ThreadGroup getThreadGroup() {
        ThreadGroup tg;

        // See bugs 4916766 and 4936203
        // We intend to create new threads in a reliable thread group.
        // This avoids problems if the application/applet
        // creates a thread group, makes JavaIDL calls which create a new
        // connection and ReaderThread, and then destroys the thread
        // group. If our ReaderThreads were to be part of such destroyed thread
        // group then it might get killed and cause other invoking threads
        // sharing the same connection to get a non-restartable
        // CommunicationFailure. We'd like to avoid that.
        //
        // Our solution is to create all of our threads in the highest thread
        // group that we have access to, given our own security clearance.
        //
        try {
            // try to get a thread group that's as high in the threadgroup
            // parent-child hierarchy, as we can get to.
            // this will prevent an ORB thread created during applet-init from
            // being killed when an applet dies.
            tg = AccessController.doPrivileged(
                new PrivilegedAction<ThreadGroup>() {
                    public ThreadGroup run() {
                        ThreadGroup tg = Thread.currentThread().getThreadGroup();
                        ThreadGroup ptg = tg;
                        try {
                            while (ptg != null) {
                                tg = ptg;
                                ptg = tg.getParent();
                            }
                        } catch (SecurityException se) {
                            // Discontinue going higher on a security exception.
                        }
                        return new ThreadGroup(tg, "ORB ThreadGroup " + tgCount.getAndIncrement());
                    }
                }
            );
        } catch (SecurityException e) {
            // something wrong, we go back to the original code
            tg = Thread.currentThread().getThreadGroup();
        }

        return tg;
    }

    public void close() {
        try {
            threadPool.close();
        } catch (IOException exc) {
            wrapper.threadPoolCloseError();
        }

        try {
            boolean isDestroyed = threadGroup.isDestroyed();
            int numThreads = threadGroup.activeCount();
            int numGroups = threadGroup.activeGroupCount();

            if (isDestroyed) {
                wrapper.threadGroupIsDestroyed(threadGroup);
            } else {
                if (numThreads > 0)
                    wrapper.threadGroupHasActiveThreadsInClose(threadGroup, numThreads);

                if (numGroups > 0)
                    wrapper.threadGroupHasSubGroupsInClose(threadGroup, numGroups);

                threadGroup.destroy();
            }
        } catch (IllegalThreadStateException exc) {
            wrapper.threadGroupDestroyFailed(exc, threadGroup);
        }

        threadGroup = null;
    }

    /**
    * This method will return an instance of the threadpool given a threadpoolId,
    * that can be used by any component in the app. server.
    *
    * @throws NoSuchThreadPoolException thrown when invalid threadpoolId is passed
    * as a parameter
    */
    public ThreadPool getThreadPool(String threadpoolId)
        throws NoSuchThreadPoolException {

        return threadPool;
    }

    /**
    * This method will return an instance of the threadpool given a numeric threadpoolId.
    * This method will be used by the ORB to support the functionality of
    * dedicated threadpool for EJB beans
    *
    * @throws NoSuchThreadPoolException thrown when invalidnumericIdForThreadpool is passed
    * as a parameter
    */
    public ThreadPool getThreadPool(int numericIdForThreadpool)
        throws NoSuchThreadPoolException {

        return threadPool;
    }

    /**
    * This method is used to return the numeric id of the threadpool, given a String
    * threadpoolId. This is used by the POA interceptors to add the numeric threadpool
    * Id, as a tagged component in the IOR. This is used to provide the functionality of
    * dedicated threadpool for EJB beans
    */
    public int  getThreadPoolNumericId(String threadpoolId) {
        return 0;
    }

    /**
    * Return a String Id for a numericId of a threadpool managed by the threadpool
    * manager
    */
    public String getThreadPoolStringId(int numericIdForThreadpool) {
       return "";
    }

    /**
    * Returns the first instance of ThreadPool in the ThreadPoolManager
    */
    public ThreadPool getDefaultThreadPool() {
        return threadPool;
    }

    /**
     * Return an instance of ThreadPoolChooser based on the componentId that was
     * passed as argument
     */
    public ThreadPoolChooser getThreadPoolChooser(String componentId) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return null;
    }
    /**
     * Return an instance of ThreadPoolChooser based on the componentIndex that was
     * passed as argument. This is added for improved performance so that the caller
     * does not have to pay the cost of computing hashcode for the componentId
     */
    public ThreadPoolChooser getThreadPoolChooser(int componentIndex) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return null;
    }

    /**
     * Sets a ThreadPoolChooser for a particular componentId in the ThreadPoolManager. This
     * would enable any component to add a ThreadPoolChooser for their specific use
     */
    public void setThreadPoolChooser(String componentId, ThreadPoolChooser aThreadPoolChooser) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
    }

    /**
     * Gets the numeric index associated with the componentId specified for a
     * ThreadPoolChooser. This method would help the component call the more
     * efficient implementation i.e. getThreadPoolChooser(int componentIndex)
     */
    public int getThreadPoolChooserNumericId(String componentId) {
        //FIXME: This method is not used, but should be fixed once
        //nio select starts working and we start using ThreadPoolChooser
        return 0;
    }

}

// End of file.
