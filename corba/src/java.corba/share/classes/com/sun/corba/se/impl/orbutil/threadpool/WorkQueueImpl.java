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

import java.util.LinkedList;

import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.WorkQueue;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.threadpool.ThreadPoolImpl;

import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;

public class WorkQueueImpl implements WorkQueue
{
    private ThreadPool workerThreadPool;
    private LinkedList theWorkQueue = new LinkedList();
    private long workItemsAdded = 0;

    // Initialized to 1 to avoid divide by zero in averageTimeInQueue()
    private long workItemsDequeued = 1;

    private long totalTimeInQueue = 0;

    // Name of the work queue
    private String name;

    // MonitoredObject for work queue
    private MonitoredObject workqueueMonitoredObject;

    public WorkQueueImpl() {
        name=ORBConstants.WORKQUEUE_DEFAULT_NAME;
        initializeMonitoring();
    }

    public WorkQueueImpl(ThreadPool workerThreadPool) {
        this(workerThreadPool, ORBConstants.WORKQUEUE_DEFAULT_NAME);
    }

    public WorkQueueImpl(ThreadPool workerThreadPool, String name) {
        this.workerThreadPool = workerThreadPool;
        this.name = name;
        initializeMonitoring();
    }

    // Setup monitoring for this workqueue
    private void initializeMonitoring() {
        workqueueMonitoredObject = MonitoringFactories.
                            getMonitoredObjectFactory().
                            createMonitoredObject(name,
                            MonitoringConstants.WORKQUEUE_MONITORING_DESCRIPTION);

        LongMonitoredAttributeBase b1 = new
            LongMonitoredAttributeBase(MonitoringConstants.WORKQUEUE_TOTAL_WORK_ITEMS_ADDED,
                    MonitoringConstants.WORKQUEUE_TOTAL_WORK_ITEMS_ADDED_DESCRIPTION) {
                public Object getValue() {
                    return new Long(WorkQueueImpl.this.totalWorkItemsAdded());
                }
            };
        workqueueMonitoredObject.addAttribute(b1);
        LongMonitoredAttributeBase b2 = new
            LongMonitoredAttributeBase(MonitoringConstants.WORKQUEUE_WORK_ITEMS_IN_QUEUE,
                    MonitoringConstants.WORKQUEUE_WORK_ITEMS_IN_QUEUE_DESCRIPTION) {
                public Object getValue() {
                    return new Long(WorkQueueImpl.this.workItemsInQueue());
                }
            };
        workqueueMonitoredObject.addAttribute(b2);
        LongMonitoredAttributeBase b3 = new
            LongMonitoredAttributeBase(MonitoringConstants.WORKQUEUE_AVERAGE_TIME_IN_QUEUE,
                    MonitoringConstants.WORKQUEUE_AVERAGE_TIME_IN_QUEUE_DESCRIPTION) {
                public Object getValue() {
                    return new Long(WorkQueueImpl.this.averageTimeInQueue());
                }
            };
        workqueueMonitoredObject.addAttribute(b3);
    }


    // Package private method to get the monitored object for this
    // class
    MonitoredObject getMonitoredObject() {
        return workqueueMonitoredObject;
    }

    public synchronized void addWork(Work work) {
            workItemsAdded++;
            work.setEnqueueTime(System.currentTimeMillis());
            theWorkQueue.addLast(work);
            ((ThreadPoolImpl)workerThreadPool).notifyForAvailableWork(this);
    }

    synchronized Work requestWork(long waitTime) throws TimeoutException, InterruptedException
    {
        Work workItem;
        ((ThreadPoolImpl)workerThreadPool).incrementNumberOfAvailableThreads();

            if (theWorkQueue.size() != 0) {
                workItem = (Work)theWorkQueue.removeFirst();
                totalTimeInQueue += System.currentTimeMillis() - workItem.getEnqueueTime();
                workItemsDequeued++;
                ((ThreadPoolImpl)workerThreadPool).decrementNumberOfAvailableThreads();
                return workItem;
            }

            try {

                long remainingWaitTime = waitTime;
                long finishTime = System.currentTimeMillis() + waitTime;

                do {

                    this.wait(remainingWaitTime);

                    if (theWorkQueue.size() != 0) {
                        workItem = (Work)theWorkQueue.removeFirst();
                        totalTimeInQueue += System.currentTimeMillis() - workItem.getEnqueueTime();
                        workItemsDequeued++;
                        ((ThreadPoolImpl)workerThreadPool).decrementNumberOfAvailableThreads();
                        return workItem;
                    }

                    remainingWaitTime = finishTime - System.currentTimeMillis();

                } while (remainingWaitTime > 0);

                ((ThreadPoolImpl)workerThreadPool).decrementNumberOfAvailableThreads();
                throw new TimeoutException();

            } catch (InterruptedException ie) {
                ((ThreadPoolImpl)workerThreadPool).decrementNumberOfAvailableThreads();
                throw ie;
            }
    }

    public void setThreadPool(ThreadPool workerThreadPool) {
            this.workerThreadPool = workerThreadPool;
    }

    public ThreadPool getThreadPool() {
            return workerThreadPool;
    }

    /**
     * Returns the total number of Work items added to the Queue.
     * This method is unsynchronized and only gives a snapshot of the
     * state when it is called
     */
    public long totalWorkItemsAdded() {
        return workItemsAdded;
    }

    /**
     * Returns the total number of Work items in the Queue to be processed
     * This method is unsynchronized and only gives a snapshot of the
     * state when it is called
     */
    public int workItemsInQueue() {
        return theWorkQueue.size();
    }

    public synchronized long averageTimeInQueue() {
        return (totalTimeInQueue/workItemsDequeued);
    }

    public String getName() {
        return name;
    }
}

// End of file.
