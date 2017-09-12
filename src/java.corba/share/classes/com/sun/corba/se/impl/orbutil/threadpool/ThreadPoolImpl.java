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

package com.sun.corba.se.impl.orbutil.threadpool;

import java.io.IOException;
import java.io.Closeable;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.List;
import java.util.ArrayList;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.WorkQueue;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.threadpool.WorkQueueImpl;

import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.spi.logging.CORBALogDomains;

public class ThreadPoolImpl implements ThreadPool
{
    // serial counter useful for debugging
    private static AtomicInteger threadCounter = new AtomicInteger(0);
    private static final ORBUtilSystemException wrapper =
        ORBUtilSystemException.get(CORBALogDomains.RPC_TRANSPORT);


    // Any time currentThreadCount and/or availableWorkerThreads is updated
    // or accessed this ThreadPool's WorkQueue must be locked. And, it is
    // expected that this ThreadPool's WorkQueue is the only object that
    // updates and accesses these values directly and indirectly though a
    // call to a method in this ThreadPool. If any call to update or access
    // those values must synchronized on this ThreadPool's WorkQueue.
    private WorkQueue workQueue;

    // Stores the number of available worker threads
    private int availableWorkerThreads = 0;

    // Stores the number of threads in the threadpool currently
    private int currentThreadCount = 0;

    // Minimum number of worker threads created at instantiation of the threadpool
    private int minWorkerThreads = 0;

    // Maximum number of worker threads in the threadpool
    private int maxWorkerThreads = 0;

    // Inactivity timeout value for worker threads to exit and stop running
    private long inactivityTimeout;

    // Indicates if the threadpool is bounded or unbounded
    private boolean boundedThreadPool = false;

    // Running count of the work items processed
    // Set the value to 1 so that divide by zero is avoided in
    // averageWorkCompletionTime()
    private AtomicLong processedCount = new AtomicLong(1);

    // Running aggregate of the time taken in millis to execute work items
    // processed by the threads in the threadpool
    private AtomicLong totalTimeTaken = new AtomicLong(0);

    // Name of the ThreadPool
    private String name;

    // MonitoredObject for ThreadPool
    private MonitoredObject threadpoolMonitoredObject;

    // ThreadGroup in which threads should be created
    private ThreadGroup threadGroup;

    Object workersLock = new Object();
    List<WorkerThread> workers = new ArrayList<>();

    /**
     * This constructor is used to create an unbounded threadpool
     */
    public ThreadPoolImpl(ThreadGroup tg, String threadpoolName) {
        inactivityTimeout = ORBConstants.DEFAULT_INACTIVITY_TIMEOUT;
        maxWorkerThreads = Integer.MAX_VALUE;
        workQueue = new WorkQueueImpl(this);
        threadGroup = tg;
        name = threadpoolName;
        initializeMonitoring();
    }

    /**
     * This constructor is used to create an unbounded threadpool
     * in the ThreadGroup of the current thread
     */
    public ThreadPoolImpl(String threadpoolName) {
        this( Thread.currentThread().getThreadGroup(), threadpoolName ) ;
    }

    /**
     * This constructor is used to create bounded threadpool
     */
    public ThreadPoolImpl(int minSize, int maxSize, long timeout,
                                            String threadpoolName)
    {
        minWorkerThreads = minSize;
        maxWorkerThreads = maxSize;
        inactivityTimeout = timeout;
        boundedThreadPool = true;
        workQueue = new WorkQueueImpl(this);
        name = threadpoolName;
        for (int i = 0; i < minWorkerThreads; i++) {
            createWorkerThread();
        }
        initializeMonitoring();
    }

    // Note that this method should not return until AFTER all threads have died.
    public void close() throws IOException {

        // Copy to avoid concurrent modification problems.
        List<WorkerThread> copy = null;
        synchronized (workersLock) {
            copy = new ArrayList<>(workers);
        }

        for (WorkerThread wt : copy) {
            wt.close();
            while (wt.getState() != Thread.State.TERMINATED) {
                try {
                    wt.join();
                } catch (InterruptedException exc) {
                    wrapper.interruptedJoinCallWhileClosingThreadPool(exc, wt, this);
                }
            }
        }

        threadGroup = null;
    }


