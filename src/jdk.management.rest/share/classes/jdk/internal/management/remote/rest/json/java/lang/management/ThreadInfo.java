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

package jdk.internal.management.remote.rest.json.java.lang.management;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONObject;

public class ThreadInfo extends java.lang.management.ThreadInfo {

    // Definitions in ManagementFactoryHelper.java but private:
    // These values are defined in jmm.h
    private static final int JMM_THREAD_STATE_FLAG_MASK = 0xFFF00000;
    private static final int JMM_THREAD_STATE_FLAG_SUSPENDED = 0x00100000;
    private static final int JMM_THREAD_STATE_FLAG_NATIVE = 0x00400000;

    protected ThreadInfo(long threadId, String threadName, boolean daemon, int priority,
                         int state, LockInfo lockInfo,
                         long lockOwnerId, String lockOwnerName,
                         long blockedCount, long blockedTime,
                         long waitedCount, long waitedTime,
                         StackTraceElement[] stackTrace) {

        super(threadId, threadName, daemon, priority,
                   state, lockInfo, lockOwnerId, lockOwnerName,
                   blockedCount, blockedTime,
                   waitedCount, waitedTime, stackTrace);
//                    new MonitorInfo[0], new LockInfo[0]); // EMPTY_MONITORS, EMPTY_SYNCS);
    }
 
    public static ThreadInfo from(JSONObject json) {
        if (json == null) {
            return null;
        }
        long         threadId     = JSONObject.getObjectFieldLong(json, "threadId");
        String       threadName   = JSONObject.getObjectFieldString(json, "threadName");
        boolean      daemon       = JSONObject.getObjectFieldBoolean(json, "daemon");
        int          priority     = (int) JSONObject.getObjectFieldLong(json, "priority");

        long         blockedTime  = JSONObject.getObjectFieldLong(json, "blockedTime");
        long         blockedCount = JSONObject.getObjectFieldLong(json, "blockedCount");
        long         waitedTime   = JSONObject.getObjectFieldLong(json, "waitedTime");
        long         waitedCount  = JSONObject.getObjectFieldLong(json, "waitedCount");
        LockInfo     lockInfo     = jdk.internal.management.remote.rest.json.java.lang.management.LockInfo.from((JSONObject) json.get("lockInfo"));
        String       lockName     = JSONObject.getObjectFieldString(json, "lockName");
        long         lockOwnerId  = JSONObject.getObjectFieldLong(json, "lockOwnerId");
        String       lockOwnerName= JSONObject.getObjectFieldString(json, "lockOwnerName");

        // Thread State, plus isSuspended and inNative flags.
        int state = 0;
        boolean      inNative     = JSONObject.getObjectFieldBoolean(json, "inNative");
        boolean      suspended    = JSONObject.getObjectFieldBoolean(json, "suspended");
        if (inNative) {
            state |= JMM_THREAD_STATE_FLAG_NATIVE;
        }
        if (suspended) {
            state |= JMM_THREAD_STATE_FLAG_SUSPENDED;
        }
        try {
            String s = JSONObject.getObjectFieldString(json, "threadState"); // e.g. RUNNABLE
            Thread.State threadState  = Thread.State.valueOf(Thread.State.class, s);
            state |= threadState.ordinal();
        } catch (IllegalArgumentException iae) {

        }

        StackTraceElement[] stackTrace =  null;
        JSONArray stackTraceJSON = JSONObject.getObjectFieldArray(json, "stackTrace");
        if (stackTraceJSON != null) {
            stackTrace = new StackTraceElement[stackTraceJSON.size()];
            for (int i=0; i< stackTraceJSON.size(); i++) {
                    JSONObject e = (JSONObject) stackTraceJSON.get(i);
                    String classLoaderName = JSONObject.getObjectFieldString(e, "classLoaderName");
                    String className = JSONObject.getObjectFieldString(e, "className");
                    String fileName = JSONObject.getObjectFieldString(e, "fileName");
                    int lineNumber = (int) JSONObject.getObjectFieldLong(e, "lineNumber");
                    String methodName = JSONObject.getObjectFieldString(e, "methodName");
                    String moduleName = JSONObject.getObjectFieldString(e, "moduleName");
                    String moduleVersion = JSONObject.getObjectFieldString(e, "moduleVersion");
                    boolean nativeMethod = JSONObject.getObjectFieldBoolean(e, "nativeMethod");
                    stackTrace[i] = new StackTraceElement(classLoaderName, moduleName, moduleVersion,
                        className, methodName, fileName, lineNumber);
            }
        }

        MonitorInfo[]       lockedMonitors = new MonitorInfo[0];
        LockInfo[]          lockedSynchronizers =  new LockInfo[0];

        ThreadInfo x = new ThreadInfo(threadId, threadName, daemon, priority,
                              state, lockInfo, lockOwnerId, lockOwnerName,
                              blockedCount, blockedTime,
                              waitedCount, waitedTime, stackTrace);
//                              lockedMonitors, lockedSynchronizers);

        return x;
    }
}

