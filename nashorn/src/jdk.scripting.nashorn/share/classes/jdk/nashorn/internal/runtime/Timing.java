/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Simple wallclock timing framework
 */
@Logger(name="time")
public final class Timing implements Loggable {

    private DebugLogger log;
    private TimeSupplier timeSupplier;
    private final boolean isEnabled;
    private final long startTime;

    private static final String LOGGER_NAME = Timing.class.getAnnotation(Logger.class).name();

    /**
     * Instantiate singleton timer for ScriptEnvironment
     * @param isEnabled true if enabled, otherwise we keep the instance around
     *      for code brevity and "isEnabled" checks, but never instantiate anything
     *      inside it
     */
    public Timing(final boolean isEnabled) {
        this.isEnabled = isEnabled;
        this.startTime = System.nanoTime();
    }

    /**
     * Get the log info accumulated by this Timing instance
     * @return log info as one string
     */
    public String getLogInfo() {
        assert isEnabled();
        return timeSupplier.get();
    }

    /**
     * Get the log info accumulated by this Timing instance
     * @return log info as and array of strings, one per line
     */
    public String[] getLogInfoLines() {
        assert isEnabled();
        return timeSupplier.getStrings();
    }

    /**
     * Check if timing is enabled
     * @return true if timing is enabled
     */
    boolean isEnabled() {
        return isEnabled;
    }

    /**
     * When timing, this can be called to register a new module for timing
     * or add to its accumulated time
     *
     * @param module   module name
     * @param durationNano duration to add to accumulated time for module, in nanoseconds.
     */
    public void accumulateTime(final String module, final long durationNano) {
        if (isEnabled()) {
            ensureInitialized(Context.getContextTrusted());
            timeSupplier.accumulateTime(module, durationNano);
        }
    }

    private DebugLogger ensureInitialized(final Context context) {
        //lazy init, as there is not necessarily a context available when
        //a ScriptEnvironment gets initialize
        if (isEnabled() && log == null) {
            log = initLogger(context);
            if (log.isEnabled()) {
                this.timeSupplier = new TimeSupplier();
                Runtime.getRuntime().addShutdownHook(
                        new Thread() {
                            @Override
                            public void run() {
                                //System.err.println because the context and the output streams may be gone
                                //when the shutdown hook executes
                                final StringBuilder sb = new StringBuilder();
                                for (final String str : timeSupplier.getStrings()) {
                                    sb.append('[').
                                        append(Timing.getLoggerName()).
                                        append("] ").
                                        append(str).
                                        append('\n');
                                }
                                System.err.print(sb);
                            }
                        });
            }
        }
        return log;
    }

    static String getLoggerName() {
        return LOGGER_NAME;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    /**
     * Takes a duration in nanoseconds, and returns a string representation of it rounded to milliseconds.
     * @param durationNano duration in nanoseconds
     * @return the string representing the duration in milliseconds.
     */
    public static String toMillisPrint(final long durationNano) {
        return Long.toString(TimeUnit.NANOSECONDS.toMillis(durationNano));
    }

    final class TimeSupplier implements Supplier<String> {
        private final Map<String, LongAdder> timings = new ConcurrentHashMap<>();
        private final LinkedBlockingQueue<String> orderedTimingNames = new LinkedBlockingQueue<>();
        private final Function<String, LongAdder> newTimingCreator = s -> {
            orderedTimingNames.add(s);
            return new LongAdder();
        };

        String[] getStrings() {
            final List<String> strs = new ArrayList<>();
            final BufferedReader br = new BufferedReader(new StringReader(get()));
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    strs.add(line);
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return strs.toArray(new String[strs.size()]);
        }

        @Override
        public String get() {
            final long t = System.nanoTime();

            long knownTime = 0L;
            int  maxKeyLength = 0;
            int  maxValueLength = 0;

            for (final Map.Entry<String, LongAdder> entry : timings.entrySet()) {
                maxKeyLength   = Math.max(maxKeyLength, entry.getKey().length());
                maxValueLength = Math.max(maxValueLength, toMillisPrint(entry.getValue().longValue()).length());
            }
            maxKeyLength++;

            final StringBuilder sb = new StringBuilder();
            sb.append("Accumulated compilation phase timings:\n\n");
            for (final String timingName: orderedTimingNames) {
                int len;

                len = sb.length();
                sb.append(timingName);
                len = sb.length() - len;

                while (len++ < maxKeyLength) {
                    sb.append(' ');
                }

                final long duration = timings.get(timingName).longValue();
                final String strDuration = toMillisPrint(duration);
                len = strDuration.length();
                for (int i = 0; i < maxValueLength - len; i++) {
                    sb.append(' ');
                }

                sb.append(strDuration).
                    append(" ms\n");

                knownTime += duration;
            }

            final long total = t - startTime;
            sb.append('\n');
            sb.append("Total runtime: ").
                append(toMillisPrint(total)).
                append(" ms (Non-runtime: ").
                append(toMillisPrint(knownTime)).
                append(" ms [").
                append((int)(knownTime * 100.0 / total)).
                append("%])");

            sb.append("\n\nEmitted compile units: ").
                append(CompileUnit.getEmittedUnitCount());

            return sb.toString();
        }

        private void accumulateTime(final String module, final long duration) {
            timings.computeIfAbsent(module, newTimingCreator).add(duration);
        }
    }
}
