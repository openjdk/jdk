/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug     4982289
 * @summary Test ThreadInfo.from to return a valid
 *          ThreadInfo object. Or throw exception if
 *          the input CompositeData is invalid.
 * @author  Mandy Chung
 *
 * @compile OpenTypeConverter.java
 * @build ThreadInfoCompositeData
 * @run main ThreadInfoCompositeData
 */

import javax.management.openmbean.*;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

public class ThreadInfoCompositeData {
    private static StackTraceElement[] ste = new StackTraceElement[1];
    private static CompositeData[] steCD = new CompositeData[1];
    private static String lockClassName = "myClass";
    private static int lockIdentityHashCode = 123456;
    private static String lockName = lockClassName + '@' +
        Integer.toHexString(lockIdentityHashCode);
    private static LockInfo lockInfo =
        new LockInfo(lockClassName, lockIdentityHashCode);

    public static void main(String[] argv) throws Exception {
        // A valid CompositeData is passed to ThreadInfo
        createGoodCompositeData();
        // A valid CompositeData for JDK 5.0 ThreadInfo
        // is passed to ThreadInfo
        createV5ThreadInfo();
        // An invalid CompositeData is passed to ThreadInfo.from()
        badNameCompositeData();
        badTypeCompositeData();
        System.out.println("Test passed");
    }

    public static void createGoodCompositeData() throws Exception {
        CompositeType ct =
            new CompositeType("MyCompositeType",
                              "CompositeType for ThreadInfo",
                              validItemNames,
                              validItemNames,
                              validItemTypes);
        CompositeData cd =
            new CompositeDataSupport(ct,
                                     validItemNames,
                                     values);
        ThreadInfo info = ThreadInfo.from(cd);
        checkThreadInfo(info);
   }

    public static void createV5ThreadInfo() throws Exception {
        String[] v5ItemNames = new String[NUM_V5_ATTS];
        OpenType[] v5ItemTypes = new OpenType[NUM_V5_ATTS];
        Object[] v5ItemValues = new Object[NUM_V5_ATTS];
        for (int i = 0; i < NUM_V5_ATTS; i++) {
            v5ItemNames[i] = validItemNames[i];
            v5ItemTypes[i] = validItemTypes[i];
            v5ItemValues[i] = values[i];
        }
        CompositeType ct =
            new CompositeType("MyCompositeType",
                              "CompositeType for JDK 5.0 ThreadInfo",
                              v5ItemNames,
                              v5ItemNames,
                              v5ItemTypes);
        CompositeData cd =
            new CompositeDataSupport(ct,
                                     v5ItemNames,
                                     v5ItemValues);
        ThreadInfo info = ThreadInfo.from(cd);
        checkThreadInfo(info);
   }

   static void checkThreadInfo(ThreadInfo info) throws Exception {
        if (info.getThreadId() != ((Long) values[THREAD_ID]).longValue()) {
            throw new RuntimeException("Thread Id = " + info.getThreadId() +
               " expected = " + values[THREAD_ID]);
        }
        if (!info.getThreadName().equals(values[THREAD_NAME])) {
            throw new RuntimeException("Thread Name = " +
               info.getThreadName() + " expected = " + values[THREAD_NAME]);
        }
        if (info.getThreadState() != Thread.State.RUNNABLE) {
            throw new RuntimeException("Thread Name = " +
               info.getThreadName() + " expected = " + Thread.State.RUNNABLE);
        }
        if (info.getBlockedTime() != ((Long) values[BLOCKED_TIME]).longValue()) {
            throw new RuntimeException("blocked time = " +
               info.getBlockedTime() +
               " expected = " + values[BLOCKED_TIME]);
        }
        if (info.getBlockedCount() != ((Long) values[BLOCKED_COUNT]).longValue()) {
            throw new RuntimeException("blocked count = " +
               info.getBlockedCount() +
               " expected = " + values[BLOCKED_COUNT]);
        }
        if (info.getWaitedTime() != ((Long) values[WAITED_TIME]).longValue()) {
            throw new RuntimeException("waited time = " +
               info.getWaitedTime() +
               " expected = " + values[WAITED_TIME]);
        }
        if (info.getWaitedCount() != ((Long) values[WAITED_COUNT]).longValue()) {
            throw new RuntimeException("waited count = " +
               info.getWaitedCount() +
               " expected = " + values[WAITED_COUNT]);
        }
        if (!info.getLockName().equals(values[LOCK_NAME])) {
            throw new RuntimeException("Lock Name = " +
               info.getLockName() + " expected = " + values[LOCK_NAME]);
        }
        if (info.getLockOwnerId() !=
                ((Long) values[LOCK_OWNER_ID]).longValue()) {
            throw new RuntimeException(
               "LockOwner Id = " + info.getLockOwnerId() +
               " expected = " + values[LOCK_OWNER_ID]);
        }
        if (!info.getLockOwnerName().equals(values[LOCK_OWNER_NAME])) {
            throw new RuntimeException("LockOwner Name = " +
               info.getLockOwnerName() + " expected = " +
               values[LOCK_OWNER_NAME]);
        }

        checkStackTrace(info.getStackTrace());

        checkLockInfo(info.getLockInfo());
    }

