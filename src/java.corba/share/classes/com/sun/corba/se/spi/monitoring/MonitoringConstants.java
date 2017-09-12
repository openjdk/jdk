/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.monitoring;

public interface MonitoringConstants
{
    public static final String DEFAULT_MONITORING_ROOT = "orb";
    public static final String DEFAULT_MONITORING_ROOT_DESCRIPTION =
        "ORB Management and Monitoring Root";

    //
    // Connection Monitoring
    //

    public static final String CONNECTION_MONITORING_ROOT =
        "Connections";
    public static final String CONNECTION_MONITORING_ROOT_DESCRIPTION =
        "Statistics on inbound/outbound connections";

    public static final String INBOUND_CONNECTION_MONITORING_ROOT =
        "Inbound";
    public static final String INBOUND_CONNECTION_MONITORING_ROOT_DESCRIPTION=
        "Statistics on inbound connections";

    public static final String OUTBOUND_CONNECTION_MONITORING_ROOT =
        "Outbound";
    public static final String OUTBOUND_CONNECTION_MONITORING_ROOT_DESCRIPTION=
        "Statistics on outbound connections";

    public static final String CONNECTION_MONITORING_DESCRIPTION =
        "Connection statistics";

    public static final String CONNECTION_TOTAL_NUMBER_OF_CONNECTIONS =
        "NumberOfConnections";
    public static final String CONNECTION_TOTAL_NUMBER_OF_CONNECTIONS_DESCRIPTION =
        "The total number of connections";
    public static final String CONNECTION_NUMBER_OF_IDLE_CONNECTIONS =
        "NumberOfIdleConnections";
    public static final String CONNECTION_NUMBER_OF_IDLE_CONNECTIONS_DESCRIPTION =
        "The number of idle connections";
    public static final String CONNECTION_NUMBER_OF_BUSY_CONNECTIONS =
        "NumberOfBusyConnections";
    public static final String CONNECTION_NUMBER_OF_BUSY_CONNECTIONS_DESCRIPTION =
        "The number of busy connections";

    //
    // ThreadPool and WorkQueue monitoring constants
    //

    public static final String THREADPOOL_MONITORING_ROOT = "threadpool";
    public static final String THREADPOOL_MONITORING_ROOT_DESCRIPTION =
        "Monitoring for all ThreadPool instances";
    public static final String THREADPOOL_MONITORING_DESCRIPTION =
        "Monitoring for a ThreadPool";
    public static final String THREADPOOL_CURRENT_NUMBER_OF_THREADS =
        "currentNumberOfThreads";
    public static final String THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION =
        "Current number of total threads in the ThreadPool";
    public static final String THREADPOOL_NUMBER_OF_AVAILABLE_THREADS =
        "numberOfAvailableThreads";
    public static final String THREADPOOL_NUMBER_OF_AVAILABLE_THREADS_DESCRIPTION =
        "Number of available threads in the ThreadPool";
    public static final String THREADPOOL_NUMBER_OF_BUSY_THREADS =
        "numberOfBusyThreads";
    public static final String THREADPOOL_NUMBER_OF_BUSY_THREADS_DESCRIPTION =
        "Number of busy threads in the ThreadPool";
    public static final String THREADPOOL_AVERAGE_WORK_COMPLETION_TIME =
        "averageWorkCompletionTime";
    public static final String THREADPOOL_AVERAGE_WORK_COMPLETION_TIME_DESCRIPTION =
        "Average elapsed time taken to complete a work item by the ThreadPool";
    public static final String THREADPOOL_CURRENT_PROCESSED_COUNT =
        "currentProcessedCount";
    public static final String THREADPOOL_CURRENT_PROCESSED_COUNT_DESCRIPTION =
        "Number of Work items processed by the ThreadPool";

    public static final String WORKQUEUE_MONITORING_DESCRIPTION =
        "Monitoring for a Work Queue";
    public static final String WORKQUEUE_TOTAL_WORK_ITEMS_ADDED =
        "totalWorkItemsAdded";
    public static final String WORKQUEUE_TOTAL_WORK_ITEMS_ADDED_DESCRIPTION =
        "Total number of Work items added to the Queue";
    public static final String WORKQUEUE_WORK_ITEMS_IN_QUEUE =
        "workItemsInQueue";
    public static final String WORKQUEUE_WORK_ITEMS_IN_QUEUE_DESCRIPTION =
        "Number of Work items in the Queue to be processed";
    public static final String WORKQUEUE_AVERAGE_TIME_IN_QUEUE =
        "averageTimeInQueue";
    public static final String WORKQUEUE_AVERAGE_TIME_IN_QUEUE_DESCRIPTION =
        "Average time a work item waits in the work queue";
}

// End of file.
