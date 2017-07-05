/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.management;

import java.util.List;
import java.util.ArrayList;
import sun.management.counter.Counter;


/**
 * Implementation class of HotspotRuntimeMBean interface.
 *
 * Internal, uncommitted management interface for Hotspot runtime
 * system.
 */
class HotspotRuntime
    implements HotspotRuntimeMBean {

    private VMManagement jvm;

    /**
     * Constructor of HotspotRuntime class.
     */
    HotspotRuntime(VMManagement vm) {
        jvm = vm;
    }

    public long getSafepointCount() {
        return jvm.getSafepointCount();
    }

    public long getTotalSafepointTime() {
        return jvm.getTotalSafepointTime();
    }

    public long getSafepointSyncTime() {
        return jvm.getSafepointSyncTime();
    }

    // Performance counter support
    private static final String JAVA_RT          = "java.rt.";
    private static final String COM_SUN_RT       = "com.sun.rt.";
    private static final String SUN_RT           = "sun.rt.";
    private static final String JAVA_PROPERTY    = "java.property.";
    private static final String COM_SUN_PROPERTY = "com.sun.property.";
    private static final String SUN_PROPERTY     = "sun.property.";
    private static final String RT_COUNTER_NAME_PATTERN =
        JAVA_RT + "|" + COM_SUN_RT + "|" + SUN_RT + "|" +
        JAVA_PROPERTY + "|" + COM_SUN_PROPERTY + "|" + SUN_PROPERTY;

    public java.util.List<Counter> getInternalRuntimeCounters() {
        return jvm.getInternalCounters(RT_COUNTER_NAME_PATTERN);
    }
}
