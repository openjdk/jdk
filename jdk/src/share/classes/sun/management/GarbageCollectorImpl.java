/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

import com.sun.management.GcInfo;
import javax.management.openmbean.CompositeData;
import javax.management.MBeanInfo;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;

import java.util.List;
import java.util.ListIterator;

/**
 * Implementation class for the garbage collector.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getGarbageCollectorMXBeans() returns a list
 * of instances of this class.
 */
class GarbageCollectorImpl extends MemoryManagerImpl
    implements GarbageCollectorMXBean {

    GarbageCollectorImpl(String name) {
        super(name);
    }

    public native long getCollectionCount();
    public native long getCollectionTime();


    // The memory pools are static and won't be changed.
    // TODO: If the hotspot implementation begins to have pools
    // dynamically created and removed, this needs to be modified.
    private String[] poolNames = null;
    synchronized String[] getAllPoolNames() {
        if (poolNames == null) {
            List pools = ManagementFactory.getMemoryPoolMXBeans();
            poolNames = new String[pools.size()];
            int i = 0;
            for (ListIterator iter = pools.listIterator();
                 iter.hasNext();
                 i++) {
                MemoryPoolMXBean p = (MemoryPoolMXBean) iter.next();
                poolNames[i] = p.getName();
            }
        }
        return poolNames;
    }

    // Sun JDK extension
    private GcInfoBuilder gcInfoBuilder;
    public GcInfo getLastGcInfo() {
        synchronized (this) {
            if (gcInfoBuilder == null) {
                 gcInfoBuilder = new GcInfoBuilder(this, getAllPoolNames());
            }
        }

        GcInfo info = gcInfoBuilder.getLastGcInfo();
        return info;
    }

    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
    }

}
