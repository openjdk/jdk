/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * Tracks memory usage within a scope using {@link com.sun.management.ThreadMXBean}. This facility
 * should be employed using the try-with-resources pattern:
 *
 * <pre>
 * try (DebugCloseable a = memUseTracker.start()) {
 *     // the code to measure
 * }
 * </pre>
 */
public interface MemUseTrackerKey extends MetricKey {

    /**
     * Creates a point from which memory usage will be recorded if memory use tracking is
     * {@linkplain DebugContext#isMemUseTrackingEnabled() enabled}.
     *
     * @return an object that must be closed once the activity has completed to add the memory used
     *         since this call to the total for this tracker
     */
    DebugCloseable start(DebugContext debug);

    /**
     * Gets the current value of this tracker.
     */
    long getCurrentValue(DebugContext debug);

    @Override
    MemUseTrackerKey doc(String string);

    static long getCurrentThreadAllocatedBytes() {
        return GraalServices.getCurrentThreadAllocatedBytes();
    }
}
