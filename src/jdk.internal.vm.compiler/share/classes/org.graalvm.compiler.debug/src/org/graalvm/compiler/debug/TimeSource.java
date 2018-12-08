/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * A consistent source of timing data that should be used by all facilities in the debug package.
 */
public class TimeSource {
    private static final boolean USING_THREAD_CPU_TIME = GraalServices.isCurrentThreadCpuTimeSupported();

    /**
     * Gets the current time of this thread in nanoseconds from the most accurate timer available on
     * the system. The returned value will be the current time in nanoseconds precision but not
     * necessarily nanoseconds accuracy.
     * <p>
     * The intended use case of this method is to measure the time a certain action takes by making
     * successive calls to it. It should not be used to measure total times in the sense of a time
     * stamp.
     *
     * @return the current thread's time in nanoseconds
     */
    public static long getTimeNS() {
        return USING_THREAD_CPU_TIME ? GraalServices.getCurrentThreadCpuTime() : System.nanoTime();
    }

}
