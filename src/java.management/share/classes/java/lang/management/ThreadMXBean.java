/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.management;

import java.util.Map;

/**
 * The management interface for the thread system of the Java virtual machine.
 *
 * <p> {@code ThreadMXBean} supports monitoring and management of
 * {@linkplain Thread##platform-threads platform threads} in the Java
 * virtual machine. Platform threads are typically mapped to kernel
 * threads scheduled by the operating system.  {@code ThreadMXBean}
 * does not support monitoring or management of {@linkplain
 * Thread##virtual-threads virtual threads}.
 *
 * <p> A Java virtual machine has a single instance of the implementation
 * class of this interface.  This instance implementing this interface is
 * an <a href="ManagementFactory.html#MXBean">MXBean</a>
 * that can be obtained by calling
 * the {@link ManagementFactory#getThreadMXBean} method or
 * from the {@link ManagementFactory#getPlatformMBeanServer
 * platform MBeanServer} method.
 *
 * <p>The {@code ObjectName} for uniquely identifying the MXBean for
 * the thread system within an MBeanServer is:
 * <blockquote>
 *    {@link ManagementFactory#THREAD_MXBEAN_NAME
 *           java.lang:type=Threading}
 * </blockquote>
 *
 * It can be obtained by calling the
 * {@link PlatformManagedObject#getObjectName} method.
 *
 * <h2>Thread ID</h2>
 * Thread ID is a positive long value returned by calling the thread's
 * {@link Thread#threadId() threadId()} method.
 * The thread ID is unique during its lifetime.  When the thread terminates,
 * its thread ID may be reused.
 *
 * <p> Some methods in this interface take a thread ID or an array
 * of thread IDs as the input parameter and return per-thread information.
 *
 * <h2>Thread CPU time</h2>
 * A Java virtual machine implementation may support measuring the CPU time
 * for the current platform thread, for any platform thread, or for no threads.
 *
 * <p>
 * The {@link #isThreadCpuTimeSupported} method can be used to determine
 * if a Java virtual machine supports measuring of the CPU time for any
 * platform thread.  The {@link #isCurrentThreadCpuTimeSupported()} method
 * can be used to determine if a Java virtual machine supports measuring of
 * the CPU time with the {@link #getCurrentThreadCpuTime()} and
 * {@link #getCurrentThreadUserTime()} methods from a platform thread.
 *
 * <p> The CPU time provided by this interface has nanosecond precision
 * but not necessarily nanosecond accuracy.
 *
 * <p>
 * A Java virtual machine may disable CPU time measurement
 * by default.
 * The {@link #isThreadCpuTimeEnabled} and {@link #setThreadCpuTimeEnabled}
 * methods can be used to test if CPU time measurement is enabled
 * and to enable/disable this support respectively.
 * Enabling thread CPU measurement could be expensive in some
 * Java virtual machine implementations.
 *
 * <h2>Thread Contention Monitoring</h2>
 * Some Java virtual machines may support thread contention monitoring.
 * When thread contention monitoring is enabled, the accumulated elapsed
 * time that the thread has blocked for synchronization or waited for
 * notification will be collected and returned in the
 * <a href="ThreadInfo.html#SyncStats">{@code ThreadInfo}</a> object.
 * <p>
 * The {@link #isThreadContentionMonitoringSupported} method can be used to
 * determine if a Java virtual machine supports thread contention monitoring.
 * The thread contention monitoring is disabled by default.  The
 * {@link #setThreadContentionMonitoringEnabled} method can be used to enable
 * thread contention monitoring.
 *
 * <h2>Synchronization Information and Deadlock Detection</h2>
 * Some Java virtual machines may support monitoring of
 * {@linkplain #isObjectMonitorUsageSupported object monitor usage} and
 * {@linkplain #isSynchronizerUsageSupported ownable synchronizer usage}.
 * The {@link #getThreadInfo(long[], boolean, boolean)} and
 * {@link #dumpAllThreads} methods can be used to obtain the thread stack trace
 * and synchronization information including which
 * {@linkplain LockInfo <i>lock</i>} a thread is blocked to
 * acquire or waiting on and which locks the thread currently owns.
 * <p>
 * The {@code ThreadMXBean} interface provides the
 * {@link #findMonitorDeadlockedThreads} and
 * {@link #findDeadlockedThreads} methods to find deadlocks in
 * the running application.
 *
 * @see ManagementFactory#getPlatformMXBeans(Class)
 * @see <a href="../../../javax/management/package-summary.html">
 *      JMX Specification.</a>
 * @see <a href="package-summary.html#examples">
 *      Ways to Access MXBeans</a>
 *
 * @author  Mandy Chung
 * @since   1.5
 */

