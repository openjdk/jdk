/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ThreadInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.LockInfo;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;

/**
 * A CompositeData for ThreadInfo for the local management support.
 * This class avoids the performance penalty paid to the
 * construction of a CompositeData use in the local case.
 */
public class ThreadInfoCompositeData extends LazyCompositeData {
    private final ThreadInfo threadInfo;
    private final CompositeData cdata;
    private final boolean currentVersion;

    private ThreadInfoCompositeData(ThreadInfo ti) {
        this.threadInfo = ti;
        this.currentVersion = true;
        this.cdata = null;
    }

    private ThreadInfoCompositeData(CompositeData cd) {
        this.threadInfo = null;
        this.currentVersion = ThreadInfoCompositeData.isCurrentVersion(cd);
        this.cdata = cd;
    }

    public ThreadInfo getThreadInfo() {
        return threadInfo;
    }

    public boolean isCurrentVersion() {
        return currentVersion;
    }

    public static ThreadInfoCompositeData getInstance(CompositeData cd) {
        validateCompositeData(cd);
        return new ThreadInfoCompositeData(cd);
    }

    public static CompositeData toCompositeData(ThreadInfo ti) {
        ThreadInfoCompositeData ticd = new ThreadInfoCompositeData(ti);
        return ticd.getCompositeData();
    }

    protected CompositeData getCompositeData() {
        // Convert StackTraceElement[] to CompositeData[]
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        CompositeData[] stackTraceData =
            new CompositeData[stackTrace.length];
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            stackTraceData[i] = StackTraceElementCompositeData.toCompositeData(ste);
        }

        // Convert MonitorInfo[] and LockInfo[] to CompositeData[]
        CompositeData lockInfoData =
            LockInfoCompositeData.toCompositeData(threadInfo.getLockInfo());

        // Convert LockInfo[] and MonitorInfo[] to CompositeData[]
        LockInfo[] lockedSyncs = threadInfo.getLockedSynchronizers();
        CompositeData[] lockedSyncsData =
            new CompositeData[lockedSyncs.length];
        for (int i = 0; i < lockedSyncs.length; i++) {
            LockInfo li = lockedSyncs[i];
            lockedSyncsData[i] = LockInfoCompositeData.toCompositeData(li);
        }

        MonitorInfo[] lockedMonitors = threadInfo.getLockedMonitors();
        CompositeData[] lockedMonitorsData =
            new CompositeData[lockedMonitors.length];
        for (int i = 0; i < lockedMonitors.length; i++) {
            MonitorInfo mi = lockedMonitors[i];
            lockedMonitorsData[i] = MonitorInfoCompositeData.toCompositeData(mi);
        }

        // CONTENTS OF THIS ARRAY MUST BE SYNCHRONIZED WITH
        // threadInfoItemNames!
        final Object[] threadInfoItemValues = {
            new Long(threadInfo.getThreadId()),
            threadInfo.getThreadName(),
            threadInfo.getThreadState().name(),
            new Long(threadInfo.getBlockedTime()),
            new Long(threadInfo.getBlockedCount()),
            new Long(threadInfo.getWaitedTime()),
            new Long(threadInfo.getWaitedCount()),
            lockInfoData,
            threadInfo.getLockName(),
            new Long(threadInfo.getLockOwnerId()),
            threadInfo.getLockOwnerName(),
            stackTraceData,
            new Boolean(threadInfo.isSuspended()),
            new Boolean(threadInfo.isInNative()),
            lockedMonitorsData,
            lockedSyncsData,
        };

