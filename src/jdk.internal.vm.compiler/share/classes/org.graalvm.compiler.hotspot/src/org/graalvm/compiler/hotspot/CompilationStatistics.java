/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.services.Services;

@SuppressWarnings("unused")
public final class CompilationStatistics {

    public static class Options {
        // @formatter:off
        @Option(help = "Enables CompilationStatistics.")
        public static final OptionKey<Boolean> UseCompilationStatistics = new OptionKey<>(false);
        // @formatter:on
    }

    private static final long RESOLUTION = 100000000;

    private static final CompilationStatistics DUMMY = new CompilationStatistics(null, false);

    private static ConcurrentLinkedDeque<CompilationStatistics> list = new ConcurrentLinkedDeque<>();

    private static final ThreadLocal<Deque<CompilationStatistics>> current = new ThreadLocal<Deque<CompilationStatistics>>() {

        @Override
        protected Deque<CompilationStatistics> initialValue() {
            return new ArrayDeque<>();
        }
    };

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface NotReported {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface TimeValue {
    }

    private static long zeroTime = System.nanoTime();

    private static long getThreadAllocatedBytes() {
        return GraalServices.getCurrentThreadAllocatedBytes();
    }

    @NotReported private final long startTime;
    @NotReported private long threadAllocatedBytesStart;

    private int bytecodeCount;
    private int codeSize;
    @TimeValue private long duration;
    private long memoryUsed;
    private final boolean osr;
    private final String holder;
    private final String name;
    private final String signature;

    private CompilationStatistics(HotSpotResolvedJavaMethod method, boolean osr) {
        this.osr = osr;
        if (method != null) {
            holder = method.getDeclaringClass().getName();
            name = method.getName();
            signature = method.getSignature().toMethodDescriptor();
            startTime = System.nanoTime();
            bytecodeCount = method.getCodeSize();
            threadAllocatedBytesStart = getThreadAllocatedBytes();
        } else {
            assert DUMMY == null : "only DUMMY has no method";
            holder = "";
            name = "";
            signature = "";
            startTime = 0;
        }
    }

    public void finish(HotSpotResolvedJavaMethod method, HotSpotInstalledCode code) {
        if (isEnabled()) {
            duration = System.nanoTime() - startTime;
            codeSize = (int) code.getCodeSize();
            memoryUsed = getThreadAllocatedBytes() - threadAllocatedBytesStart;
            if (current.get().getLast() != this) {
                throw new RuntimeException("mismatch in finish()");
            }
            current.get().removeLast();
        }
    }

    public static CompilationStatistics current() {
        return current.get().isEmpty() ? null : current.get().getLast();
    }

    public static CompilationStatistics create(OptionValues options, HotSpotResolvedJavaMethod method, boolean isOSR) {
        if (Options.UseCompilationStatistics.getValue(options)) {
            CompilationStatistics stats = new CompilationStatistics(method, isOSR);
            list.add(stats);
            current.get().addLast(stats);
            return stats;
        } else {
            return DUMMY;
        }
    }

    public boolean isEnabled() {
        return this != DUMMY;
    }

    @SuppressWarnings("deprecation")
    public static void clear(String dumpName) {
        try {
            ConcurrentLinkedDeque<CompilationStatistics> snapshot = list;
            long snapshotZeroTime = zeroTime;

            list = new ConcurrentLinkedDeque<>();
            zeroTime = System.nanoTime();

            Date now = new Date();
            String dateString = (now.getYear() + 1900) + "-" + (now.getMonth() + 1) + "-" + now.getDate() + "-" + now.getHours() + "" + now.getMinutes();

            dumpCompilations(snapshot, dumpName, dateString);

            try (FileOutputStream fos = new FileOutputStream("timeline_" + dateString + "_" + dumpName + ".csv", true); PrintStream out = new PrintStream(fos)) {

                long[] timeSpent = new long[10000];
                int maxTick = 0;
                for (CompilationStatistics stats : snapshot) {
                    long start = stats.startTime - snapshotZeroTime;
                    long duration = stats.duration;
                    if (start < 0) {
                        duration -= -start;
                        start = 0;
                    }

                    int tick = (int) (start / RESOLUTION);
                    long timeLeft = RESOLUTION - (start % RESOLUTION);

                    while (tick < timeSpent.length && duration > 0) {
                        if (tick > maxTick) {
                            maxTick = tick;
                        }
                        timeSpent[tick] += Math.min(timeLeft, duration);
                        duration -= timeLeft;
                        tick++;
                        timeLeft = RESOLUTION;
                    }
                }
                String timelineName = Services.getSavedProperties().get("stats.timeline.name");
                if (timelineName != null && !timelineName.isEmpty()) {
                    out.printf("%s%c", CSVUtil.Escape.escape(timelineName), CSVUtil.SEPARATOR);
                }
                for (int i = 0; i < maxTick; i++) {
                    out.printf("%d%c", normalize(timeSpent[i]), CSVUtil.SEPARATOR);
                }
                // print last column
                out.printf("%d", normalize(timeSpent[maxTick]));
                out.println();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long normalize(long time) {
        return time * 100 / RESOLUTION;
    }

    protected static void dumpCompilations(ConcurrentLinkedDeque<CompilationStatistics> snapshot, String dumpName, String dateString) throws IllegalAccessException, FileNotFoundException {
        String fileName = "compilations_" + dateString + "_" + dumpName + ".csv";
        char separator = '\t';
        try (PrintStream out = new PrintStream(fileName)) {
            // output the list of all compilations

            Field[] declaredFields = CompilationStatistics.class.getDeclaredFields();
            ArrayList<Field> fields = new ArrayList<>();
            for (Field field : declaredFields) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(NotReported.class)) {
                    fields.add(field);
                }
            }
            String format = CSVUtil.buildFormatString("%s", separator, fields.size());
            CSVUtil.Escape.println(out, separator, CSVUtil.QUOTE, CSVUtil.ESCAPE, format, fields.toArray());
            for (CompilationStatistics stats : snapshot) {
                Object[] values = new Object[fields.size()];
                for (int i = 0; i < fields.size(); i++) {
                    Field field = fields.get(i);
                    if (field.isAnnotationPresent(TimeValue.class)) {
                        double value = field.getLong(stats) / 1000000d;
                        values[i] = String.format(Locale.ENGLISH, "%.3f", value);
                    } else {
                        values[i] = field.get(stats);
                    }
                }
                CSVUtil.Escape.println(out, separator, CSVUtil.QUOTE, CSVUtil.ESCAPE, format, values);
            }
        }
    }
}
