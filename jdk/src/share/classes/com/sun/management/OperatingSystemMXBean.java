/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.management;

/**
 * Platform-specific management interface for the operating system
 * on which the Java virtual machine is running.
 *
 * <p>
 * The <tt>OperatingSystemMXBean</tt> object returned by
 * {@link java.lang.management.ManagementFactory#getOperatingSystemMXBean()}
 * is an instance of the implementation class of this interface
 * or {@link UnixOperatingSystemMXBean} interface depending on
 * its underlying operating system.
 *
 * @author  Mandy Chung
 * @since   1.5
 */
public interface OperatingSystemMXBean extends
    java.lang.management.OperatingSystemMXBean {

    /**
     * Returns the amount of virtual memory that is guaranteed to
     * be available to the running process in bytes,
     * or <tt>-1</tt> if this operation is not supported.
     *
     * @return the amount of virtual memory that is guaranteed to
     * be available to the running process in bytes,
     * or <tt>-1</tt> if this operation is not supported.
     */
    public long getCommittedVirtualMemorySize();

    /**
     * Returns the total amount of swap space in bytes.
     *
     * @return the total amount of swap space in bytes.
     */
    public long getTotalSwapSpaceSize();

    /**
     * Returns the amount of free swap space in bytes.
     *
     * @return the amount of free swap space in bytes.
     */
    public long getFreeSwapSpaceSize();

    /**
     * Returns the CPU time used by the process on which the Java
     * virtual machine is running in nanoseconds.  The returned value
     * is of nanoseconds precision but not necessarily nanoseconds
     * accuracy.  This method returns <tt>-1</tt> if the
     * the platform does not support this operation.
     *
     * @return the CPU time used by the process in nanoseconds,
     * or <tt>-1</tt> if this operation is not supported.
     */
    public long getProcessCpuTime();

    /**
     * Returns the amount of free physical memory in bytes.
     *
     * @return the amount of free physical memory in bytes.
     */
    public long getFreePhysicalMemorySize();

    /**
     * Returns the total amount of physical memory in bytes.
     *
     * @return the total amount of physical memory in  bytes.
     */
    public long getTotalPhysicalMemorySize();
}
