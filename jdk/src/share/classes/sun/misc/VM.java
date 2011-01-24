/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

    // Invoked by by System.initializeSystemClass just before returning.
    // Subsystems that are invoked during initialization can check this
    // property in order to avoid doing things that should wait until the
    // application class loader has been set up.
    //
    public static void booted() {
        booted = true;
    }

    public static boolean isBooted() {
        return booted;
    }

    // A user-settable upper limit on the maximum amount of allocatable direct
    // buffer memory.  This value may be changed during VM initialization if
    // "java" is launched with "-XX:MaxDirectMemorySize=<size>".
    //
    // The initial value of this field is arbitrary; during JRE initialization
    // it will be reset to the value specified on the command line, if any,
    // otherwise to Runtime.getRuntime.maxDirectMemory().
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

    // A user-settable boolean to determine whether ClassLoader.loadClass should
    // accept array syntax.  This value may be changed during VM initialization
    // via the system property "sun.lang.ClassLoader.allowArraySyntax".
    //
    // The default for 1.5 is "true", array syntax is allowed.  In 1.6, the
    // default will be "false".  The presence of this system property to
    // control array syntax allows applications the ability to preview this new
    // behaviour.
    //
    private static boolean defaultAllowArraySyntax = false;
    private static boolean allowArraySyntax = defaultAllowArraySyntax;

    // The allowArraySyntax boolean is initialized during system initialization
    // in the saveAndRemoveProperties method.
    //
    // It is initialized based on the value of the system property
    // "sun.lang.ClassLoader.allowArraySyntax".  If the system property is not
    // provided, the default for 1.5 is "true".  In 1.6, the default will be
    // "false".  If the system property is provided, then the value of
    // allowArraySyntax will be equal to "true" if Boolean.parseBoolean()
    // returns "true".   Otherwise, the field will be set to "false".
    //
    public static boolean allowArraySyntax() {
        return allowArraySyntax;
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

        // Set a boolean to determine whether ClassLoader.loadClass accepts
        // array syntax.  This value is controlled by the system property
        // "sun.lang.ClassLoader.allowArraySyntax".
        s = props.getProperty("sun.lang.ClassLoader.allowArraySyntax");
        allowArraySyntax = (s == null
                               ? defaultAllowArraySyntax
                               : Boolean.parseBoolean(s));

        // Remove other private system properties
        // used by java.lang.Integer.IntegerCache
        props.remove("java.lang.Integer.IntegerCache.high");

        // used by java.util.zip.ZipFile
        props.remove("sun.zip.disableMemoryMapping");
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
     * Add <tt>n</tt> to the objects pending for finalization count.
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
    private final static int JVMTI_THREAD_STATE_ALIVE = 0x0001;
    private final static int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
    private final static int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
    private final static int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    private final static int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
    private final static int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;

    static {
        initialize();
    }
    private native static void initialize();
}
