/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import jdk.nashorn.internal.runtime.options.Options;

/**
 * Simple wallclock timing framework
 */
public final class Timing {
    private static final boolean ENABLED = Options.getBooleanProperty("nashorn.time");
    private static final Map<String, Long> TIMINGS;
    private static final long START_TIME;

    static {
        if (ENABLED) {
            TIMINGS    = new LinkedHashMap<>();
            START_TIME = System.currentTimeMillis();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    final long t = System.currentTimeMillis();
                    long knownTime = 0L;
                    int  maxLength = 0;

                    for (final Map.Entry<String, Long> entry : TIMINGS.entrySet()) {
                        maxLength = Math.max(maxLength, entry.getKey().length());
                    }
                    maxLength++;

                    for (final Map.Entry<String, Long> entry : TIMINGS.entrySet()) {
                        final StringBuilder sb = new StringBuilder();

                        sb.append(entry.getKey());
                        while (sb.length() < maxLength) {
                            sb.append(' ');
                        }

                        final long duration = entry.getValue();
                        sb.append(duration);
                        sb.append(' ');
                        sb.append(" ms");

                        knownTime += duration;

                        System.err.println(sb.toString()); //Context err is gone by shutdown TODO
                    }

                    final long total = t - START_TIME;
                    System.err.println("Total runtime: " + total + " ms (Non-runtime: " + knownTime + " ms [" + (int)(knownTime * 100.0 / total) + "%])");
                }
            });
        } else {
            TIMINGS = null;
            START_TIME = 0L;
        }
    }

    /**
     * Check if timing is inabled
     * @return true if timing is enabled
     */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * When timing, this can be called to register a new module for timing
     * or add to its accumulated time
     *
     * @param module   module name
     * @param duration duration to add to accumulated time for module
     */
    public static void accumulateTime(final String module, final long duration) {
        if (Timing.isEnabled()) {
            Long accumulatedTime = TIMINGS.get(module);
            if (accumulatedTime == null) {
                accumulatedTime = 0L;
            }
            TIMINGS.put(module, accumulatedTime + duration);
        }
    }

}