    private static void checkStackTrace(StackTraceElement[] s)
        throws Exception {
        if (ste.length != s.length) {
            throw new RuntimeException("Stack Trace length = " +
                s.length + " expected = " + ste.length);
        }

        StackTraceElement s1 = ste[0];
        StackTraceElement s2 = s[0];

        if (!s1.getClassName().equals(s2.getClassName())) {
            throw new RuntimeException("Class name = " +
                s2.getClassName() + " expected = " + s1.getClassName());
        }
        if (!s1.getMethodName().equals(s2.getMethodName())) {
            throw new RuntimeException("Method name = " +
                s2.getMethodName() + " expected = " + s1.getMethodName());
        }
        if (!s1.getFileName().equals(s2.getFileName())) {
            throw new RuntimeException("File name = " +
                s2.getFileName() + " expected = " + s1.getFileName());
        }
        if (s1.getLineNumber() != s2.getLineNumber()) {
            throw new RuntimeException("Line number = " +
                s2.getLineNumber() + " expected = " + s1.getLineNumber());
        }
    }

    private static void checkLockInfo(LockInfo li)
        throws Exception {
        if (!li.getClassName().equals(lockInfo.getClassName())) {
            throw new RuntimeException("Class Name = " +
                li.getClassName() + " expected = " + lockInfo.getClassName());
        }
        if (li.getIdentityHashCode() != lockInfo.getIdentityHashCode()) {
            throw new RuntimeException("Class Name = " +
                li.getIdentityHashCode() + " expected = " +
                lockInfo.getIdentityHashCode());
        }
    }

