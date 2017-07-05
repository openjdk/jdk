/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/*
 */

import static java.lang.management.ManagementFactory.*;
import java.lang.management.ThreadMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import javax.management.*;
import java.io.*;

/**
 * Example of using the java.lang.management API to dump stack trace
 * and to perform deadlock detection.
 *
 * @author  Mandy Chung
 */
public class ThreadMonitor {
    private MBeanServerConnection server;
    private ThreadMXBean tmbean;
    private ObjectName objname;

    // default - JDK 6+ VM
    private String findDeadlocksMethodName = "findDeadlockedThreads";
    private boolean canDumpLocks = true;

    /**
     * Constructs a ThreadMonitor object to get thread information
     * in a remote JVM.
     */
    public ThreadMonitor(MBeanServerConnection server) throws IOException {
       this.server = server;
       this.tmbean = newPlatformMXBeanProxy(server,
                                            THREAD_MXBEAN_NAME,
                                            ThreadMXBean.class);
       try {
           objname = new ObjectName(THREAD_MXBEAN_NAME);
        } catch (MalformedObjectNameException e) {
            // should not reach here
            InternalError ie = new InternalError(e.getMessage());
            ie.initCause(e);
            throw ie;
       }
       parseMBeanInfo();
    }

    /**
     * Constructs a ThreadMonitor object to get thread information
     * in the local JVM.
     */
    public ThreadMonitor() {
        this.tmbean = getThreadMXBean();
    }

    /**
     * Prints the thread dump information to System.out.
     */
    public void threadDump() {
        if (canDumpLocks) {
            if (tmbean.isObjectMonitorUsageSupported() &&
                tmbean.isSynchronizerUsageSupported()) {
                // Print lock info if both object monitor usage
                // and synchronizer usage are supported.
                // This sample code can be modified to handle if
                // either monitor usage or synchronizer usage is supported.
                dumpThreadInfoWithLocks();
            }
        } else {
            dumpThreadInfo();
        }
    }

    private void dumpThreadInfo() {
       System.out.println("Full Java thread dump");
       long[] tids = tmbean.getAllThreadIds();
       ThreadInfo[] tinfos = tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
       for (ThreadInfo ti : tinfos) {
           printThreadInfo(ti);
       }
    }

    /**
     * Prints the thread dump information with locks info to System.out.
     */
    private void dumpThreadInfoWithLocks() {
       System.out.println("Full Java thread dump with locks info");

       ThreadInfo[] tinfos = tmbean.dumpAllThreads(true, true);
       for (ThreadInfo ti : tinfos) {
           printThreadInfo(ti);
           LockInfo[] syncs = ti.getLockedSynchronizers();
           printLockInfo(syncs);
       }
       System.out.println();
    }

    private static String INDENT = "    ";

    private void printThreadInfo(ThreadInfo ti) {
       // print thread information
       printThread(ti);

       // print stack trace with locks
       StackTraceElement[] stacktrace = ti.getStackTrace();
       MonitorInfo[] monitors = ti.getLockedMonitors();
       for (int i = 0; i < stacktrace.length; i++) {
           StackTraceElement ste = stacktrace[i];
           System.out.println(INDENT + "at " + ste.toString());
           for (MonitorInfo mi : monitors) {
               if (mi.getLockedStackDepth() == i) {
                   System.out.println(INDENT + "  - locked " + mi);
               }
           }
       }
       System.out.println();
    }

    private void printThread(ThreadInfo ti) {
       StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
                                            " Id=" + ti.getThreadId() +
                                            " in " + ti.getThreadState());
       if (ti.getLockName() != null) {
           sb.append(" on lock=" + ti.getLockName());
       }
       if (ti.isSuspended()) {
           sb.append(" (suspended)");
       }
       if (ti.isInNative()) {
           sb.append(" (running in native)");
       }
       System.out.println(sb.toString());
       if (ti.getLockOwnerName() != null) {
            System.out.println(INDENT + " owned by " + ti.getLockOwnerName() +
                               " Id=" + ti.getLockOwnerId());
       }
    }

    private void printMonitorInfo(ThreadInfo ti) {
       MonitorInfo[] monitors = ti.getLockedMonitors();
       System.out.println(INDENT + "Locked monitors: count = " + monitors.length);
       for (MonitorInfo mi : monitors) {
           System.out.println(INDENT + "  - " + mi + " locked at ");
           System.out.println(INDENT + "      " + mi.getLockedStackDepth() +
                              " " + mi.getLockedStackFrame());
       }
    }

    private void printLockInfo(LockInfo[] locks) {
       System.out.println(INDENT + "Locked synchronizers: count = " + locks.length);
       for (LockInfo li : locks) {
           System.out.println(INDENT + "  - " + li);
       }
       System.out.println();
    }

    /**
     * Checks if any threads are deadlocked. If any, print
     * the thread dump information.
     */
    public boolean findDeadlock() {
       long[] tids;
       if (findDeadlocksMethodName.equals("findDeadlockedThreads") &&
               tmbean.isSynchronizerUsageSupported()) {
           tids = tmbean.findDeadlockedThreads();
           if (tids == null) {
               return false;
           }

           System.out.println("Deadlock found :-");
           ThreadInfo[] infos = tmbean.getThreadInfo(tids, true, true);
           for (ThreadInfo ti : infos) {
               printThreadInfo(ti);
               printMonitorInfo(ti);
               printLockInfo(ti.getLockedSynchronizers());
               System.out.println();
           }
       } else {
           tids = tmbean.findMonitorDeadlockedThreads();
           if (tids == null) {
               return false;
           }
           ThreadInfo[] infos = tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
           for (ThreadInfo ti : infos) {
               // print thread information
               printThreadInfo(ti);
           }
       }

       return true;
    }


    private void parseMBeanInfo() throws IOException {
        try {
            MBeanOperationInfo[] mopis = server.getMBeanInfo(objname).getOperations();

            // look for findDeadlockedThreads operations;
            boolean found = false;
            for (MBeanOperationInfo op : mopis) {
                if (op.getName().equals(findDeadlocksMethodName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // if findDeadlockedThreads operation doesn't exist,
                // the target VM is running on JDK 5 and details about
                // synchronizers and locks cannot be dumped.
                findDeadlocksMethodName = "findMonitorDeadlockedThreads";
                canDumpLocks = false;
            }
        } catch (IntrospectionException e) {
            InternalError ie = new InternalError(e.getMessage());
            ie.initCause(e);
            throw ie;
        } catch (InstanceNotFoundException e) {
            InternalError ie = new InternalError(e.getMessage());
            ie.initCause(e);
            throw ie;
        } catch (ReflectionException e) {
            InternalError ie = new InternalError(e.getMessage());
            ie.initCause(e);
            throw ie;
        }
    }
}