        try {
            return new CompositeDataSupport(threadInfoCompositeType,
                                            threadInfoItemNames,
                                            threadInfoItemValues);
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }
    }

    // Attribute names
    private static final String THREAD_ID       = "threadId";
    private static final String THREAD_NAME     = "threadName";
    private static final String THREAD_STATE    = "threadState";
    private static final String BLOCKED_TIME    = "blockedTime";
    private static final String BLOCKED_COUNT   = "blockedCount";
    private static final String WAITED_TIME     = "waitedTime";
    private static final String WAITED_COUNT    = "waitedCount";
    private static final String LOCK_INFO       = "lockInfo";
    private static final String LOCK_NAME       = "lockName";
    private static final String LOCK_OWNER_ID   = "lockOwnerId";
    private static final String LOCK_OWNER_NAME = "lockOwnerName";
    private static final String STACK_TRACE     = "stackTrace";
    private static final String SUSPENDED       = "suspended";
    private static final String IN_NATIVE       = "inNative";
    private static final String LOCKED_MONITORS = "lockedMonitors";
    private static final String LOCKED_SYNCS    = "lockedSynchronizers";

    private static final String[] threadInfoItemNames = {
        THREAD_ID,
        THREAD_NAME,
        THREAD_STATE,
        BLOCKED_TIME,
        BLOCKED_COUNT,
        WAITED_TIME,
        WAITED_COUNT,
        LOCK_INFO,
        LOCK_NAME,
        LOCK_OWNER_ID,
        LOCK_OWNER_NAME,
        STACK_TRACE,
        SUSPENDED,
        IN_NATIVE,
        LOCKED_MONITORS,
        LOCKED_SYNCS,
    };

    // New attributes added in 6.0 ThreadInfo
    private static final String[] threadInfoV6Attributes = {
        LOCK_INFO,
        LOCKED_MONITORS,
        LOCKED_SYNCS,
    };

    // Current version of ThreadInfo
    private static final CompositeType threadInfoCompositeType;
    // Previous version of ThreadInfo
    private static final CompositeType threadInfoV5CompositeType;
    private static final CompositeType lockInfoCompositeType;
    static {
        try {
            threadInfoCompositeType = (CompositeType)
                MappedMXBeanType.toOpenType(ThreadInfo.class);
            // Form a CompositeType for JDK 5.0 ThreadInfo version
            String[] itemNames =
                threadInfoCompositeType.keySet().toArray(new String[0]);
            int numV5Attributes = threadInfoItemNames.length -
                                      threadInfoV6Attributes.length;
            String[] v5ItemNames = new String[numV5Attributes];
            String[] v5ItemDescs = new String[numV5Attributes];
            OpenType<?>[] v5ItemTypes = new OpenType<?>[numV5Attributes];
            int i = 0;
            for (String n : itemNames) {
                if (isV5Attribute(n)) {
                    v5ItemNames[i] = n;
                    v5ItemDescs[i] = threadInfoCompositeType.getDescription(n);
                    v5ItemTypes[i] = threadInfoCompositeType.getType(n);
                    i++;
                }
            }

            threadInfoV5CompositeType =
                new CompositeType("java.lang.management.ThreadInfo",
                                  "J2SE 5.0 java.lang.management.ThreadInfo",
                                  v5ItemNames,
                                  v5ItemDescs,
                                  v5ItemTypes);
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }

        // Each CompositeData object has its CompositeType associated
        // with it.  So we can get the CompositeType representing LockInfo
        // from a mapped CompositeData for any LockInfo object.
        // Thus we construct a random LockInfo object and pass it
        // to LockInfoCompositeData to do the conversion.
        Object o = new Object();
        LockInfo li = new LockInfo(o.getClass().getName(),
                                   System.identityHashCode(o));
        CompositeData cd = LockInfoCompositeData.toCompositeData(li);
        lockInfoCompositeType = cd.getCompositeType();
    }

    private static boolean isV5Attribute(String itemName) {
        for (String n : threadInfoV6Attributes) {
            if (itemName.equals(n)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCurrentVersion(CompositeData cd) {
        if (cd == null) {
            throw new NullPointerException("Null CompositeData");
        }

        return isTypeMatched(threadInfoCompositeType, cd.getCompositeType());
    }

    public long threadId() {
        return getLong(cdata, THREAD_ID);
    }

    public String threadName() {
        // The ThreadName item cannot be null so we check that
        // it is present with a non-null value.
        String name = getString(cdata, THREAD_NAME);
        if (name == null) {
            throw new IllegalArgumentException("Invalid composite data: " +
                "Attribute " + THREAD_NAME + " has null value");
        }
        return name;
    }

    public Thread.State threadState() {
        return Thread.State.valueOf(getString(cdata, THREAD_STATE));
    }

    public long blockedTime() {
        return getLong(cdata, BLOCKED_TIME);
    }

    public long blockedCount() {
        return getLong(cdata, BLOCKED_COUNT);
    }

    public long waitedTime() {
        return getLong(cdata, WAITED_TIME);
    }

    public long waitedCount() {
        return getLong(cdata, WAITED_COUNT);
    }

    public String lockName() {
        // The LockName and LockOwnerName can legitimately be null,
        // we don't bother to check the value
        return getString(cdata, LOCK_NAME);
    }

    public long lockOwnerId() {
        return getLong(cdata, LOCK_OWNER_ID);
    }

    public String lockOwnerName() {
        return getString(cdata, LOCK_OWNER_NAME);
    }

    public boolean suspended() {
        return getBoolean(cdata, SUSPENDED);
    }

    public boolean inNative() {
        return getBoolean(cdata, IN_NATIVE);
    }

    public StackTraceElement[] stackTrace() {
        CompositeData[] stackTraceData =
            (CompositeData[]) cdata.get(STACK_TRACE);

        // The StackTrace item cannot be null, but if it is we will get
        // a NullPointerException when we ask for its length.
        StackTraceElement[] stackTrace =
            new StackTraceElement[stackTraceData.length];
        for (int i = 0; i < stackTraceData.length; i++) {
            CompositeData cdi = stackTraceData[i];
            stackTrace[i] = StackTraceElementCompositeData.from(cdi);
        }
        return stackTrace;
    }

    // 6.0 new attributes
    public LockInfo lockInfo() {
        CompositeData lockInfoData = (CompositeData) cdata.get(LOCK_INFO);
        return LockInfo.from(lockInfoData);
    }

    public MonitorInfo[] lockedMonitors() {
        CompositeData[] lockedMonitorsData =
            (CompositeData[]) cdata.get(LOCKED_MONITORS);

        // The LockedMonitors item cannot be null, but if it is we will get
        // a NullPointerException when we ask for its length.
        MonitorInfo[] monitors =
            new MonitorInfo[lockedMonitorsData.length];
        for (int i = 0; i < lockedMonitorsData.length; i++) {
            CompositeData cdi = lockedMonitorsData[i];
            monitors[i] = MonitorInfo.from(cdi);
        }
        return monitors;
    }

    public LockInfo[] lockedSynchronizers() {
        CompositeData[] lockedSyncsData =
            (CompositeData[]) cdata.get(LOCKED_SYNCS);

        // The LockedSynchronizers item cannot be null, but if it is we will
        // get a NullPointerException when we ask for its length.
        LockInfo[] locks = new LockInfo[lockedSyncsData.length];
        for (int i = 0; i < lockedSyncsData.length; i++) {
            CompositeData cdi = lockedSyncsData[i];
            locks[i] = LockInfo.from(cdi);
        }
        return locks;
    }

    /** Validate if the input CompositeData has the expected
     * CompositeType (i.e. contain all attributes with expected
     * names and types).
     */
    public static void validateCompositeData(CompositeData cd) {
        if (cd == null) {
            throw new NullPointerException("Null CompositeData");
        }

        CompositeType type = cd.getCompositeType();
        boolean currentVersion = true;
        if (!isTypeMatched(threadInfoCompositeType, type)) {
            currentVersion = false;
            // check if cd is an older version
            if (!isTypeMatched(threadInfoV5CompositeType, type)) {
                throw new IllegalArgumentException(
                    "Unexpected composite type for ThreadInfo");
            }
        }

        CompositeData[] stackTraceData =
            (CompositeData[]) cd.get(STACK_TRACE);
        if (stackTraceData == null) {
            throw new IllegalArgumentException(
                "StackTraceElement[] is missing");
        }
        if (stackTraceData.length > 0) {
            StackTraceElementCompositeData.validateCompositeData(stackTraceData[0]);
        }

        // validate v6 attributes
        if (currentVersion) {
            CompositeData li = (CompositeData) cd.get(LOCK_INFO);
            if (li != null) {
                if (!isTypeMatched(lockInfoCompositeType,
                                   li.getCompositeType())) {
                    throw new IllegalArgumentException(
                        "Unexpected composite type for \"" +
                        LOCK_INFO + "\" attribute.");
                }
            }

            CompositeData[] lms = (CompositeData[]) cd.get(LOCKED_MONITORS);
            if (lms == null) {
                throw new IllegalArgumentException("MonitorInfo[] is null");
            }
            if (lms.length > 0) {
                MonitorInfoCompositeData.validateCompositeData(lms[0]);
            }

            CompositeData[] lsyncs = (CompositeData[]) cd.get(LOCKED_SYNCS);
            if (lsyncs == null) {
                throw new IllegalArgumentException("LockInfo[] is null");
            }
            if (lsyncs.length > 0) {
                if (!isTypeMatched(lockInfoCompositeType,
                                   lsyncs[0].getCompositeType())) {
                    throw new IllegalArgumentException(
                        "Unexpected composite type for \"" +
                        LOCKED_SYNCS + "\" attribute.");
                }
            }

        }
    }

    private static final long serialVersionUID = 2464378539119753175L;
}