    // Setup monitoring for this threadpool
    private void initializeMonitoring() {
        // Get root monitored object
        MonitoredObject root = MonitoringFactories.getMonitoringManagerFactory().
                createMonitoringManager(MonitoringConstants.DEFAULT_MONITORING_ROOT, null).
                getRootMonitoredObject();

        // Create the threadpool monitoring root
        MonitoredObject threadPoolMonitoringObjectRoot = root.getChild(
                    MonitoringConstants.THREADPOOL_MONITORING_ROOT);
        if (threadPoolMonitoringObjectRoot == null) {
            threadPoolMonitoringObjectRoot =  MonitoringFactories.
                    getMonitoredObjectFactory().createMonitoredObject(
                    MonitoringConstants.THREADPOOL_MONITORING_ROOT,
                    MonitoringConstants.THREADPOOL_MONITORING_ROOT_DESCRIPTION);
            root.addChild(threadPoolMonitoringObjectRoot);
        }
        threadpoolMonitoredObject = MonitoringFactories.
                    getMonitoredObjectFactory().
                    createMonitoredObject(name,
                    MonitoringConstants.THREADPOOL_MONITORING_DESCRIPTION);

        threadPoolMonitoringObjectRoot.addChild(threadpoolMonitoredObject);

        LongMonitoredAttributeBase b1 = new
            LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS,
                    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
                public Object getValue() {
                    return new Long(ThreadPoolImpl.this.currentNumberOfThreads());
                }
            };
        threadpoolMonitoredObject.addAttribute(b1);
        LongMonitoredAttributeBase b2 = new
            LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_AVAILABLE_THREADS,
                    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
                public Object getValue() {
                    return new Long(ThreadPoolImpl.this.numberOfAvailableThreads());
                }
            };
        threadpoolMonitoredObject.addAttribute(b2);
        LongMonitoredAttributeBase b3 = new
            LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS,
                    MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS_DESCRIPTION) {
                public Object getValue() {
                    return new Long(ThreadPoolImpl.this.numberOfBusyThreads());
                }
            };
        threadpoolMonitoredObject.addAttribute(b3);
        LongMonitoredAttributeBase b4 = new
            LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME,
                    MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME_DESCRIPTION) {
                public Object getValue() {
                    return new Long(ThreadPoolImpl.this.averageWorkCompletionTime());
                }
            };
        threadpoolMonitoredObject.addAttribute(b4);
        LongMonitoredAttributeBase b5 = new
            LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT,
                    MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT_DESCRIPTION) {
                public Object getValue() {
                    return new Long(ThreadPoolImpl.this.currentProcessedCount());
                }
            };
        threadpoolMonitoredObject.addAttribute(b5);

        // Add the monitored object for the WorkQueue

        threadpoolMonitoredObject.addChild(
                ((WorkQueueImpl)workQueue).getMonitoredObject());
    }

    // Package private method to get the monitored object for this
    // class
    MonitoredObject getMonitoredObject() {
        return threadpoolMonitoredObject;
    }

    public WorkQueue getAnyWorkQueue()
    {
        return workQueue;
    }

    public WorkQueue getWorkQueue(int queueId)
        throws NoSuchWorkQueueException
    {
        if (queueId != 0)
            throw new NoSuchWorkQueueException();
        return workQueue;
    }

    /**
     * To be called from the workqueue when work is added to the
     * workQueue. This method would create new threads if required
     * or notify waiting threads on the queue for available work
     */
    void notifyForAvailableWork(WorkQueue aWorkQueue) {
        synchronized (aWorkQueue) {
            if (availableWorkerThreads < aWorkQueue.workItemsInQueue()) {
                createWorkerThread();
            } else {
                aWorkQueue.notify();
            }
        }
    }


    private Thread createWorkerThreadHelper( String name ) {
        // Thread creation needs to be in a doPrivileged block
        // if there is a non-null security manager for two reasons:
        // 1. The creation of a thread in a specific ThreadGroup
        //    is a privileged operation.  Lack of a doPrivileged
        //    block here causes an AccessControlException
        //    (see bug 6268145).
        // 2. We want to make sure that the permissions associated
        //    with this thread do NOT include the permissions of
        //    the current thread that is calling this method.
        //    This leads to problems in the app server where
        //    some threads in the ThreadPool randomly get
        //    bad permissions, leading to unpredictable
        //    permission errors (see bug 6021011).
        //
        //    A Java thread contains a stack of call frames,
        //    one for each method called that has not yet returned.
        //    Each method comes from a particular class.  The class
        //    was loaded by a ClassLoader which has an associated
        //    CodeSource, and this determines the Permissions
        //    for all methods in that class.  The current
        //    Permissions for the thread are the intersection of
        //    all Permissions for the methods on the stack.
        //    This is part of the Security Context of the thread.
        //
        //    When a thread creates a new thread, the new thread
        //    inherits the security context of the old thread.
        //    This is bad in a ThreadPool, because different
        //    creators of threads may have different security contexts.
        //    This leads to occasional unpredictable errors when
        //    a thread is re-used in a different security context.
        //
        //    Avoiding this problem is simple: just do the thread
        //    creation in a doPrivileged block.  This sets the
        //    inherited security context to that of the code source
        //    for the ORB code itself, which contains all permissions
        //    in either Java SE or Java EE.
        WorkerThread thread = new WorkerThread(threadGroup, name);
        synchronized (workersLock) {
            workers.add(thread);
        }

        // The thread must be set to a daemon thread so the
        // VM can exit if the only threads left are PooledThreads
        // or other daemons.  We don't want to rely on the
        // calling thread always being a daemon.
        // Note that no exception is possible here since we
        // are inside the doPrivileged block.
        thread.setDaemon(true);

        wrapper.workerThreadCreated(thread, thread.getContextClassLoader());

        thread.start();
        return null;
    }


    /**
     * To be called from the workqueue to create worker threads when none
     * available.
     */
    void createWorkerThread() {
        final String name = getName();
        synchronized (workQueue) {
            try {
                if (System.getSecurityManager() == null) {
                    createWorkerThreadHelper(name);
                } else {
                    // If we get here, we need to create a thread.
                    AccessController.doPrivileged(
                            new PrivilegedAction() {
                        public Object run() {
                            return createWorkerThreadHelper(name);
                        }
                    }
                    );
                }
            } catch (Throwable t) {
                // Decrementing the count of current worker threads.
                // But, it will be increased in the finally block.
                decrementCurrentNumberOfThreads();
                wrapper.workerThreadCreationFailure(t);
            } finally {
                incrementCurrentNumberOfThreads();
            }
        }
    }

    public int minimumNumberOfThreads() {
        return minWorkerThreads;
    }

    public int maximumNumberOfThreads() {
        return maxWorkerThreads;
    }

    public long idleTimeoutForThreads() {
        return inactivityTimeout;
    }

    public int currentNumberOfThreads() {
        synchronized (workQueue) {
            return currentThreadCount;
        }
    }

    void decrementCurrentNumberOfThreads() {
        synchronized (workQueue) {
            currentThreadCount--;
        }
    }

    void incrementCurrentNumberOfThreads() {
        synchronized (workQueue) {
            currentThreadCount++;
        }
    }

    public int numberOfAvailableThreads() {
        synchronized (workQueue) {
            return availableWorkerThreads;
        }
    }

    public int numberOfBusyThreads() {
        synchronized (workQueue) {
            return (currentThreadCount - availableWorkerThreads);
        }
    }

    public long averageWorkCompletionTime() {
        synchronized (workQueue) {
            return (totalTimeTaken.get() / processedCount.get());
        }
    }

    public long currentProcessedCount() {
        synchronized (workQueue) {
            return processedCount.get();
        }
    }

    public String getName() {
        return name;
    }

    /**
    * This method will return the number of WorkQueues serviced by the threadpool.
    */
    public int numberOfWorkQueues() {
        return 1;
    }


    private static synchronized int getUniqueThreadId() {
        return ThreadPoolImpl.threadCounter.incrementAndGet();
    }

    /**
     * This method will decrement the number of available threads
     * in the threadpool which are waiting for work. Called from
     * WorkQueueImpl.requestWork()
     */
    void decrementNumberOfAvailableThreads() {
        synchronized (workQueue) {
            availableWorkerThreads--;
        }
    }

    /**
     * This method will increment the number of available threads
     * in the threadpool which are waiting for work. Called from
     * WorkQueueImpl.requestWork()
     */
    void incrementNumberOfAvailableThreads() {
        synchronized (workQueue) {
            availableWorkerThreads++;
        }
    }


    private class WorkerThread extends Thread implements Closeable
    {
        private Work currentWork;
        private int threadId = 0; // unique id for the thread
        private volatile boolean closeCalled = false;
        private String threadPoolName;
        // name seen by Thread.getName()
        private StringBuffer workerThreadName = new StringBuffer();

        WorkerThread(ThreadGroup tg, String threadPoolName) {
            super(tg, null, "Idle", 0, false);
            this.threadId = ThreadPoolImpl.getUniqueThreadId();
            this.threadPoolName = threadPoolName;
            setName(composeWorkerThreadName(threadPoolName, "Idle"));
        }

        public synchronized void close() {
            closeCalled = true;
            interrupt();
        }

        private void resetClassLoader() {

        }

        private void performWork() {
            long start = System.currentTimeMillis();
            try {
                currentWork.doWork();
            } catch (Throwable t) {
                wrapper.workerThreadDoWorkThrowable(this, t);
            }
            long elapsedTime = System.currentTimeMillis() - start;
            totalTimeTaken.addAndGet(elapsedTime);
            processedCount.incrementAndGet();
        }

        public void run() {
            try  {
                while (!closeCalled) {
                    try {
                        currentWork = ((WorkQueueImpl)workQueue).requestWork(
                            inactivityTimeout);
                        if (currentWork == null)
                            continue;
                    } catch (InterruptedException exc) {
                        wrapper.workQueueThreadInterrupted( exc, getName(),
                           Boolean.valueOf(closeCalled));

                        continue ;
                    } catch (Throwable t) {
                         wrapper.workerThreadThrowableFromRequestWork(this, t,
                                workQueue.getName());

                        continue;
                    }

                    performWork();

                    // set currentWork to null so that the work item can be
                    // garbage collected without waiting for the next work item.
                    currentWork = null;

                    resetClassLoader();
                }
            } catch (Throwable e) {
                // This should not be possible
                wrapper.workerThreadCaughtUnexpectedThrowable(this,e);
            } finally {
                synchronized (workersLock) {
                    workers.remove(this);
                }
            }
        }

        private String composeWorkerThreadName(String poolName, String workerName) {
            workerThreadName.setLength(0);
            workerThreadName.append("p: ").append(poolName);
            workerThreadName.append("; w: ").append(workerName);
            return workerThreadName.toString();
        }
    } // End of WorkerThread class

}

// End of file.
