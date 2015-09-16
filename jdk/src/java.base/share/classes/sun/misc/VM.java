/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import static java.lang.Thread.State.*;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VM {

    /* The following methods used to be native methods that instruct
     * the VM to selectively suspend certain threads in low-memory
     * situations. They are inherently dangerous and not implementable
     * on native threads. We removed them in JDK 1.2. The skeletons
     * remain so that existing applications that use these methods
     * will still work.
     */
    private static boolean suspended = false;

    /** @deprecated */
    @Deprecated
    public static boolean threadsSuspended() {
        return suspended;
    }

    @SuppressWarnings("deprecation")
    public static boolean allowThreadSuspension(ThreadGroup g, boolean b) {
        return g.allowThreadSuspension(b);
    }

    /** @deprecated */
    @Deprecated
    public static boolean suspendThreads() {
        suspended = true;
        return true;
    }

    // Causes any suspended threadgroups to be resumed.
    /** @deprecated */
    @Deprecated
    public static void unsuspendThreads() {
        suspended = false;
    }

    // Causes threadgroups no longer marked suspendable to be resumed.
    /** @deprecated */
    @Deprecated
    public static void unsuspendSomeThreads() {
    }

    /* Deprecated fields and methods -- Memory advice not supported in 1.2 */

    /** @deprecated */
    @Deprecated
    public static final int STATE_GREEN = 1;

    /** @deprecated */
    @Deprecated
    public static final int STATE_YELLOW = 2;

    /** @deprecated */
    @Deprecated
    public static final int STATE_RED = 3;

    /** @deprecated */
    @Deprecated
    public static final int getState() {
        return STATE_GREEN;
    }

    /** @deprecated */
    @Deprecated
    public static void registerVMNotification(VMNotification n) { }

    /** @deprecated */
    @Deprecated
    public static void asChange(int as_old, int as_new) { }

    /** @deprecated */
    @Deprecated
    public static void asChange_otherthread(int as_old, int as_new) { }

    /*
     * Not supported in 1.2 because these will have to be exported as
     * JVM functions, and we are not sure we want do that. Leaving
     * here so it can be easily resurrected -- just remove the //
     * comments.
     */

    /**
     * Resume Java profiling.  All profiling data is added to any
     * earlier profiling, unless <code>resetJavaProfiler</code> is
     * called in between.  If profiling was not started from the
     * command line, <code>resumeJavaProfiler</code> will start it.
     * <p>
     *
     * NOTE: Profiling must be enabled from the command line for a
     * java.prof report to be automatically generated on exit; if not,
     * writeJavaProfilerReport must be invoked to write a report.
     *
     * @see     resetJavaProfiler
     * @see     writeJavaProfilerReport
     */

    // public native static void resumeJavaProfiler();

    /**
     * Suspend Java profiling.
     */
    // public native static void suspendJavaProfiler();

    /**
     * Initialize Java profiling.  Any accumulated profiling
     * information is discarded.
     */
    // public native static void resetJavaProfiler();

    /**
     * Write the current profiling contents to the file "java.prof".
     * If the file already exists, it will be overwritten.
     */
    // public native static void writeJavaProfilerReport();


    private static volatile boolean booted = false;
    private static final Object lock = new Object();

    // Invoked by System.initializeSystemClass just before returning.
    // Subsystems that are invoked during initialization can check this
    // property in order to avoid doing things that should wait until the
    // application class loader has been set up.
    //
    public static void booted() {
        synchronized (lock) {
            booted = true;
            lock.notifyAll();
        }
    }

    public static boolean isBooted() {
        return booted;
    }

    // Waits until VM completes initialization
    //
    // This method is invoked by the Finalizer thread
    public static void awaitBooted() throws InterruptedException {
        synchronized (lock) {
            while (!booted) {
                lock.wait();
            }
        }
    }

    // A user-settable upper limit on the maximum amount of allocatable direct
    // buffer memory.  This value may be changed during VM initialization if
    // "java" is launched with "-XX:MaxDirectMemorySize=<size>".
    //
    // The initial value of this field is arbitrary; during JRE initialization
    // it will be reset to the value specified on the command line, if any,
    // otherwise to Runtime.getRuntime().maxMemory().
    //
    private static long directMemory = 64 * 1024 * 1024;

    // Returns the maximum amount of allocatable direct buffer memory.
    // The directMemory variable is initialized during system initialization
    // in the saveAndRemoveProperties method.
    //
    public static long maxDirectMemory() {
        return directMemory;
    }

    // User-controllable flag that determines if direct buffers should be page
    // aligned. The "-XX:+PageAlignDirectMemory" option can be used to force
    // buffers, allocated by ByteBuffer.allocateDirect, to be page aligned.
    private static boolean pageAlignDirectMemory;

    // Returns {@code true} if the direct buffers should be page aligned. This
    // variable is initialized by saveAndRemoveProperties.
    public static boolean isDirectMemoryPageAligned() {
        return pageAlignDirectMemory;
    }

    /**
     * Returns true if the given class loader is in the system domain
     * in which all permissions are granted.
     */
    public static boolean isSystemDomainLoader(ClassLoader loader) {
        return loader == null;
    }

    /**
     * Returns the system property of the specified key saved at
     * system initialization time.  This method should only be used
     * for the system properties that are not changed during runtime.
     * It accesses a private copy of the system properties so
     * that user's locking of the system properties object will not
     * cause the library to deadlock.
     *
     * Note that the saved system properties do not include
     * the ones set by sun.misc.Version.init().
     *
     */
    public static String getSavedProperty(String key) {
        if (savedProps.isEmpty())
            throw new IllegalStateException("Should be non-empty if initialized");

        return savedProps.getProperty(key);
    }

    // TODO: the Property Management needs to be refactored and
    // the appropriate prop keys need to be accessible to the
    // calling classes to avoid duplication of keys.
    private static final Properties savedProps = new Properties();

    // Save a private copy of the system properties and remove
    // the system properties that are not intended for public access.
    //
    // This method can only be invoked during system initialization.
    public static void saveAndRemoveProperties(Properties props) {
        if (booted)
            throw new IllegalStateException("System initialization has completed");

        savedProps.putAll(props);

        // Set the maximum amount of direct memory.  This value is controlled
        // by the vm option -XX:MaxDirectMemorySize=<size>.
        // The maximum amount of allocatable direct buffer memory (in bytes)
        // from the system property sun.nio.MaxDirectMemorySize set by the VM.
        // The system property will be removed.
        String s = (String)props.remove("sun.nio.MaxDirectMemorySize");
        if (s != null) {
            if (s.equals("-1")) {
                // -XX:MaxDirectMemorySize not given, take default
                directMemory = Runtime.getRuntime().maxMemory();
            } else {
                long l = Long.parseLong(s);
                if (l > -1)
                    directMemory = l;
            }
        }

        // Check if direct buffers should be page aligned
        s = (String)props.remove("sun.nio.PageAlignDirectMemory");
        if ("true".equals(s))
            pageAlignDirectMemory = true;

        // Remove other private system properties
        // used by java.lang.Integer.IntegerCache
        props.remove("java.lang.Integer.IntegerCache.high");

        // used by java.util.zip.ZipFile
        props.remove("sun.zip.disableMemoryMapping");

        // used by sun.launcher.LauncherHelper
        props.remove("sun.java.launcher.diag");
    }

    // Initialize any miscellenous operating system settings that need to be
    // set for the class libraries.
    //
    public static void initializeOSEnvironment() {
        if (!booted) {
            OSEnvironment.initialize();
        }
    }

    /* Current count of objects pending for finalization */
    private static volatile int finalRefCount = 0;

    /* Peak count of objects pending for finalization */
    private static volatile int peakFinalRefCount = 0;

    /*
     * Gets the number of objects pending for finalization.
     *
     * @return the number of objects pending for finalization.
     */
    public static int getFinalRefCount() {
        return finalRefCount;
    }

    /*
     * Gets the peak number of objects pending for finalization.
     *
     * @return the peak number of objects pending for finalization.
     */
    public static int getPeakFinalRefCount() {
        return peakFinalRefCount;
    }

    /*
     * Add {@code n} to the objects pending for finalization count.
     *
     * @param n an integer value to be added to the objects pending
     * for finalization count
     */
    public static void addFinalRefCount(int n) {
        // The caller must hold lock to synchronize the update.

        finalRefCount += n;
        if (finalRefCount > peakFinalRefCount) {
            peakFinalRefCount = finalRefCount;
        }
    }

    /**
     * Returns Thread.State for the given threadStatus
     */
    public static Thread.State toThreadState(int threadStatus) {
        if ((threadStatus & JVMTI_THREAD_STATE_RUNNABLE) != 0) {
            return RUNNABLE;
        } else if ((threadStatus & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0) {
            return BLOCKED;
        } else if ((threadStatus & JVMTI_THREAD_STATE_WAITING_INDEFINITELY) != 0) {
            return WAITING;
        } else if ((threadStatus & JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT) != 0) {
            return TIMED_WAITING;
        } else if ((threadStatus & JVMTI_THREAD_STATE_TERMINATED) != 0) {
            return TERMINATED;
        } else if ((threadStatus & JVMTI_THREAD_STATE_ALIVE) == 0) {
            return NEW;
        } else {
            return RUNNABLE;
        }
    }

    /* The threadStatus field is set by the VM at state transition
     * in the hotspot implementation. Its value is set according to
     * the JVM TI specification GetThreadState function.
     */
    private static final int JVMTI_THREAD_STATE_ALIVE = 0x0001;
    private static final int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
    private static final int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
    private static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    private static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
    private static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;

    /*
     * Returns the first non-null class loader up the execution stack,
     * or null if only code from the null class loader is on the stack.
     */
    public static native ClassLoader latestUserDefinedLoader();

    /**
     * Returns {@code true} if we are in a set UID program.
     */
    public static boolean isSetUID() {
        long uid = getuid();
        long euid = geteuid();
        long gid = getgid();
        long egid = getegid();
        return uid != euid  || gid != egid;
    }

    /**
     * Returns the real user ID of the calling process,
     * or -1 if the value is not available.
     */
    public static native long getuid();

    /**
     * Returns the effective user ID of the calling process,
     * or -1 if the value is not available.
     */
    public static native long geteuid();

    /**
     * Returns the real group ID of the calling process,
     * or -1 if the value is not available.
     */
    public static native long getgid();

    /**
     * Returns the effective group ID of the calling process,
     * or -1 if the value is not available.
     */
    public static native long getegid();

    /**
     * Get a nanosecond time stamp adjustment in the form of a single long.
     *
     * This value can be used to create an instant using
     * {@link java.time.Instant#ofEpochSecond(long, long)
     *  java.time.Instant.ofEpochSecond(offsetInSeconds,
     *  getNanoTimeAdjustment(offsetInSeconds))}.
     * <p>
     * The value returned has the best resolution available to the JVM on
     * the current system.
     * This is usually down to microseconds - or tenth of microseconds -
     * depending on the OS/Hardware and the JVM implementation.
     *
     * @param offsetInSeconds The offset in seconds from which the nanosecond
     *        time stamp should be computed.
     *
     * @apiNote The offset should be recent enough - so that
     *         {@code offsetInSeconds} is within {@code +/- 2^32} seconds of the
     *         current UTC time. If the offset is too far off, {@code -1} will be
     *         returned. As such, {@code -1} must not be considered as a valid
     *         nano time adjustment, but as an exception value indicating
     *         that an offset closer to the current time should be used.
     *
     * @return A nanosecond time stamp adjustment in the form of a single long.
     *     If the offset is too far off the current time, this method returns -1.
     *     In that case, the caller should call this method again, passing a
     *     more accurate offset.
     */
    public static native long getNanoTimeAdjustment(long offsetInSeconds);

    static {
        initialize();
    }
    private static native void initialize();
}
