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

package com.sun.management.internal;

import sun.management.BaseOperatingSystemImpl;
import sun.management.VMManagement;
/**
 * Implementation class for the operating system.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getOperatingSystemMXBean() returns an instance
 * of this class.
 */
class OperatingSystemImpl extends BaseOperatingSystemImpl
    implements com.sun.management.UnixOperatingSystemMXBean {

    OperatingSystemImpl(VMManagement vm) {
        super(vm);
    }

    public long getCommittedVirtualMemorySize() {
        return getCommittedVirtualMemorySize0();
    }

    public long getTotalSwapSpaceSize() {
        return getTotalSwapSpaceSize0();
    }

    public long getFreeSwapSpaceSize() {
        return getFreeSwapSpaceSize0();
    }

    public long getProcessCpuTime() {
        return getProcessCpuTime0();
    }

    public long getFreePhysicalMemorySize() {
        return getFreePhysicalMemorySize0();
    }

    public long getTotalPhysicalMemorySize() {
        return getTotalPhysicalMemorySize0();
    }

    public long getOpenFileDescriptorCount() {
        return getOpenFileDescriptorCount0();
    }

    public long getMaxFileDescriptorCount() {
        return getMaxFileDescriptorCount0();
    }

    public double getSystemCpuLoad() {
        return getSystemCpuLoad0();
    }

    public double getProcessCpuLoad() {
        return getProcessCpuLoad0();
    }

    /* native methods */
    private native long getCommittedVirtualMemorySize0();
    private native long getFreePhysicalMemorySize0();
    private native long getFreeSwapSpaceSize0();
    private native long getMaxFileDescriptorCount0();
    private native long getOpenFileDescriptorCount0();
    private native long getProcessCpuTime0();
    private native double getProcessCpuLoad0();
    private native double getSystemCpuLoad0();
    private native long getTotalPhysicalMemorySize0();
    private native long getTotalSwapSpaceSize0();

    static {
        initialize0();
    }

    private static native void initialize0();
}
