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

package com.sun.corba.se.spi.orbutil.threadpool;

public interface WorkQueue
{

    /**
    * This method is used to add work to the WorkQueue
    */
    public void addWork(Work aWorkItem);

    /**
    * This method will return the name of the WorkQueue.
    */
    public String getName();

    /**
    * Returns the total number of Work items added to the Queue.
    */
    public long totalWorkItemsAdded();

    /**
    * Returns the total number of Work items in the Queue to be processed.
    */
    public int workItemsInQueue();

    /**
    * Returns the average time a work item is waiting in the queue before
    * getting processed.
    */
    public long averageTimeInQueue();

    /**
     * Set the ThreadPool instance servicing this WorkQueue
     */
    public void setThreadPool(ThreadPool aThreadPool);

    /**
     * Get the ThreadPool instance servicing this WorkQueue
     */
    public ThreadPool getThreadPool();
}

// End of file.
