/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.WorkQueue;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.threadpool.WorkQueueImpl;

import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;

public class ThreadPoolImpl implements ThreadPool
{
    private static int threadCounter = 0; // serial counter useful for debugging

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
    private long processedCount = 1;

    // Running aggregate of the time taken in millis to execute work items
    // processed by the threads in the threadpool
    private long totalTimeTaken = 0;

    // Lock for protecting state when required
    private Object lock = new Object();

    // Name of the ThreadPool
    private String name;

    // MonitoredObject for ThreadPool
    private MonitoredObject threadpoolMonitoredObject;

    // ThreadGroup in which threads should be created
    private ThreadGroup threadGroup ;

    /**
     * This constructor is used to create an unbounded threadpool
     */
    public ThreadPoolImpl(ThreadGroup tg, String threadpoolName) {
        inactivityTimeout = ORBConstants.DEFAULT_INACTIVITY_TIMEOUT;
        maxWorkerThreads = Integer.MAX_VALUE;
        workQueue = new WorkQueueImpl(this);
        threadGroup = tg ;
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
        synchronized (lock) {
            if (availableWorkerThreads == 0) {
                createWorkerThread();
            } else {
                aWorkQueue.notify();
            }
        }
    }


    /**
     * To be called from the workqueue to create worker threads when none
     * available.
     */
    void createWorkerThread() {
        WorkerThread thread;

        synchronized (lock) {
            if (boundedThreadPool) {
                if (currentThreadCount < maxWorkerThreads) {
                    thread = new WorkerThread(threadGroup, getName());
                    currentThreadCount++;
                } else {
                    // REVIST - Need to create a thread to monitor the
                    // the state for deadlock i.e. all threads waiting for
                    // something which can be got from the item in the
                    // workqueue, but there is no thread available to
                    // process that work item - DEADLOCK !!
                    return;
                }
            } else {
                thread = new WorkerThread(threadGroup, getName());
                currentThreadCount++;
            }
        }

        // The thread must be set to a daemon thread so the
        // VM can exit if the only threads left are PooledThreads
        // or other daemons.  We don't want to rely on the
        // calling thread always being a daemon.

        // Catch exceptions since setDaemon can cause a
        // security exception to be thrown under netscape
        // in the Applet mode
        try {
            thread.setDaemon(true);
        } catch (Exception e) {
            // REVISIT - need to do some logging here
        }

        thread.start();
    }

    /**
    * This method will return the minimum number of threads maintained
    * by the threadpool.
    */
    public int minimumNumberOfThreads() {
        return minWorkerThreads;
    }

    /**
    * This method will return the maximum number of threads in the
    * threadpool at any point in time, for the life of the threadpool
    */
    public int maximumNumberOfThreads() {
        return maxWorkerThreads;
    }

    /**
    * This method will return the time in milliseconds when idle
    * threads in the threadpool are removed.
    */
    public long idleTimeoutForThreads() {
        return inactivityTimeout;
    }

    /**
    * This method will return the total number of threads currently in the
    * threadpool. This method returns a value which is not synchronized.
    */
    public int currentNumberOfThreads() {
        synchronized (lock) {
            return currentThreadCount;
        }
    }

    /**
    * This method will return the number of available threads in the
    * threadpool which are waiting for work. This method returns a
    * value which is not synchronized.
    */
    public int numberOfAvailableThreads() {
        synchronized (lock) {
            return availableWorkerThreads;
        }
    }

    /**
    * This method will return the number of busy threads in the threadpool
    * This method returns a value which is not synchronized.
    */
    public int numberOfBusyThreads() {
        synchronized (lock) {
            return (currentThreadCount - availableWorkerThreads);
        }
    }

    /**
     * This method returns the average elapsed time taken to complete a Work
     * item in milliseconds.
     */
    public long averageWorkCompletionTime() {
        synchronized (lock) {
            return (totalTimeTaken / processedCount);
        }
    }

    /**
     * This method returns the number of Work items processed by the threadpool
     */
    public long currentProcessedCount() {
        synchronized (lock) {
            return processedCount;
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
        return ThreadPoolImpl.threadCounter++;
    }


    private class WorkerThread extends Thread
    {
        private Work currentWork;
        private int threadId = 0; // unique id for the thread
        // thread pool this WorkerThread belongs too
        private String threadPoolName;
        // name seen by Thread.getName()
        private StringBuffer workerThreadName = new StringBuffer();

        WorkerThread(ThreadGroup tg, String threadPoolName) {
            super(tg, "Idle");
            this.threadId = ThreadPoolImpl.getUniqueThreadId();
            this.threadPoolName = threadPoolName;
            setName(composeWorkerThreadName(threadPoolName, "Idle"));
        }

        public void run() {
            while (true) {
                try {

                    synchronized (lock) {
                        availableWorkerThreads++;
                    }

                    // Get some work to do
                    currentWork = ((WorkQueueImpl)workQueue).requestWork(inactivityTimeout);

                    synchronized (lock) {
                        availableWorkerThreads--;
                        // It is possible in notifyForAvailableWork that the
                        // check for availableWorkerThreads = 0 may return
                        // false, because the availableWorkerThreads has not been
                        // decremented to zero before the producer thread added
                        // work to the queue. This may create a deadlock, if the
                        // executing thread needs information which is in the work
                        // item queued in the workqueue, but has no thread to work
                        // on it since none was created because availableWorkerThreads = 0
                        // returned false.
                        // The following code will ensure that a thread is always available
                        // in those situations
                        if  ((availableWorkerThreads == 0) &&
                                (workQueue.workItemsInQueue() > 0)) {
                            createWorkerThread();
                        }
                    }

                    // Set the thread name for debugging.
                    setName(composeWorkerThreadName(threadPoolName,
                                      Integer.toString(this.threadId)));

                    long start = System.currentTimeMillis();

                    try {
                        // Do the work
                        currentWork.doWork();
                    } catch (Throwable t) {
                        // Ignore all errors.
                        ;
                    }

                    long end = System.currentTimeMillis();


                    synchronized (lock) {
                        totalTimeTaken += (end - start);
                        processedCount++;
                    }

                    // set currentWork to null so that the work item can be
                    // garbage collected
                    currentWork = null;

                    setName(composeWorkerThreadName(threadPoolName, "Idle"));

                } catch (TimeoutException e) {
                    // This thread timed out waiting for something to do.

                    synchronized (lock) {
                        availableWorkerThreads--;

                        // This should for both bounded and unbounded case
                        if (currentThreadCount > minWorkerThreads) {
                            currentThreadCount--;
                            // This thread can exit.
                            return;
                        } else {
                            // Go back to waiting on workQueue
                            continue;
                        }
                    }
                } catch (InterruptedException ie) {
                    // InterruptedExceptions are
                    // caught here.  Thus, threads can be forced out of
                    // requestWork and so they have to reacquire the lock.
                    // Other options include ignoring or
                    // letting this thread die.
                    // Ignoring for now. REVISIT
                    synchronized (lock) {
                        availableWorkerThreads--;
                    }

                } catch (Throwable e) {

                    // Ignore any exceptions that currentWork.process
                    // accidently lets through, but let Errors pass.
                    // Add debugging output?  REVISIT
                    synchronized (lock) {
                        availableWorkerThreads--;
                    }

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