    public static void badNameCompositeData() throws Exception {
        CompositeType ct =
            new CompositeType("MyCompositeType",
                              "CompositeType for ThreadInfo",
                              badItemNames,
                              badItemNames,
                              validItemTypes);
        CompositeData cd =
            new CompositeDataSupport(ct,
                                     badItemNames,
                                     values);

        try {
            ThreadInfo info = ThreadInfo.from(cd);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected exception: " +
                e.getMessage());
            return;
        }
        throw new RuntimeException(
            "IllegalArgumentException not thrown");
    }

    public static void badTypeCompositeData() throws Exception {
        CompositeType ct =
            new CompositeType("MyCompositeType",
                              "CompositeType for ThreadInfo",
                              validItemNames,
                              validItemNames,
                              badItemTypes);

        // patch values[STACK_TRACE] to Long
        values[STACK_TRACE] = new Long(1000);
        values[LOCK_INFO] = new Long(1000);
        CompositeData cd =
            new CompositeDataSupport(ct,
                                     validItemNames,
                                     values);

        try {
            ThreadInfo info = ThreadInfo.from(cd);
        } catch (IllegalArgumentException e) {
            System.out.println("Expected exception: " +
                e.getMessage());
            return;
        }
        throw new RuntimeException(
            "IllegalArgumentException not thrown");
    }

    private static final int THREAD_ID       = 0;
    private static final int THREAD_NAME     = 1;
    private static final int THREAD_STATE    = 2;
    private static final int BLOCKED_TIME    = 3;
    private static final int BLOCKED_COUNT   = 4;
    private static final int WAITED_TIME     = 5;
    private static final int WAITED_COUNT    = 6;
    private static final int LOCK_NAME       = 7;
    private static final int LOCK_OWNER_ID   = 8;
    private static final int LOCK_OWNER_NAME = 9;
    private static final int STACK_TRACE     = 10;
    private static final int SUSPENDED       = 11;
    private static final int IN_NATIVE       = 12;
    private static final int NUM_V5_ATTS     = 13;
    // JDK 6.0 ThreadInfo attribtues
    private static final int LOCK_INFO       = 13;

    private static final String[] validItemNames = {
        "threadId",
        "threadName",
        "threadState",
        "blockedTime",
        "blockedCount",
        "waitedTime",
        "waitedCount",
        "lockName",
        "lockOwnerId",
        "lockOwnerName",
        "stackTrace",
        "suspended",
        "inNative",
        "lockInfo",
    };

    private static OpenType[] validItemTypes = {
        SimpleType.LONG,
        SimpleType.STRING,
        SimpleType.STRING,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.STRING,
        SimpleType.LONG,
        SimpleType.STRING,
        null,  // ArrayType for StackTraceElement[]
        SimpleType.BOOLEAN,
        SimpleType.BOOLEAN,
        null,  // CompositeType for LockInfo
    };

    private static Object[] values = {
        new Long(100),
        "FooThread",
        "RUNNABLE",
        new Long(200),
        new Long(10),
        new Long(300),
        new Long(20),
        lockName,
        new Long(99),
        "BarThread",
        steCD,
        new Boolean(false),
        new Boolean(false),
        null, // To be initialized to lockInfoCD
    };

    private static final String[] steItemNames = {
        "className",
        "methodName",
        "fileName",
        "lineNumber",
        "nativeMethod",
    };

    private static final String[] lockInfoItemNames = {
        "className",
        "identityHashCode",
    };

    static {
        // create stack trace element
        ste[0] = new StackTraceElement("FooClass", "getFoo", "Foo.java", 100);

        // initialize the ste[0] and values and validItemTypes
        try {
            CompositeType steCType = (CompositeType)
                OpenTypeConverter.toOpenType(StackTraceElement.class);
            validItemTypes[STACK_TRACE] = new ArrayType(1, steCType);

            final Object[] steValue = {
                ste[0].getClassName(),
                ste[0].getMethodName(),
                ste[0].getFileName(),
                new Integer(ste[0].getLineNumber()),
                new Boolean(ste[0].isNativeMethod()),
            };

            steCD[0] =
                new CompositeDataSupport(steCType,
                                         steItemNames,
                                         steValue);

            CompositeType lockInfoCType = (CompositeType)
                OpenTypeConverter.toOpenType(LockInfo.class);
            validItemTypes[LOCK_INFO] = lockInfoCType;

            final Object[] lockInfoValue = {
                lockInfo.getClassName(),
                lockInfo.getIdentityHashCode(),
            };

            values[LOCK_INFO] =
                new CompositeDataSupport(lockInfoCType,
                                         lockInfoItemNames,
                                         lockInfoValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String[] badItemNames = {
        "threadId",
        "threadName",
        "threadState",
        "blockedTime",
        "blockedCount",
        "waitedTime",
        "waitedCount",
        "lockName",
        "lockOwnerId",
        "lockOwnerName",
        "BadStackTrace", // bad item name
        "suspended",
        "inNative",
        "lockInfo",
    };
    private static final OpenType[] badItemTypes = {
        SimpleType.LONG,
        SimpleType.STRING,
        SimpleType.STRING,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.LONG,
        SimpleType.STRING,
        SimpleType.LONG,
        SimpleType.STRING,
        SimpleType.LONG,  // bad type
        SimpleType.BOOLEAN,
        SimpleType.BOOLEAN,
        SimpleType.LONG,  // bad type
    };

}
