/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

import jdk.jfr.Recording;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.management.HiddenWait;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.util.ValueFormatter;

/**
 * Checks if the running VM supports Flight Recorder.
 *
 * Purpose of this helper class is to detect early and cleanly if the VM has
 * support for Flight Recorder, i.e. not throw {@link UnsatisfiedLinkError} in
 * unexpected places.
 * <p>
 * This is needed so a disabled-jfr.jar can be built for non Oracle JDKs.
 */
public final class JVMSupport {
    private static final String UNSUPPORTED_VM_MESSAGE = "Flight Recorder is not supported on this VM";
    private static final boolean notAvailable = !checkAvailability();
    /*
     * This field will be lazily initialized and the access is not synchronized.
     * The possible data race is benign and is worth of not introducing any contention here.
     */
    private static Instant lastTimestamp;
    private static volatile boolean nativeOK;

    private static boolean checkAvailability() {
        // set jfr.unsupported.vm to true to test API on an unsupported VM
        try {
            if (SecuritySupport.getBooleanProperty("jfr.unsupported.vm")) {
                return false;
            }
        } catch (NoClassDefFoundError cnfe) {
            // May happen on JDK 8, where jdk.internal.misc.Unsafe can't be found
            return false;
        }
        try {
            // Will typically throw UnsatisfiedLinkError if
            // there is no native implementation
            JVM.isAvailable();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void ensureWithInternalError() {
        if (notAvailable) {
            throw new InternalError(UNSUPPORTED_VM_MESSAGE);
        }
    }

    public static void ensureWithIOException() throws IOException {
        if (notAvailable) {
            throw new IOException(UNSUPPORTED_VM_MESSAGE);
        }
    }

    public static void ensureWithIllegalStateException() {
        if (notAvailable) {
            throw new IllegalStateException(UNSUPPORTED_VM_MESSAGE);
        }
    }

    public static boolean isNotAvailable() {
        return notAvailable;
    }

    public static void tryToInitializeJVM() {
    }

    static long nanosToTicks(long nanos) {
        return (long) (nanos * JVM.getTimeConversionFactor());
    }

    static long getChunkStartNanos() {
        long nanos = JVM.getChunkStartNanos();
        // Spin here to give Instant.now() a chance to catch up.
        awaitUniqueTimestamp();
        return nanos;
    }

    private static void awaitUniqueTimestamp() {
        if (lastTimestamp == null) {
            lastTimestamp = Instant.now(); // lazy initialization
        }
        while (true) {
            Instant time = Instant.now();
            if (!time.equals(lastTimestamp)) {
                lastTimestamp = time;
                return;
            }
            HiddenWait hiddenWait = new HiddenWait();
            hiddenWait.takeNap(1);
        }
    }

    public static synchronized EventConfiguration getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        Utils.ensureValidEventSubclass(eventClass);
        Object configuration = JVM.getConfiguration(eventClass);
        if (configuration == null || configuration instanceof EventConfiguration) {
            return (EventConfiguration) configuration;
        }
        throw new InternalError("Could not get configuration object on event class " + eventClass.getName());
    }

    public static synchronized void setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, EventConfiguration configuration) {
        Utils.ensureValidEventSubclass(eventClass);
        if (!JVM.setConfiguration(eventClass, configuration)) {
            throw new InternalError("Could not set configuration object on event class " + eventClass.getName());
        }
    }

    static boolean shouldInstrument(boolean isJDK, String name) {
        if (!isJDK) {
            return true;
        }
        if (!name.contains(".Container")) {
            // Didn't match @Name("jdk.jfr.Container*") or class name "jdk.jfr.events.Container*"
            return true;
        }
        return JVM.isContainerized();
    }

    public static String makeFilename(Recording recording) {
        String pid = JVM.getPid();
        String date = ValueFormatter.formatDateTime(LocalDateTime.now());
        String idText = recording == null ? "" :  "-id-" + Long.toString(recording.getId());
        return "hotspot-" + "pid-" + pid + idText + "-" + date + ".jfr";
    }

    public static boolean createFailedNativeJFR() throws IllegalStateException {
        return JVM.createJFR(true);
    }

    public static void createJFR() {
        nativeOK = JVM.createJFR(false);
    }

    public static boolean destroyJFR() {
        boolean result = JVM.destroyJFR();
        nativeOK = !result;
        return result;
    }

    public static boolean hasJFR() {
        return nativeOK;
    }
}
