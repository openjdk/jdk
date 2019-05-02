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
import jdk.vm.ci.services.Services;

/**
 * JDK13+ version of {@code LibGraal}.
 */
public class LibGraal {

    public static boolean isAvailable() {
        return isCurrentRuntime() || libgraalIsolate != 0L;
    }

    public static boolean isCurrentRuntime() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static long getIsolate() {
        if (isCurrentRuntime() || !isAvailable()) {
            throw new IllegalStateException();
        }
        return libgraalIsolate;
    }

    public static long getIsolateThread() {
        if (isCurrentRuntime()) {
            throw new IllegalStateException();
        }
        return CURRENT_ISOLATE_THREAD.get();
    }

    @SuppressWarnings("unused")
    public static long[] registerNativeMethods(HotSpotJVMCIRuntime runtime, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (isCurrentRuntime() || !isAvailable()) {
            throw new IllegalStateException();
        }
        // Waiting for https://bugs.openjdk.java.net/browse/JDK-8220623
        // return runtime.registerNativeMethods(clazz);
        throw new IllegalStateException("Requires JDK-8220623");
    }

    @SuppressWarnings("unused")
    public static long translate(HotSpotJVMCIRuntime runtime, Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        // return runtime.translate(obj);
        throw new IllegalStateException("Requires JDK-8220623");
    }

    @SuppressWarnings("unused")
    public static <T> T unhand(HotSpotJVMCIRuntime runtime, Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        // return runtime.unhand(type, handle);
        throw new IllegalStateException("Requires JDK-8220623");
    }

    private static final ThreadLocal<Long> CURRENT_ISOLATE_THREAD = new ThreadLocal<>() {
        @Override
        protected Long initialValue() {
            return attachThread(libgraalIsolate);
        }
    };

    private static final long libgraalIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();

    private static long initializeLibgraal() {
        try {
            // Initialize JVMCI to ensure JVMCI opens its packages to
            // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
            // below will fail on JDK13+.
            Services.initializeJVMCI();

            // Waiting for https://bugs.openjdk.java.net/browse/JDK-8220623
            // HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            // long[] nativeInterface = runtime.registerNativeMethods(LibGraal.class);
            // return nativeInterface[1];
            return 0L;
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    /**
     * Attaches the current thread to a thread in {@code isolate}.
     *
     * @param isolate
     */
    private static native long attachThread(long isolate);
}
