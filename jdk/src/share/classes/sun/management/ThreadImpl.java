/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.management;

import java.lang.management.ManagementFactory;

import java.lang.management.ThreadInfo;

import javax.management.ObjectName;

/**
 * Implementation class for the thread subsystem.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getThreadMXBean() returns an instance
 * of this class.
 */
class ThreadImpl implements com.sun.management.ThreadMXBean {

    private final VMManagement jvm;

    // default for thread contention monitoring is disabled.
    private boolean contentionMonitoringEnabled = false;
    private boolean cpuTimeEnabled;
    private boolean allocatedMemoryEnabled;

    /**
     * Constructor of ThreadImpl class.
     */
    ThreadImpl(VMManagement vm) {
        this.jvm = vm;
        this.cpuTimeEnabled = jvm.isThreadCpuTimeEnabled();
        this.allocatedMemoryEnabled = jvm.isThreadAllocatedMemoryEnabled();
    }

    public int getThreadCount() {
        return jvm.getLiveThreadCount();
    }

    public int getPeakThreadCount() {
        return jvm.getPeakThreadCount();
    }

    public long getTotalStartedThreadCount() {
        return jvm.getTotalThreadCount();
    }

    public int getDaemonThreadCount() {
        return jvm.getDaemonThreadCount();
    }

    public boolean isThreadContentionMonitoringSupported() {
        return jvm.isThreadContentionMonitoringSupported();
    }

    public synchronized boolean isThreadContentionMonitoringEnabled() {
       if (!isThreadContentionMonitoringSupported()) {
            throw new UnsupportedOperationException(
                "Thread contention monitoring is not supported.");
        }
        return contentionMonitoringEnabled;
    }

    public boolean isThreadCpuTimeSupported() {
        return jvm.isOtherThreadCpuTimeSupported();
    }

    public boolean isCurrentThreadCpuTimeSupported() {
        return jvm.isCurrentThreadCpuTimeSupported();
    }

    public boolean isThreadAllocatedMemorySupported() {
        return jvm.isThreadAllocatedMemorySupported();
    }

    public boolean isThreadCpuTimeEnabled() {
        if (!isThreadCpuTimeSupported() &&
            !isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException(
                "Thread CPU time measurement is not supported");
        }
        return cpuTimeEnabled;
    }

    public boolean isThreadAllocatedMemoryEnabled() {
        if (!isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException(
                "Thread allocated memory measurement is not supported");
        }
        return allocatedMemoryEnabled;
    }

    public long[] getAllThreadIds() {
        Util.checkMonitorAccess();

        Thread[] threads = getThreads();
        int length = threads.length;
        long[] ids = new long[length];
        for (int i = 0; i < length; i++) {
            Thread t = threads[i];
            ids[i] = t.getId();
        }
        return ids;
    }

    public ThreadInfo getThreadInfo(long id) {
        long[] ids = new long[1];
        ids[0] = id;
        final ThreadInfo[] infos = getThreadInfo(ids, 0);
        return infos[0];
    }

    public ThreadInfo getThreadInfo(long id, int maxDepth) {
        long[] ids = new long[1];
        ids[0] = id;
        final ThreadInfo[] infos = getThreadInfo(ids, maxDepth);
        return infos[0];
    }

    public ThreadInfo[] getThreadInfo(long[] ids) {
        return getThreadInfo(ids, 0);
    }

    private void verifyThreadIds(long[] ids) {
        if (ids == null) {
            throw new NullPointerException("Null ids parameter.");
        }

        for (int i = 0; i < ids.length; i++) {
            if (ids[i] <= 0) {
                throw new IllegalArgumentException(
                    "Invalid thread ID parameter: " + ids[i]);
            }
        }
    }

