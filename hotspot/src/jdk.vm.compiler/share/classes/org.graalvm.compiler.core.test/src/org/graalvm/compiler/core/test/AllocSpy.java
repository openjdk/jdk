/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import com.google.monitoring.runtime.instrumentation.Sampler;

/**
 * Tool for analyzing allocations within a scope using the
 * <a href="https://code.google.com/p/java-allocation-instrumenter/">Java Allocation
 * Instrumenter</a>. Allocation records are aggregated per stack trace at an allocation site. The
 * size of the stack trace is governed by the value of the "AllocSpy.ContextSize" system property
 * (default is 5).
 * <p>
 * Using this facility requires using -javaagent on the command line. For example:
 *
 * <pre>
 * mx --vm server unittest -javaagent:lib/java-allocation-instrumenter.jar -dsa -DAllocSpy.ContextSize=6 BC_iadd2
 * </pre>
 *
 * @see #SampleBytes
 * @see #SampleInstances
 * @see #HistogramLimit
 * @see #NameSize
 * @see #BarSize
 * @see #NumberSize
 */
public final class AllocSpy implements AutoCloseable {

    static ThreadLocal<AllocSpy> current = new ThreadLocal<>();

    private static final boolean ENABLED;

    static {
        boolean enabled = false;
        try {
            Field field = AllocationRecorder.class.getDeclaredField("instrumentation");
            field.setAccessible(true);
            enabled = field.get(null) != null;
        } catch (Exception e) {
        }
        ENABLED = enabled;
        if (ENABLED) {
            AllocationRecorder.addSampler(new GraalContextSampler());
        }
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    static String prop(String sfx) {
        return AllocSpy.class.getSimpleName() + "." + sfx;
    }

    /**
     * Determines if bytes per allocation site are recorded.
     */
    private static final boolean SampleBytes = parseBoolean(getProperty(prop("SampleBytes"), "true"));

    /**
     * Determines if allocations per allocation site are recorded.
     */
    private static final boolean SampleInstances = parseBoolean(getProperty(prop("SampleInstances"), "true"));

    /**
     * The size of context to record for each allocation site in terms of Graal frames.
     */
    private static final int ContextSize = getInteger(prop("ContextSize"), 5);

    /**
     * Only the {@code HistogramLimit} most frequent values are printed.
     */
    private static final int HistogramLimit = getInteger(prop("HistogramLimit"), 40);

    /**
     * The width of the allocation context column.
     */
    private static final int NameSize = getInteger(prop("NameSize"), 50);

    /**
     * The width of the histogram bar column.
     */
    private static final int BarSize = getInteger(prop("BarSize"), 100);

    /**
     * The width of the frequency column.
     */
    private static final int NumberSize = getInteger(prop("NumberSize"), 10);

    final Object name;
    final AllocSpy parent;
    final Map<String, CountedValue> bytesPerGraalContext = new HashMap<>();
    final Map<String, CountedValue> instancesPerGraalContext = new HashMap<>();

    public static AllocSpy open(Object name) {
        if (ENABLED) {
            return new AllocSpy(name);
        }
        return null;
    }

    private AllocSpy(Object name) {
        this.name = name;
        parent = current.get();
        current.set(this);
    }

    @Override
    public void close() {
        current.set(parent);
        PrintStream ps = System.out;
        ps.println("\n\nAllocation histograms for " + name);
        if (SampleBytes) {
            print(ps, bytesPerGraalContext, "BytesPerGraalContext", HistogramLimit, NameSize + 60, BarSize);
        }
        if (SampleInstances) {
            print(ps, instancesPerGraalContext, "InstancesPerGraalContext", HistogramLimit, NameSize + 60, BarSize);
        }
    }

    private static void printLine(PrintStream printStream, char c, int lineSize) {
        char[] charArr = new char[lineSize];
        Arrays.fill(charArr, c);
        printStream.printf("%s%n", new String(charArr));
    }

    private static void print(PrintStream ps, Map<String, CountedValue> map, String name, int limit, int nameSize, int barSize) {
        if (map.isEmpty()) {
            return;
        }

        List<CountedValue> list = new ArrayList<>(map.values());
        Collections.sort(list);

        // Sum up the total number of elements.
        int total = 0;
        for (CountedValue cv : list) {
            total += cv.getCount();
        }

        // Print header.
        ps.printf("%s has %d unique elements and %d total elements:%n", name, list.size(), total);

        int max = list.get(0).getCount();
        final int lineSize = nameSize + NumberSize + barSize + 10;
        printLine(ps, '-', lineSize);
        String formatString = "| %-" + nameSize + "s | %-" + NumberSize + "d | %-" + barSize + "s |\n";
        for (int i = 0; i < list.size() && i < limit; ++i) {
            CountedValue cv = list.get(i);
            int value = cv.getCount();
            char[] bar = new char[(int) (((double) value / (double) max) * barSize)];
            Arrays.fill(bar, '=');
            String[] lines = String.valueOf(cv.getValue()).split("\\n");

            String objectString = lines[0];
            if (objectString.length() > nameSize) {
                objectString = objectString.substring(0, nameSize - 3) + "...";
            }
            ps.printf(formatString, objectString, value, new String(bar));
            for (int j = 1; j < lines.length; j++) {
                String line = lines[j];
                if (line.length() > nameSize) {
                    line = line.substring(0, nameSize - 3) + "...";
                }
                ps.printf("|   %-" + (nameSize - 2) + "s | %-" + NumberSize + "s | %-" + barSize + "s |%n", line, " ", " ");

            }
        }
        printLine(ps, '-', lineSize);
    }

    CountedValue bytesPerGraalContext(String context) {
        return getCounter(context, bytesPerGraalContext);
    }

    CountedValue instancesPerGraalContext(String context) {
        return getCounter(context, instancesPerGraalContext);
    }

    protected static CountedValue getCounter(String desc, Map<String, CountedValue> map) {
        CountedValue count = map.get(desc);
        if (count == null) {
            count = new CountedValue(0, desc);
            map.put(desc, count);
        }
        return count;
    }

    private static final String[] Excluded = {AllocSpy.class.getName(), AllocationRecorder.class.getName()};

    private static boolean excludeFrame(String className) {
        for (String e : Excluded) {
            if (className.startsWith(e)) {
                return true;
            }
        }
        return false;
    }

    static class GraalContextSampler implements Sampler {

        @Override
        public void sampleAllocation(int count, String desc, Object newObj, long size) {
            AllocSpy scope = current.get();
            if (scope != null) {
                StringBuilder sb = new StringBuilder(200);
                Throwable t = new Throwable();
                int remainingGraalFrames = ContextSize;
                for (StackTraceElement e : t.getStackTrace()) {
                    if (remainingGraalFrames < 0) {
                        break;
                    }
                    String className = e.getClassName();
                    boolean isGraalFrame = className.contains(".graal.");
                    if (sb.length() != 0) {
                        append(sb.append('\n'), e);
                    } else {
                        if (!excludeFrame(className)) {
                            sb.append("type=").append(desc);
                            if (count != -1) {
                                sb.append('[').append(count).append(']');
                            }
                            append(sb.append('\n'), e);
                        }
                    }
                    if (isGraalFrame) {
                        remainingGraalFrames--;
                    }
                }
                String context = sb.toString();
                if (SampleBytes) {
                    scope.bytesPerGraalContext(context).add((int) size);
                }
                if (SampleInstances) {
                    scope.instancesPerGraalContext(context).inc();
                }
            }
        }

        protected StringBuilder append(StringBuilder sb, StackTraceElement e) {
            String className = e.getClassName();
            int period = className.lastIndexOf('.');
            if (period != -1) {
                sb.append(className, period + 1, className.length());
            } else {
                sb.append(className);
            }
            sb.append('.').append(e.getMethodName());
            if (e.isNativeMethod()) {
                sb.append("(Native Method)");
            } else if (e.getFileName() != null && e.getLineNumber() >= 0) {
                sb.append('(').append(e.getFileName()).append(':').append(e.getLineNumber()).append(")");
            } else {
                sb.append("(Unknown Source)");
            }
            return sb;
        }
    }

    /**
     * A value and a frequency. The ordering imposed by {@link #compareTo(CountedValue)} places
     * values with higher frequencies first.
     */
    static class CountedValue implements Comparable<CountedValue> {

        private int count;
        private final Object value;

        CountedValue(int count, Object value) {
            this.count = count;
            this.value = value;
        }

        @Override
        public int compareTo(CountedValue o) {
            if (count < o.count) {
                return 1;
            } else if (count > o.count) {
                return -1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return count + " -> " + value;
        }

        public void inc() {
            count++;
        }

        public void add(int n) {
            count += n;
        }

        public int getCount() {
            return count;
        }

        public Object getValue() {
            return value;
        }
    }
}