public interface ThreadMXBean extends PlatformManagedObject {
    /**
     * Returns the current number of live platform threads including both
     * daemon and non-daemon threads.
     * The count does not include virtual threads.
     *
     * @return the current number of live platform threads.
     */
    public int getThreadCount();

    /**
     * Returns the peak live platform thread count since the Java virtual
     * machine started or peak was reset.
     * The count does not include virtual threads.
     *
     * @return the peak live platform thread count.
     */
    public int getPeakThreadCount();

    /**
     * Returns the total number of platform threads created and also started
     * since the Java virtual machine started.
     * The count does not include virtual threads.
     *
     * @return the total number of platform threads started.
     */
    public long getTotalStartedThreadCount();

    /**
     * Returns the current number of live platform threads that are daemon threads.
     * The count does not include virtual threads.
     *
     * @return the current number of live platform threads that are daemon threads.
     */
    public int getDaemonThreadCount();

    /**
     * Returns the threadIDs of all live platform threads.
     * The thread IDs of virtual threads are not included.
     * Some threads included in the returned array
     * may have been terminated when this method returns.
     *
     * @return an array of {@code long}, each is a thread ID.
     */
    public long[] getAllThreadIds();

    /**
     * Returns the thread info for a thread of the specified
     * {@code id} with no stack trace.
     * This method is equivalent to calling:
     * <blockquote>
     *   {@link #getThreadInfo(long, int) getThreadInfo(id, 0);}
     * </blockquote>
     *
     * <p>
     * This method returns a {@code ThreadInfo} object representing
     * the thread information for the thread of the specified ID.
     * The stack trace, locked monitors, and locked synchronizers
     * in the returned {@code ThreadInfo} object will
     * be empty.
     *
     * If a thread of the given ID is a virtual thread, is not alive, or does
     * not exist, then this method will return {@code null}. A thread is
     * alive if it has been started and has not yet terminated.
     *
     * <p>
     * <b>MBeanServer access</b>:<br>
     * The mapped type of {@code ThreadInfo} is
     * {@code CompositeData} with attributes as specified in the
     * {@link ThreadInfo#from ThreadInfo.from} method.
     *
     * @param id the thread ID of the thread. Must be positive.
     *
     * @return a {@link ThreadInfo} object for the thread of the given ID
     * with no stack trace, no locked monitor and no synchronizer info;
     * {@code null} if the thread of the given ID is a virtual thread,
     * is not alive, or it does not exist.
     *
     * @throws IllegalArgumentException if {@code id <= 0}.
     */
    public ThreadInfo getThreadInfo(long id);

    /**
     * Returns the thread info for each thread
     * whose ID is in the input array {@code ids} with no stack trace.
     * This method is equivalent to calling:
     * <blockquote><pre>
     *   {@link #getThreadInfo(long[], int) getThreadInfo}(ids, 0);
     * </pre></blockquote>
     *
     * <p>
     * This method returns an array of the {@code ThreadInfo} objects.
     * The stack trace, locked monitors, and locked synchronizers
     * in each {@code ThreadInfo} object will be empty.
     *
     * If a thread of the given ID is a virtual thread, is not alive, or does
     * not exist, the corresponding element in the returned array will
     * contain {@code null}.  A thread is alive if
     * it has been started and has not yet terminated.
     *
     * <p>
     * <b>MBeanServer access</b>:<br>
     * The mapped type of {@code ThreadInfo} is
     * {@code CompositeData} with attributes as specified in the
     * {@link ThreadInfo#from ThreadInfo.from} method.
     *
     * @param ids an array of thread IDs.
     * @return an array of the {@link ThreadInfo} objects, each containing
     * information about a thread whose ID is in the corresponding
     * element of the input array of IDs
     * with no stack trace, no locked monitor and no synchronizer info.
     *
     * @throws IllegalArgumentException if any element in the input array
     *         {@code ids} is {@code <= 0}.
     */
    public ThreadInfo[] getThreadInfo(long[] ids);

