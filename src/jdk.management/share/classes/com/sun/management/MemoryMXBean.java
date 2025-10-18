/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Platform-specific management interface for the memory
 * system on which the Java virtual machine is running.
 *
 * @since 26
 */

public interface MemoryMXBean extends java.lang.management.MemoryMXBean {
    /**
     * Returns the CPU time used by garbage collection.
     *
     * <p> CPU time used by all garbage collection. In
     * general this includes time for all driver threads,
     * workers, VM operations on the VM thread and the string
     * deduplication thread (if enabled). May be non-zero even if no
     * GC cycle occurred. This method returns {@code -1} if the
     * platform does not support this operation or if called during
     * shutdown.
     *
     * @return the total accumulated CPU time for garbage collection
     * in nanoseconds.
     *
     * @since 26
     */
    public long getTotalGcCpuTime();
}
