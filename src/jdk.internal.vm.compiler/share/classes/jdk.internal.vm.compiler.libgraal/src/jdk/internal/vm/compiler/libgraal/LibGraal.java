/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.vm.compiler.libgraal;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK13+ version of {@code LibGraal}.
 */
public class LibGraal {

    public static boolean isAvailable() {
        return inLibGraal() || isolate != 0L;
    }

    public static boolean isSupported() {
        return true;
    }

    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static void registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (inLibGraal() || !isAvailable()) {
            throw new IllegalStateException();
        }
        runtime.registerNativeMethods(clazz);
    }

    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime.translate(obj);
    }

    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime.unhand(type, handle);
    }

    private static long initializeLibgraal() {
        try {
            // Initialize JVMCI to ensure JVMCI opens its packages to
            // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
            // below will fail on JDK13+.
            Services.initializeJVMCI();

            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            long[] nativeInterface = runtime.registerNativeMethods(LibGraal.class);
            return nativeInterface[1];
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    static final long isolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();

    static boolean isCurrentThreadAttached(HotSpotJVMCIRuntime runtime) {
        return runtime.isCurrentThreadAttached();
    }

    public static boolean attachCurrentThread(HotSpotJVMCIRuntime runtime, boolean isDaemon) {
        return runtime.attachCurrentThread(isDaemon);
    }

    public static void detachCurrentThread(HotSpotJVMCIRuntime runtime) {
        runtime.detachCurrentThread();
    }

    static native long getCurrentIsolateThread(long iso);

    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        return log.getFailedSpeculationsAddress();
    }
}
