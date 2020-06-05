/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.services.Services;

/**
 * JDK13+ version of {@code LibGraal}.
 */
public class LibGraal {

    static {
        // Initialize JVMCI to ensure JVMCI opens its packages to Graal.
        Services.initializeJVMCI();
    }

    public static boolean isAvailable() {
        return inLibGraal() || theIsolate != 0L;
    }

    public static boolean isSupported() {
        return true;
    }

    public static boolean inLibGraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    public static void registerNativeMethods(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            throw new IllegalArgumentException();
        }
        if (inLibGraal() || !isAvailable()) {
            throw new IllegalStateException();
        }
        runtime().registerNativeMethods(clazz);
    }

    public static long translate(Object obj) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime().translate(obj);
    }

    public static <T> T unhand(Class<T> type, long handle) {
        if (!isAvailable()) {
            throw new IllegalStateException();
        }
        if (!inLibGraal() && LibGraalScope.currentScope.get() == null) {
            throw new IllegalStateException("Not within a " + LibGraalScope.class.getName());
        }
        return runtime().unhand(type, handle);
    }

    private static long initializeLibgraal() {
        try {
            long[] javaVMInfo = runtime().registerNativeMethods(LibGraalScope.class);
            return javaVMInfo[1];
        } catch (UnsupportedOperationException e) {
            return 0L;
        }
    }

    static final long theIsolate = Services.IS_BUILDING_NATIVE_IMAGE ? 0L : initializeLibgraal();

    static boolean isCurrentThreadAttached() {
        return runtime().isCurrentThreadAttached();
    }

    public static boolean attachCurrentThread(boolean isDaemon, long[] isolate) {
        if (isolate != null) {
            isolate[0] = LibGraal.theIsolate;
        }
        return runtime().attachCurrentThread(isDaemon);
    }

    public static boolean detachCurrentThread(boolean release) {
        runtime().detachCurrentThread();
        return false;
    }

    public static long getFailedSpeculationsAddress(HotSpotSpeculationLog log) {
        return log.getFailedSpeculationsAddress();
    }
}
