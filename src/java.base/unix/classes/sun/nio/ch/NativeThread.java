/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.util.concurrent.locks.LockSupport;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

// Signalling operations on native threads
//
// On some operating systems (e.g., Linux), closing a channel while another
// thread is blocked in an I/O operation upon that channel does not cause that
// thread to be released.  This class provides access to the native threads
// upon which Java threads are built, and defines a simple signal mechanism
// that can be used to release a native thread from a blocking I/O operation.
// On systems that do not require this type of signalling, the current() method
// always returns -1 and the signal(long) method has no effect.

public class NativeThread {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private NativeThread() { }

    /**
     * Returns the Thread to signal the current thread.
     *
     * The first use of this method on a platform thread will capture the thread's
     * native thread ID.
     */
    public static Thread threadToSignal() {
        Thread t = Thread.currentThread();
        if (!t.isVirtual() && JLA.nativeThreadID(t) == 0) {
            JLA.setThreadNativeID(current0());
        }
        return t;
    }

    /**
     * Signals the given thread. For a platform thread it sends a signal to the thread.
     * For a virtual thread it just unparks it.
     * @throws IllegalStateException if the thread is a platform thread that hasn't set its native ID
     */
    public static void signal(Thread thread) {
        if (thread.isVirtual()) {
            LockSupport.unpark(thread);
        } else {
            long id = JLA.nativeThreadID(thread);
            if (id == 0)
                throw new IllegalStateException("Native thread ID not set");
            signal0(id);
        }
    }

    /**
     * Return true if the operating system supports pending signals. If a signal is sent
     * to a thread but cannot be delivered immediately then it will be delivered when the
     * thread is in the appropriate state.
     */
    static boolean supportPendingSignals() {
        return supportPendingSignals0();
    }

    private static native boolean supportPendingSignals0();

    // Returns an opaque token representing the native thread underlying the
    // invoking Java thread.  On systems that do not require signalling, this
    // method always returns 0.
    //
    private static native long current0();

    // Signals the given native thread so as to release it from a blocking I/O
    // operation.  On systems that do not require signalling, this method has
    // no effect.
    //
    private static native void signal0(long tid);

    private static native void init();

    static {
        IOUtil.load();
        init();
    }

}
