/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;

/**
 * A timer for some activity of interest. A timer should be deployed using the try-with-resources
 * pattern:
 *
 * <pre>
 * try (TimerCloseable a = timer.start()) {
 *     // the code to time
 * }
 * </pre>
 */
public interface TimerKey extends MetricKey {

    /**
     * Starts this timer.
     *
     * @return an object that must be closed once the activity has completed to add the elapsed time
     *         since this call to the total for this timer
     */
    DebugCloseable start(DebugContext debug);

    /**
     * Gets the current value of this timer.
     */
    long getCurrentValue(DebugContext debug);

    /**
     * Gets the time unit of this timer.
     */
    TimeUnit getTimeUnit();

    @Override
    TimerKey doc(String string);

    /**
     * Gets the timer recording the amount time spent within a timed scope minus the time spent in
     * any nested timed scopes.
     *
     * @return null if this timer does not support flat timing
     */
    default TimerKey getFlat() {
        return null;
    }
}
