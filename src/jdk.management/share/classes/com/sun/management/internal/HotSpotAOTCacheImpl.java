/*
 * Copyright (c) 2025, Microsoft, Inc. All rights reserved.
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

import javax.management.ObjectName;
import jdk.management.HotSpotAOTCacheMXBean;
import sun.management.Util;
import sun.management.VMManagement;

/**
 * Implementation class for the AOT Cache subsystem.
 *
 * ManagementFactory.getRuntimeMXBean() returns an instance
 * of this class.
 */
public class HotSpotAOTCacheImpl implements HotSpotAOTCacheMXBean {

    private final VMManagement jvm;
    /**
     * Constructor of HotSpotAOTCacheImpl class.
     */
    HotSpotAOTCacheImpl(VMManagement vm) {
        this.jvm = vm;
    }

    public boolean endRecording() {
        return jvm.endAOTRecording();
    }

    public ObjectName getObjectName() {
        return Util.newObjectName("jdk.management:type=HotSpotAOTCache");
    }
}