    public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) {
        verifyThreadIds(ids);

        if (maxDepth < 0) {
            throw new IllegalArgumentException(
                "Invalid maxDepth parameter: " + maxDepth);
        }

        Util.checkMonitorAccess();

        ThreadInfo[] infos = new ThreadInfo[ids.length]; // nulls
        if (maxDepth == Integer.MAX_VALUE) {
            getThreadInfo1(ids, -1, infos);
        } else {
            getThreadInfo1(ids, maxDepth, infos);
        }
        return infos;
    }

    public void setThreadContentionMonitoringEnabled(boolean enable) {
        if (!isThreadContentionMonitoringSupported()) {
            throw new UnsupportedOperationException(
                "Thread contention monitoring is not supported");
        }

        Util.checkControlAccess();

        synchronized (this) {
            if (contentionMonitoringEnabled != enable) {
                if (enable) {
                    // if reeabled, reset contention time statistics
                    // for all threads
                    resetContentionTimes0(0);
                }

                // update the VM of the state change
                setThreadContentionMonitoringEnabled0(enable);

                contentionMonitoringEnabled = enable;
            }
        }
    }

    private boolean verifyCurrentThreadCpuTime() {
        // check if Thread CPU time measurement is supported.
        if (!isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException(
                "Current thread CPU time measurement is not supported.");
        }
        return isThreadCpuTimeEnabled();
    }

    public long getCurrentThreadCpuTime() {
        if (verifyCurrentThreadCpuTime()) {
            return getThreadTotalCpuTime0(0);
        }
        return -1;
    }

    public long getThreadCpuTime(long id) {
        long[] ids = new long[1];
        ids[0] = id;
        final long[] times = getThreadCpuTime(ids);
        return times[0];
    }

    private boolean verifyThreadCpuTime(long[] ids) {
        verifyThreadIds(ids);

        // check if Thread CPU time measurement is supported.
        if (!isThreadCpuTimeSupported() &&
            !isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException(
                "Thread CPU time measurement is not supported.");
        }

        if (!isThreadCpuTimeSupported()) {
            // support current thread only
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] != Thread.currentThread().getId()) {
                    throw new UnsupportedOperationException(
                        "Thread CPU time measurement is only supported" +
                        " for the current thread.");
                }
            }
        }

        return isThreadCpuTimeEnabled();
    }

    public long[] getThreadCpuTime(long[] ids) {
        boolean verified = verifyThreadCpuTime(ids);

        int length = ids.length;
        long[] times = new long[length];
        java.util.Arrays.fill(times, -1);

        if (verified) {
            if (length == 1) {
                long id = ids[0];
                if (id == Thread.currentThread().getId()) {
                    id = 0;
                }
                times[0] = getThreadTotalCpuTime0(id);
            } else {
                getThreadTotalCpuTime1(ids, times);
            }
        }
        return times;
    }

    public long getCurrentThreadUserTime() {
        if (verifyCurrentThreadCpuTime()) {
            return getThreadUserCpuTime0(0);
        }
        return -1;
    }

    public long getThreadUserTime(long id) {
        long[] ids = new long[1];
        ids[0] = id;
        final long[] times = getThreadUserTime(ids);
        return times[0];
    }

    public long[] getThreadUserTime(long[] ids) {
        boolean verified = verifyThreadCpuTime(ids);

        int length = ids.length;
        long[] times = new long[length];
        java.util.Arrays.fill(times, -1);

        if (verified) {
            if (length == 1) {
                long id = ids[0];
                if (id == Thread.currentThread().getId()) {
                    id = 0;
                }
                times[0] = getThreadUserCpuTime0(id);
            } else {
                getThreadUserCpuTime1(ids, times);
            }
        }
        return times;
    }

    public void setThreadCpuTimeEnabled(boolean enable) {
        if (!isThreadCpuTimeSupported() &&
            !isCurrentThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException(
                "Thread CPU time measurement is not supported");
        }

        Util.checkControlAccess();
        synchronized (this) {
            if (cpuTimeEnabled != enable) {
                // notify VM of the state change
                setThreadCpuTimeEnabled0(enable);
                cpuTimeEnabled = enable;
            }
        }
    }

    public long getThreadAllocatedBytes(long id) {
        long[] ids = new long[1];
        ids[0] = id;
        final long[] sizes = getThreadAllocatedBytes(ids);
        return sizes[0];
    }

    private boolean verifyThreadAllocatedMemory(long[] ids) {
        verifyThreadIds(ids);

        // check if Thread allocated memory measurement is supported.
        if (!isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException(
                "Thread allocated memory measurement is not supported.");
        }

        return isThreadAllocatedMemoryEnabled();
    }

    public long[] getThreadAllocatedBytes(long[] ids) {
        boolean verified = verifyThreadAllocatedMemory(ids);

        long[] sizes = new long[ids.length];
        java.util.Arrays.fill(sizes, -1);

        if (verified) {
            getThreadAllocatedMemory1(ids, sizes);
        }
        return sizes;
    }

    public void setThreadAllocatedMemoryEnabled(boolean enable) {
        if (!isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException(
                "Thread allocated memory measurement is not supported.");
        }

        Util.checkControlAccess();
        synchronized (this) {
            if (allocatedMemoryEnabled != enable) {
                // notify VM of the state change
                setThreadAllocatedMemoryEnabled0(enable);
                allocatedMemoryEnabled = enable;
            }
        }
    }

    public long[] findMonitorDeadlockedThreads() {
        Util.checkMonitorAccess();

        Thread[] threads = findMonitorDeadlockedThreads0();
        if (threads == null) {
            return null;
        }

        long[] ids = new long[threads.length];
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            ids[i] = t.getId();
        }
        return ids;
    }

    public long[] findDeadlockedThreads() {
        if (!isSynchronizerUsageSupported()) {
            throw new UnsupportedOperationException(
                "Monitoring of Synchronizer Usage is not supported.");
        }

        Util.checkMonitorAccess();

        Thread[] threads = findDeadlockedThreads0();
        if (threads == null) {
            return null;
        }

        long[] ids = new long[threads.length];
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            ids[i] = t.getId();
        }
        return ids;
    }

    public void resetPeakThreadCount() {
        Util.checkControlAccess();
        resetPeakThreadCount0();
    }

    public boolean isObjectMonitorUsageSupported() {
        return jvm.isObjectMonitorUsageSupported();
    }

    public boolean isSynchronizerUsageSupported() {
        return jvm.isSynchronizerUsageSupported();
    }

    private void verifyDumpThreads(boolean lockedMonitors,
                                   boolean lockedSynchronizers) {
        if (lockedMonitors && !isObjectMonitorUsageSupported()) {
            throw new UnsupportedOperationException(
                "Monitoring of Object Monitor Usage is not supported.");
        }

        if (lockedSynchronizers && !isSynchronizerUsageSupported()) {
            throw new UnsupportedOperationException(
                "Monitoring of Synchronizer Usage is not supported.");
        }

        Util.checkMonitorAccess();
    }

    public ThreadInfo[] getThreadInfo(long[] ids,
                                      boolean lockedMonitors,
                                      boolean lockedSynchronizers) {
        verifyThreadIds(ids);
        verifyDumpThreads(lockedMonitors, lockedSynchronizers);
        return dumpThreads0(ids, lockedMonitors, lockedSynchronizers);
    }

    public ThreadInfo[] dumpAllThreads(boolean lockedMonitors,
                                       boolean lockedSynchronizers) {
        verifyDumpThreads(lockedMonitors, lockedSynchronizers);
        return dumpThreads0(null, lockedMonitors, lockedSynchronizers);
    }

    // VM support where maxDepth == -1 to request entire stack dump
    private static native Thread[] getThreads();
    private static native void getThreadInfo1(long[] ids,
                                              int maxDepth,
                                              ThreadInfo[] result);
    private static native long getThreadTotalCpuTime0(long id);
    private static native void getThreadTotalCpuTime1(long[] ids, long[] result);
    private static native long getThreadUserCpuTime0(long id);
    private static native void getThreadUserCpuTime1(long[] ids, long[] result);
    private static native void getThreadAllocatedMemory1(long[] ids, long[] result);
    private static native void setThreadCpuTimeEnabled0(boolean enable);
    private static native void setThreadAllocatedMemoryEnabled0(boolean enable);
    private static native void setThreadContentionMonitoringEnabled0(boolean enable);
    private static native Thread[] findMonitorDeadlockedThreads0();
    private static native Thread[] findDeadlockedThreads0();
    private static native void resetPeakThreadCount0();
    private static native ThreadInfo[] dumpThreads0(long[] ids,
                                                    boolean lockedMonitors,
                                                    boolean lockedSynchronizers);

    // tid == 0 to reset contention times for all threads
    private static native void resetContentionTimes0(long tid);

    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
    }

}
