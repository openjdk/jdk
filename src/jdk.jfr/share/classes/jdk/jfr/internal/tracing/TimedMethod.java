/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tracing;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Record class that holds invocation measurements used by the MethodTiming
 * event.
 * <p>
 * Fields in record classes are truly final so might help to have a record here.
 */
record TimedMethod(AtomicLong invocations, AtomicLong time, AtomicLong minimum, AtomicLong maximum, Method method, AtomicBoolean published) {
    TimedMethod(Method method) {
        this(new AtomicLong(), new AtomicLong(), new AtomicLong(Long.MAX_VALUE), new AtomicLong(Long.MIN_VALUE), method, new AtomicBoolean());
    }

    public void updateMinMax(long duration) {
        if (duration == 0) {
            return; // Ignore data due to low-resolution clock
        }
        if (duration > maximum.getPlain()) {
            while (true) {
                long max = maximum.get();
                if (duration <= max) {
                    return;
                }
                if (maximum.weakCompareAndSetVolatile(max, duration)) {
                    return;
                }
            }
        }
        if (duration < minimum.getPlain()) {
            while (true) {
                long min = minimum.get();
                if (duration >= min) {
                    return;
                }
                if (minimum.weakCompareAndSetVolatile(min, duration)) {
                    return;
                }
            }
        }
    }
}
