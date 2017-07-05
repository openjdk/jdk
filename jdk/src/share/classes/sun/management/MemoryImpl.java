/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import javax.management.ObjectName;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ListenerNotFoundException;
import javax.management.openmbean.CompositeData;
import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Collections;

/**
 * Implementation class for the memory subsystem.
 * Standard and committed hotspot-specific metrics if any.
 *
 * ManagementFactory.getMemoryMXBean() returns an instance
 * of this class.
 */
class MemoryImpl extends NotificationEmitterSupport
                 implements MemoryMXBean {

    private final VMManagement jvm;

    private static MemoryPoolMXBean[] pools = null;
    private static MemoryManagerMXBean[] mgrs = null;

    /**
     * Constructor of MemoryImpl class
     */
    MemoryImpl(VMManagement vm) {
        this.jvm = vm;
    }

    public int getObjectPendingFinalizationCount() {
        return sun.misc.VM.getFinalRefCount();
    }

    public void gc() {
        Runtime.getRuntime().gc();
    }

    // Need to make a VM call to get coherent value
    public MemoryUsage getHeapMemoryUsage() {
        return getMemoryUsage0(true);
    }

    public MemoryUsage getNonHeapMemoryUsage() {
        return getMemoryUsage0(false);
    }

    public boolean isVerbose() {
        return jvm.getVerboseGC();
    }

    public void setVerbose(boolean value) {
        Util.checkControlAccess();

        setVerboseGC(value);
    }

    // The current Hotspot implementation does not support
    // dynamically add or remove memory pools & managers.
    static synchronized MemoryPoolMXBean[] getMemoryPools() {
        if (pools == null) {
            pools = getMemoryPools0();
        }
        return pools;
    }
    static synchronized MemoryManagerMXBean[] getMemoryManagers() {
        if (mgrs == null) {
            mgrs = getMemoryManagers0();
        }
        return mgrs;
    }
    private static native MemoryPoolMXBean[] getMemoryPools0();
    private static native MemoryManagerMXBean[] getMemoryManagers0();
    private native MemoryUsage getMemoryUsage0(boolean heap);
    private native void setVerboseGC(boolean value);

    private final static String notifName =
        "javax.management.Notification";
    private final static String[] notifTypes = {
        MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED,
        MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED
    };
    private final static String[] notifMsgs  = {
        "Memory usage exceeds usage threshold",
        "Memory usage exceeds collection usage threshold"
    };

    private MBeanNotificationInfo[] notifInfo = null;
    public MBeanNotificationInfo[] getNotificationInfo() {
        synchronized (this) {
            if (notifInfo == null) {
                 notifInfo = new MBeanNotificationInfo[1];
                 notifInfo[0] = new MBeanNotificationInfo(notifTypes,
                                                          notifName,
                                                          "Memory Notification");
            }
        }
        return notifInfo;
    }

    private static String getNotifMsg(String notifType) {
        for (int i = 0; i < notifTypes.length; i++) {
            if (notifType == notifTypes[i]) {
                return notifMsgs[i];
            }
        }
        return "Unknown message";
    }

    private static long seqNumber = 0;
    private static long getNextSeqNumber() {
        return ++seqNumber;
    }

    static void createNotification(String notifType,
                                   String poolName,
                                   MemoryUsage usage,
                                   long count) {
        MemoryImpl mbean = (MemoryImpl) ManagementFactory.getMemoryMXBean();
        if (!mbean.hasListeners()) {
            // if no listener is registered.
            return;
        }
        long timestamp = System.currentTimeMillis();
        String msg = getNotifMsg(notifType);
        Notification notif = new Notification(notifType,
                                              mbean.getObjectName(),
                                              getNextSeqNumber(),
                                              timestamp,
                                              msg);
        MemoryNotificationInfo info =
            new MemoryNotificationInfo(poolName,
                                       usage,
                                       count);
        CompositeData cd =
            MemoryNotifInfoCompositeData.toCompositeData(info);
        notif.setUserData(cd);
        mbean.sendNotification(notif);
    }

    public ObjectName getObjectName() {
        return ObjectName.valueOf(ManagementFactory.MEMORY_MXBEAN_NAME);
    }

}
