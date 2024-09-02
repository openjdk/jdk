/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.management;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Management interface for the JDK's default {@linkplain Thread##virtual-threads virtual
 * thread} scheduler.
 *
 * <p> {@code VirtualThreadSchedulerMXBean} supports monitoring of the virtual thread
 * scheduler's target parallelism and the {@linkplain Thread##platform-threads platform
 * threads} used by the virtual thread scheduler as <em>carrier threads</em>. It also
 * supports dynamically changing the scheduler's target parallelism.
 *
 * <p> The management interface is registered with the platform {@link MBeanServer
 * MBeanServer}. The {@link ObjectName ObjectName} that uniquely identifies the management
 * interface within the {@code MBeanServer} is: "jdk.management:type=VirtualThreadScheduler".
 *
 * <p> Direct access to the MXBean interface can be obtained with
 * {@link ManagementFactory#getPlatformMXBean(Class)}.
 *
 * @since 24
 */
public interface VirtualThreadSchedulerMXBean extends PlatformManagedObject {

    /**
     * {@return the scheduler's target parallelism}
     *
     * @see java.util.concurrent.ForkJoinPool#getParallelism()
     */
    int getParallelism();

    /**
     * Sets the scheduler's target parallelism.
     *
     * @param size the target parallelism level
     * @throws IllegalArgumentException if size is less than the minimum, or
     *         greater than the maximum, supported by the scheduler
     * @throws UnsupportedOperationException if changing the target
     *         parallelism is not suppored by the scheduler
     *
     * @see java.util.concurrent.ForkJoinPool#setParallelism(int)
     */
    void setParallelism(int size);

    /**
     * {@return the current number of platform threads in the scheduler's pool;
     * {@code -1} if not known}
     *
     * @apiNote The number of threads may be greater than the scheduler's target
     * parallelism.
     */
    int getThreadCount();

    /**
     * {@return an estimate of the number of platform threads currently used by
     * the scheduler as carriers for virtual threads; {@code -1} if not known}
     */
    int getCarrierThreadCount();

    /**
     * {@return an estimate of the number of virtual threads that are queued to
     * the scheduler to start or continue execution; {@code -1} if not known}
     */
    long getQueuedVirtualThreadCount();
}