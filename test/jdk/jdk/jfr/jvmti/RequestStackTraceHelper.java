/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

package jdk.jfr.jvmti;

/**
 * Bridge to the libRequestStackTraceAgent native agent. The agent resolves
 * the {@code com.sun.hotspot.functions.RequestJFRStackTrace} JVMTI extension
 * once at load time and exposes three thin wrappers used by the tests.
 *
 * Each method returns the raw {@code jvmtiError} code from the underlying
 * extension call. {@code JVMTI_ERROR_NONE} is 0.
 */
public final class RequestStackTraceHelper {

    public static final int JVMTI_ERROR_NONE                  = 0;
    public static final int JVMTI_ERROR_INVALID_THREAD        = 10;
    public static final int JVMTI_ERROR_THREAD_NOT_ALIVE      = 15;
    public static final int JVMTI_ERROR_UNSUPPORTED_OPERATION = 73;
    public static final int JVMTI_ERROR_NOT_AVAILABLE         = 98;
    public static final int JVMTI_ERROR_WRONG_PHASE           = 112;

    static {
        System.loadLibrary("RequestStackTraceAgent");
    }

    private RequestStackTraceHelper() {}

    /** Direct call from the calling thread, no ucontext. */
    public static native int requestStackTrace(long userData);

    /**
     * Raises SIGUSR1 on the current thread; the agent's signal handler
     * invokes RequestJFRStackTrace with the captured ucontext. Returns the
     * JVMTI return code observed inside the handler.
     */
    public static native int requestStackTraceFromSignalHandler(long userData);

    /** Direct call passing a jthread argument and a null ucontext. */
    public static native int requestStackTraceWithThread(Thread thread, long userData);
}