    /**
     * Returns a thread info for a thread of the specified {@code id},
     * with stack trace of a specified number of stack trace elements.
     * The {@code maxDepth} parameter indicates the maximum number of
     * {@link StackTraceElement} to be retrieved from the stack trace.
     * If {@code maxDepth == Integer.MAX_VALUE}, the entire stack trace of
     * the thread will be dumped.
     * If {@code maxDepth == 0}, no stack trace of the thread
     * will be dumped.
     * This method does not obtain the locked monitors and locked
     * synchronizers of the thread.
     * <p>
     * When the Java virtual machine has no stack trace information
     * about a thread or {@code maxDepth == 0},
     * the stack trace in the
     * {@code ThreadInfo} object will be an empty array of
     * {@code StackTraceElement}.
     *
     * <p>
     * If a thread of the given ID is a virtual thread, is not alive, or does
     * not exist, this method will return {@code null}. A thread is alive if
     * it has been started and has not yet terminated.
     *
     * <p>
     * <b>MBeanServer access</b>:<br>
     * The mapped type of {@code ThreadInfo} is
     * {@code CompositeData} with attributes as specified in the
     * {@link ThreadInfo#from ThreadInfo.from} method.
     *
     * @param id the thread ID of the thread. Must be positive.
     * @param maxDepth the maximum number of entries in the stack trace
     * to be dumped. {@code Integer.MAX_VALUE} could be used to request
     * the entire stack to be dumped.
     *
     * @return a {@link ThreadInfo} of the thread of the given ID
     * with no locked monitor and synchronizer info.
     * {@code null} if the thread of the given ID is a virtual thread, is
     * not alive or it does not exist.
     *
     * @throws IllegalArgumentException if {@code id <= 0}.
     * @throws IllegalArgumentException if {@code maxDepth is negative}.
     */
    public ThreadInfo getThreadInfo(long id, int maxDepth);

    /**
     * Returns the thread info for each thread
     * whose ID is in the input array {@code ids},
     * with stack trace of a specified number of stack trace elements.
     * The {@code maxDepth} parameter indicates the maximum number of
     * {@link StackTraceElement} to be retrieved from the stack trace.
     * If {@code maxDepth == Integer.MAX_VALUE}, the entire stack trace of
     * the thread will be dumped.
     * If {@code maxDepth == 0}, no stack trace of the thread
     * will be dumped.
     * This method does not obtain the locked monitors and locked
     * synchronizers of the threads.
     * <p>
     * When the Java virtual machine has no stack trace information
     * about a thread or {@code maxDepth == 0},
     * the stack trace in the
     * {@code ThreadInfo} object will be an empty array of
     * {@code StackTraceElement}.
     * <p>
     * This method returns an array of the {@code ThreadInfo} objects,
     * each is the thread information about the thread with the same index
     * as in the {@code ids} array.
     * If a thread of the given ID is a virtual thread, is not alive, or does
     * not exist, {@code null} will be set in the corresponding element
     * in the returned array.  A thread is alive if
     * it has been started and has not yet terminated.
     *
     * <p>
     * <b>MBeanServer access</b>:<br>
     * The mapped type of {@code ThreadInfo} is
     * {@code CompositeData} with attributes as specified in the
     * {@link ThreadInfo#from ThreadInfo.from} method.
     *
     * @param ids an array of thread IDs
     * @param maxDepth the maximum number of entries in the stack trace
     * to be dumped. {@code Integer.MAX_VALUE} could be used to request
     * the entire stack to be dumped.
     *
     * @return an array of the {@link ThreadInfo} objects, each containing
     * information about a thread whose ID is in the corresponding
     * element of the input array of IDs with no locked monitor and
     * synchronizer info.
     *
     * @throws IllegalArgumentException if {@code maxDepth is negative}.
     * @throws IllegalArgumentException if any element in the input array
     *      {@code ids} is {@code <= 0}.
     */
    public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth);

    /**
     * Tests if the Java virtual machine supports thread contention monitoring.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine supports thread contention monitoring;
     *   {@code false} otherwise.
     */
    public boolean isThreadContentionMonitoringSupported();

    /**
     * Tests if thread contention monitoring is enabled.
     *
     * @return {@code true} if thread contention monitoring is enabled;
     *         {@code false} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual
     * machine does not support thread contention monitoring.
     *
     * @see #isThreadContentionMonitoringSupported
     */
    public boolean isThreadContentionMonitoringEnabled();

    /**
     * Enables or disables thread contention monitoring.
     * Thread contention monitoring is disabled by default.
     *
     * @param enable {@code true} to enable;
     *               {@code false} to disable.
     *
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support thread contention monitoring.
     *
     * @see #isThreadContentionMonitoringSupported
     */
    public void setThreadContentionMonitoringEnabled(boolean enable);

    /**
     * Returns the total CPU time for the current thread in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     * If the implementation distinguishes between user mode time and system
     * mode time, the returned CPU time is the amount of time that
     * the current thread has executed in user mode or system mode.
     *
     * <p>
     * This is a convenience method for local management use and is
     * equivalent to calling:
     * <blockquote><pre>
     *   {@link #getThreadCpuTime getThreadCpuTime}(Thread.currentThread().threadId());
     * </pre></blockquote>
     *
     * @return the total CPU time for the current thread if the current
     * thread is a platform thread and if CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support CPU time measurement for
     * the current thread.
     *
     * @see #getCurrentThreadUserTime
     * @see #isCurrentThreadCpuTimeSupported
     * @see #isThreadCpuTimeEnabled
     * @see #setThreadCpuTimeEnabled
     */
    public long getCurrentThreadCpuTime();

    /**
     * Returns the CPU time that the current thread has executed
     * in user mode in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     *
     * <p>
     * This is a convenience method for local management use and is
     * equivalent to calling:
     * <blockquote><pre>
     *   {@link #getThreadUserTime getThreadUserTime}(Thread.currentThread().threadId());
     * </pre></blockquote>
     *
     * @return the user-level CPU time for the current thread if the current
     * thread is a platform thread and if CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support CPU time measurement for
     * the current thread.
     *
     * @see #getCurrentThreadCpuTime
     * @see #isCurrentThreadCpuTimeSupported
     * @see #isThreadCpuTimeEnabled
     * @see #setThreadCpuTimeEnabled
     */
    public long getCurrentThreadUserTime();

    /**
     * Returns the total CPU time for a thread of the specified ID in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     * If the implementation distinguishes between user mode time and system
     * mode time, the returned CPU time is the amount of time that
     * the thread has executed in user mode or system mode.
     *
     * <p>
     * If the thread of the specified ID is a virtual thread, is not alive or
     * does not exist, this method returns {@code -1}. If CPU time measurement
     * is disabled, this method returns {@code -1}.
     * A thread is alive if it has been started and has not yet terminated.
     * <p>
     * If CPU time measurement is enabled after the thread has started,
     * the Java virtual machine implementation may choose any time up to
     * and including the time that the capability is enabled as the point
     * where CPU time measurement starts.
     *
     * @param id the thread ID of a thread
     * @return the total CPU time for a thread of the specified ID if the
     * thread of the specified ID is a platform thread, the thread is alive,
     * and CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws IllegalArgumentException if {@code id <= 0}.
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support CPU time measurement for
     * other threads.
     *
     * @see #getThreadUserTime
     * @see #isThreadCpuTimeSupported
     * @see #isThreadCpuTimeEnabled
     * @see #setThreadCpuTimeEnabled
     */
    public long getThreadCpuTime(long id);

    /**
     * Returns the CPU time that a thread of the specified ID
     * has executed in user mode in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     *
     * <p>
     * If the thread of the specified ID is a virtual thread, is not alive, or
     * does not exist, this method returns {@code -1}. If CPU time measurement
     * is disabled, this method returns {@code -1}.
     * A thread is alive if it has been started and has not yet terminated.
     * <p>
     * If CPU time measurement is enabled after the thread has started,
     * the Java virtual machine implementation may choose any time up to
     * and including the time that the capability is enabled as the point
     * where CPU time measurement starts.
     *
     * @param id the thread ID of a thread
     * @return the user-level CPU time for a thread of the specified ID if the
     * thread of the specified ID is a platform thread, the thread is alive,
     * and CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws IllegalArgumentException if {@code id <= 0}.
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support CPU time measurement for
     * other threads.
     *
     * @see #getThreadCpuTime
     * @see #isThreadCpuTimeSupported
     * @see #isThreadCpuTimeEnabled
     * @see #setThreadCpuTimeEnabled
     */
    public long getThreadUserTime(long id);

    /**
     * Tests if the Java virtual machine implementation supports CPU time
     * measurement for any platform thread.
     * A Java virtual machine implementation that supports CPU time
     * measurement for any platform thread will also support CPU time
     * measurement for the current thread, when the current thread is a
     * platform thread.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine supports CPU time
     *     measurement for any platform thread;
     *   {@code false} otherwise.
     */
    public boolean isThreadCpuTimeSupported();

    /**
     * Tests if the Java virtual machine supports CPU time measurement from
     * a platform thread with the {@link #getCurrentThreadCpuTime()} and
     * {@link #getCurrentThreadUserTime()} methods.
     * This method returns {@code true} if {@link #isThreadCpuTimeSupported}
     * returns {@code true}.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine supports CPU time measurement
     *     of the current platform thread;
     *   {@code false} otherwise.
     */
    public boolean isCurrentThreadCpuTimeSupported();

    /**
     * Tests if thread CPU time measurement is enabled.
     *
     * @return {@code true} if thread CPU time measurement is enabled;
     *         {@code false} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual
     * machine does not support CPU time measurement for other threads
     * nor for the current thread.
     *
     * @see #isThreadCpuTimeSupported
     * @see #isCurrentThreadCpuTimeSupported
     */
    public boolean isThreadCpuTimeEnabled();

    /**
     * Enables or disables thread CPU time measurement.  The default
     * is platform dependent.
     *
     * @param enable {@code true} to enable;
     *               {@code false} to disable.
     *
     * @throws UnsupportedOperationException if the Java
     * virtual machine does not support CPU time measurement for
     * any threads nor for the current thread.
     *
     * @see #isThreadCpuTimeSupported
     * @see #isCurrentThreadCpuTimeSupported
     */
    public void setThreadCpuTimeEnabled(boolean enable);

    /**
     * Finds cycles of platform threads that are in deadlock waiting to acquire
     * object monitors. That is, platform threads that are blocked waiting to
     * enter a synchronization block or waiting to reenter a synchronization block
     * after an {@link Object#wait Object.wait} call, where each platform thread
     * owns one monitor while trying to obtain another monitor already held by
     * another platform thread in a cycle. Cycles that include virtual threads
     * are not found by this method.
     * <p>
     * More formally, a thread is <em>monitor deadlocked</em> if it is
     * part of a cycle in the relation "is waiting for an object monitor
     * owned by".  In the simplest case, thread A is blocked waiting
     * for a monitor owned by thread B, and thread B is blocked waiting
     * for a monitor owned by thread A.
     * <p>
     * This method is designed for troubleshooting use, but not for
     * synchronization control.  It might be an expensive operation.
     * <p>
     * This method finds deadlocks involving only object monitors.
     * To find deadlocks involving both object monitors and
     * <a href="LockInfo.html#OwnableSynchronizer">ownable synchronizers</a>,
     * the {@link #findDeadlockedThreads findDeadlockedThreads} method
     * should be used.
     *
     * @return an array of IDs of the platform threads that are monitor
     * deadlocked, if any; {@code null} otherwise.
     *
     * @see #findDeadlockedThreads
     */
    public long[] findMonitorDeadlockedThreads();

    /**
     * Resets the peak thread count to the current number of
     * live platform threads.
     *
     * @see #getPeakThreadCount
     * @see #getThreadCount
     */
    public void resetPeakThreadCount();

    /**
     * Finds cycles of platform threads that are in deadlock waiting to
     * acquire object monitors or
     * <a href="LockInfo.html#OwnableSynchronizer">ownable synchronizers</a>.
     * Platform threads are <em>deadlocked</em> in a cycle waiting for a lock of
     * these two types if each thread owns one lock while trying to acquire
     * another lock already held by another platform thread in the cycle.
     * Cycles that include virtual threads are not found by this method.
     * <p>
     * This method is designed for troubleshooting use, but not for
     * synchronization control.  It might be an expensive operation.
     *
     * @return an array of IDs of the platform threads that are
     * deadlocked waiting for object monitors or ownable synchronizers, if any;
     * {@code null} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual
     * machine does not support monitoring of ownable synchronizer usage.
     *
     * @see #isSynchronizerUsageSupported
     * @see #findMonitorDeadlockedThreads
     * @since 1.6
     */
    public long[] findDeadlockedThreads();

    /**
     * Tests if the Java virtual machine supports monitoring of
     * object monitor usage.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine supports monitoring of
     *     object monitor usage;
     *   {@code false} otherwise.
     *
     * @see #dumpAllThreads
     * @since 1.6
     */
    public boolean isObjectMonitorUsageSupported();

    /**
     * Tests if the Java virtual machine supports monitoring of
     * <a href="LockInfo.html#OwnableSynchronizer">
     * ownable synchronizer</a> usage.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine supports monitoring of ownable
     *     synchronizer usage;
     *   {@code false} otherwise.
     *
     * @see #dumpAllThreads
     * @since 1.6
     */
    public boolean isSynchronizerUsageSupported();

    /**
     * Returns the thread info for each thread
     * whose ID is in the input array {@code ids},
     * with stack trace and synchronization information.
     * This is equivalent to calling:
     * <blockquote>
     * {@link #getThreadInfo(long[], boolean, boolean, int)
     * getThreadInfo(ids, lockedMonitors, lockedSynchronizers, Integer.MAX_VALUE)}
     * </blockquote>
     *
     * @param  ids an array of thread IDs.
     * @param  lockedMonitors if {@code true}, retrieves all locked monitors.
     * @param  lockedSynchronizers if {@code true}, retrieves all locked
     *             ownable synchronizers.
     *
     * @return an array of the {@link ThreadInfo} objects, each containing
     * information about a thread whose ID is in the corresponding
     * element of the input array of IDs.
     *
     * @throws UnsupportedOperationException
     *         <ul>
     *           <li>if {@code lockedMonitors} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isObjectMonitorUsageSupported
     *               object monitor usage}; or</li>
     *           <li>if {@code lockedSynchronizers} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isSynchronizerUsageSupported
     *               ownable synchronizer usage}.</li>
     *         </ul>
     *
     * @see #isObjectMonitorUsageSupported
     * @see #isSynchronizerUsageSupported
     *
     * @since 1.6
     */
    public ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors,
                                      boolean lockedSynchronizers);

    /**
     * Returns the thread info for each thread whose ID
     * is in the input array {@code ids},
     * with stack trace of the specified maximum number of elements
     * and synchronization information.
     * If {@code maxDepth == 0}, no stack trace of the thread
     * will be dumped.
     *
     * <p>
     * This method obtains a snapshot of the thread information
     * for each thread including:
     * <ul>
     *    <li>stack trace of the specified maximum number of elements,</li>
     *    <li>the object monitors currently locked by the thread
     *        if {@code lockedMonitors} is {@code true}, and</li>
     *    <li>the <a href="LockInfo.html#OwnableSynchronizer">
     *        ownable synchronizers</a> currently locked by the thread
     *        if {@code lockedSynchronizers} is {@code true}.</li>
     * </ul>
     * <p>
     * This method returns an array of the {@code ThreadInfo} objects,
     * each is the thread information about the thread with the same index
     * as in the {@code ids} array.
     * If a thread of the given ID is a virtual thread, is not alive, or does
     * not exist, {@code null} will be set in the corresponding element
     * in the returned array.  A thread is alive if
     * it has been started and has not yet terminated.
     * <p>
     * If a thread does not lock any object monitor or {@code lockedMonitors}
     * is {@code false}, the returned {@code ThreadInfo} object will have an
     * empty {@code MonitorInfo} array.  Similarly, if a thread does not
     * lock any synchronizer or {@code lockedSynchronizers} is {@code false},
     * the returned {@code ThreadInfo} object
     * will have an empty {@code LockInfo} array.
     *
     * <p>
     * When both {@code lockedMonitors} and {@code lockedSynchronizers}
     * parameters are {@code false}, it is equivalent to calling:
     * <blockquote><pre>
     *     {@link #getThreadInfo(long[], int)  getThreadInfo(ids, maxDepth)}
     * </pre></blockquote>
     *
     * <p>
     * This method is designed for troubleshooting use, but not for
     * synchronization control.  It might be an expensive operation.
     *
     * <p>
     * <b>MBeanServer access</b>:<br>
     * The mapped type of {@code ThreadInfo} is
     * {@code CompositeData} with attributes as specified in the
     * {@link ThreadInfo#from ThreadInfo.from} method.
     *
     * @implSpec The default implementation throws
     * {@code UnsupportedOperationException}.
     *
     * @param  ids an array of thread IDs.
     * @param  lockedMonitors if {@code true}, retrieves all locked monitors.
     * @param  lockedSynchronizers if {@code true}, retrieves all locked
     *             ownable synchronizers.
     * @param  maxDepth indicates the maximum number of
     * {@link StackTraceElement} to be retrieved from the stack trace.
     *
     * @return an array of the {@link ThreadInfo} objects, each containing
     * information about a thread whose ID is in the corresponding
     * element of the input array of IDs.
     *
     * @throws IllegalArgumentException if {@code maxDepth} is negative.
     * @throws UnsupportedOperationException
     *         <ul>
     *           <li>if {@code lockedMonitors} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isObjectMonitorUsageSupported
     *               object monitor usage}; or</li>
     *           <li>if {@code lockedSynchronizers} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isSynchronizerUsageSupported
     *               ownable synchronizer usage}.</li>
     *         </ul>
     *
     * @see #isObjectMonitorUsageSupported
     * @see #isSynchronizerUsageSupported
     *
     * @since 10
     */

    public default ThreadInfo[] getThreadInfo(long[] ids, boolean lockedMonitors,
                                              boolean lockedSynchronizers, int maxDepth) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the thread info for all live platform threads with stack trace
     * and synchronization information.
     * The thread IDs of virtual threads are not included.
     * This method is equivalent to calling:
     * <blockquote>
     * {@link #dumpAllThreads(boolean, boolean, int)
     * dumpAllThreads(lockedMonitors, lockedSynchronizers, Integer.MAX_VALUE)}
     * </blockquote>
     *
     * @param  lockedMonitors if {@code true}, dump all locked monitors.
     * @param  lockedSynchronizers if {@code true}, dump all locked
     *             ownable synchronizers.
     *
     * @return an array of {@link ThreadInfo} for all live platform threads.
     *
     * @throws UnsupportedOperationException
     *         <ul>
     *           <li>if {@code lockedMonitors} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isObjectMonitorUsageSupported
     *               object monitor usage}; or</li>
     *           <li>if {@code lockedSynchronizers} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isSynchronizerUsageSupported
     *               ownable synchronizer usage}.</li>
     *         </ul>
     *
     * @see #isObjectMonitorUsageSupported
     * @see #isSynchronizerUsageSupported
     *
     * @since 1.6
     */
    public ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers);


    /**
     * Returns the thread info for all live platform threads
     * with stack trace of the specified maximum number of elements
     * and synchronization information.
     * if {@code maxDepth == 0}, no stack trace of the thread
     * will be dumped.
     * The thread IDs of virtual threads are not included.
     * Some threads included in the returned array
     * may have been terminated when this method returns.
     *
     * <p>
     * This method returns an array of {@link ThreadInfo} objects
     * as specified in the {@link #getThreadInfo(long[], boolean, boolean, int)}
     * method.
     *
     * @implSpec The default implementation throws
     * {@code UnsupportedOperationException}.
     *
     * @param  lockedMonitors if {@code true}, dump all locked monitors.
     * @param  lockedSynchronizers if {@code true}, dump all locked
     *             ownable synchronizers.
     * @param  maxDepth indicates the maximum number of
     * {@link StackTraceElement} to be retrieved from the stack trace.
     *
     * @return an array of {@link ThreadInfo} for all live platform threads.
     *
     * @throws IllegalArgumentException if {@code maxDepth} is negative.
     * @throws UnsupportedOperationException
     *         <ul>
     *           <li>if {@code lockedMonitors} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isObjectMonitorUsageSupported
     *               object monitor usage}; or</li>
     *           <li>if {@code lockedSynchronizers} is {@code true} but
     *               the Java virtual machine does not support monitoring
     *               of {@linkplain #isSynchronizerUsageSupported
     *               ownable synchronizer usage}.</li>
     *         </ul>
     *
     * @see #isObjectMonitorUsageSupported
     * @see #isSynchronizerUsageSupported
     *
     * @since 10
     */
    public default ThreadInfo[] dumpAllThreads(boolean lockedMonitors,
                                               boolean lockedSynchronizers, int maxDepth) {
        throw new UnsupportedOperationException();
    }
}
