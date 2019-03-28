/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.debug;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.debug.DynamicCounterNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

//JaCoCo Exclude

/**
 * This class contains infrastructure to maintain counters based on {@link DynamicCounterNode}s. The
 * infrastructure is enabled by specifying either the GenericDynamicCounters or
 * BenchmarkDynamicCounters option.
 * <p>
 *
 * The counters are kept in a special area allocated for each native JavaThread object, and the
 * number of counters is configured using {@code -XX:JVMCICounterSize=value}.
 * {@code -XX:+/-JVMCICountersExcludeCompiler} configures whether to exclude compiler threads
 * (defaults to true).
 *
 * The subsystems that use the logging need to have their own options to turn on the counters, and
 * insert DynamicCounterNodes when they're enabled.
 *
 * Counters will be displayed as a rate (per second) if their group name starts with "~", otherwise
 * they will be displayed as a total number.
 *
 * See <a href="BenchmarkDynamicCountersHelp.txt">here</a> for a detailed example of how to use
 * benchmark counters.
 */
public class BenchmarkCounters {

    public static class Options {

        //@formatter:off
        @Option(help = "Turn on the benchmark counters, and displays the results on VM shutdown", type = OptionType.Debug)
        public static final OptionKey<Boolean> GenericDynamicCounters = new OptionKey<>(false);
        @Option(help = "Turn on the benchmark counters, and displays the results every n milliseconds", type = OptionType.Debug)
        public static final OptionKey<Integer> TimedDynamicCounters = new OptionKey<>(-1);

        @Option(help = "file:doc-files/BenchmarkDynamicCountersHelp.txt", type = OptionType.Debug)
        public static final OptionKey<String> BenchmarkDynamicCounters = new OptionKey<>(null);
        @Option(help = "Use grouping separators for number printing", type = OptionType.Debug)
        public static final OptionKey<Boolean> DynamicCountersPrintGroupSeparator = new OptionKey<>(true);
        @Option(help = "File to which benchmark counters are dumped. A CSV format is used if the file ends with .csv " +
                       "otherwise a more human readable format is used. The fields in the CSV format are: " +
                       "category, group, name, value", type = OptionType.Debug)
        public static final OptionKey<String> BenchmarkCountersFile = new OptionKey<>(null);
        @Option(help = "Dump dynamic counters", type = OptionType.Debug)
        public static final OptionKey<Boolean> BenchmarkCountersDumpDynamic = new OptionKey<>(true);
        @Option(help = "Dump static counters", type = OptionType.Debug)
        public static final OptionKey<Boolean> BenchmarkCountersDumpStatic = new OptionKey<>(false);
        @Option(help = "file:doc-files/AbortOnBenchmarkCounterOverflowHelp.txt", type = OptionType.Debug)
        public static final OptionKey<Boolean> AbortOnBenchmarkCounterOverflow = new OptionKey<>(false);
        //@formatter:on
    }

    public static boolean enabled = false;

    private static class Counter {
        public final int index;
        public final String group;
        public final AtomicLong staticCounters;

        Counter(int index, String group, AtomicLong staticCounters) {
            this.index = index;
            this.group = group;
            this.staticCounters = staticCounters;
        }
    }

    public static final ConcurrentHashMap<String, Counter> counterMap = new ConcurrentHashMap<>();
    public static long[] delta;

    public static int getIndexConstantIncrement(String name, String group, GraalHotSpotVMConfig config, long increment) {
        Counter counter = getCounter(name, group, config);
        counter.staticCounters.addAndGet(increment);
        return counter.index;
    }

    public static int getIndex(String name, String group, GraalHotSpotVMConfig config) {
        Counter counter = getCounter(name, group, config);
        return counter.index;
    }

    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION", justification = "concurrent abstraction calls are in synchronized block")
    private static Counter getCounter(String name, String group, GraalHotSpotVMConfig config) throws GraalError {
        if (!enabled) {
            throw new GraalError("cannot access count index when counters are not enabled: " + group + ", " + name);
        }
        String nameGroup = name + "#" + group;
        Counter counter = counterMap.get(nameGroup);
        if (counter == null) {
            synchronized (BenchmarkCounters.class) {
                counter = counterMap.get(nameGroup);
                if (counter == null) {
                    counter = new Counter(counterMap.size(), group, new AtomicLong());
                    counterMap.put(nameGroup, counter);
                }
            }
        }
        assert counter.group.equals(group) : "mismatching groups: " + counter.group + " vs. " + group;
        int countersSize = config.jvmciCountersSize;
        if (counter.index >= countersSize) {
            throw new GraalError("too many counters, reduce number of counters or increase -XX:JVMCICounterSize=... (current value: " + countersSize + ")");
        }
        return counter;
    }

    private static synchronized void dump(OptionValues options, PrintStream out, double seconds, long[] counters, int maxRows) {
        if (!counterMap.isEmpty()) {
            try (Dumper dumper = Dumper.getDumper(options, out, counterMap.size(), seconds, maxRows)) {
                TreeSet<String> set = new TreeSet<>();
                counterMap.forEach((nameGroup, counter) -> set.add(counter.group));
                for (String group : set) {
                    if (group != null) {
                        if (Options.BenchmarkCountersDumpStatic.getValue(options)) {
                            dumper.dumpCounters(true, group, collectStaticCounters(), counterMap.entrySet(), options);
                        }
                        if (Options.BenchmarkCountersDumpDynamic.getValue(options)) {
                            dumper.dumpCounters(false, group, collectDynamicCounters(counters), counterMap.entrySet(), options);
                        }
                    }
                }
            }

            clear(counters);
        }
    }

    private static synchronized long[] collectDynamicCounters(long[] counters) {
        long[] array = counters.clone();
        for (int i = 0; i < array.length; i++) {
            array[i] -= delta[i];
        }
        return array;
    }

    private static synchronized long[] collectStaticCounters() {
        long[] array = new long[counterMap.size()];
        for (Counter counter : counterMap.values()) {
            array[counter.index] = counter.staticCounters.get();
        }
        return array;
    }

    private static synchronized void clear(long[] counters) {
        delta = counters;
    }

    private static boolean shouldDumpComputerReadable(OptionValues options) {
        String dumpFile = Options.BenchmarkCountersFile.getValue(options);
        return dumpFile != null && (dumpFile.endsWith(".csv") || dumpFile.endsWith(".CSV"));
    }

    private abstract static class Dumper implements AutoCloseable {
        public static Dumper getDumper(OptionValues options, PrintStream out, int counterSize, double second, int maxRows) {
            Dumper dumper = shouldDumpComputerReadable(options) ? new ComputerReadableDumper(out) : new HumanReadableDumper(out, second, maxRows);
            dumper.start(counterSize);
            return dumper;
        }

        protected final PrintStream out;

        private Dumper(PrintStream out) {
            this.out = out;
        }

        protected abstract void start(int size);

        public abstract void dumpCounters(boolean staticCounter, String group, long[] array, Set<Entry<String, Counter>> counterEntrySet, OptionValues options);

        @Override
        public abstract void close();

    }

    private static String getName(String nameGroup, String group) {
        return nameGroup.substring(0, nameGroup.length() - group.length() - 1);
    }

    private static long percentage(long counter, long sum) {
        return (counter * 200 + 1) / sum / 2;
    }

    private static class HumanReadableDumper extends Dumper {
        private final double seconds;
        private final int maxRows;

        HumanReadableDumper(PrintStream out, double seconds, int maxRows) {
            super(out);
            this.seconds = seconds;
            this.maxRows = maxRows;
        }

        @Override
        public void start(int size) {
            out.println("====== dynamic counters (" + size + " in total) ======");
        }

        @Override
        public void close() {
            out.println("============================");
        }

        @Override
        public void dumpCounters(boolean staticCounter, String group, long[] array, Set<Entry<String, Counter>> counterEntrySet, OptionValues options) {
            // sort the counters by putting them into a sorted map
            TreeMap<Long, String> sorted = new TreeMap<>();
            long sum = 0;
            for (Map.Entry<String, Counter> entry : counterEntrySet) {
                Counter counter = entry.getValue();
                int index = counter.index;
                if (counter.group.equals(group)) {
                    sum += array[index];
                    sorted.put(array[index] * array.length + index, getName(entry.getKey(), group));
                }
            }

            if (sum > 0) {
                long cutoff = sorted.size() < 10 ? 1 : Math.max(1, sum / 100);
                int cnt = sorted.size();

                // remove everything below cutoff and keep at most maxRows
                Iterator<Map.Entry<Long, String>> iter = sorted.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Long, String> entry = iter.next();
                    long counter = entry.getKey() / array.length;
                    if (counter < cutoff || cnt > maxRows) {
                        iter.remove();
                    }
                    cnt--;
                }

                String numFmt = Options.DynamicCountersPrintGroupSeparator.getValue(options) ? "%,19d" : "%19d";
                if (staticCounter) {
                    out.println("=========== " + group + " (static counters):");
                    for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                        long counter = entry.getKey() / array.length;
                        out.format(Locale.US, numFmt + " %3d%%  %s\n", counter, percentage(counter, sum), entry.getValue());
                    }
                    out.format(Locale.US, numFmt + " total\n", sum);
                } else {
                    if (group.startsWith("~")) {
                        out.println("=========== " + group + " (dynamic counters), time = " + seconds + " s:");
                        for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                            long counter = entry.getKey() / array.length;
                            out.format(Locale.US, numFmt + "/s %3d%%  %s\n", (long) (counter / seconds), percentage(counter, sum), entry.getValue());
                        }
                        out.format(Locale.US, numFmt + "/s total\n", (long) (sum / seconds));
                    } else {
                        out.println("=========== " + group + " (dynamic counters):");
                        for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                            long counter = entry.getKey() / array.length;
                            out.format(Locale.US, numFmt + " %3d%%  %s\n", counter, percentage(counter, sum), entry.getValue());
                        }
                        out.format(Locale.US, numFmt + " total\n", sum);
                    }
                }
            }
        }
    }

    private static final String CSV_FMT = CSVUtil.buildFormatString("%s", "%s", "%s", "%d");

    private static class ComputerReadableDumper extends Dumper {

        ComputerReadableDumper(PrintStream out) {
            super(out);
        }

        @Override
        public void start(int size) {
            // do nothing
        }

        @Override
        public void close() {
            // do nothing
        }

        @Override
        public void dumpCounters(boolean staticCounter, String group, long[] array, Set<Entry<String, Counter>> counterEntrySet, OptionValues options) {
            String category = staticCounter ? "static counters" : "dynamic counters";
            for (Map.Entry<String, Counter> entry : counterEntrySet) {
                Counter counter = entry.getValue();
                if (counter.group.equals(group)) {
                    String name = getName(entry.getKey(), group);
                    int index = counter.index;
                    long value = array[index];
                    CSVUtil.Escape.println(out, CSV_FMT, category, group, name, value);
                }
            }
        }
    }

    private abstract static class CallbackOutputStream extends OutputStream {

        protected final PrintStream delegate;
        private final byte[][] patterns;
        private final int[] positions;

        CallbackOutputStream(PrintStream delegate, String... patterns) {
            this.delegate = delegate;
            this.positions = new int[patterns.length];
            this.patterns = new byte[patterns.length][];
            for (int i = 0; i < patterns.length; i++) {
                this.patterns[i] = patterns[i].getBytes();
            }
        }

        protected abstract void patternFound(int index);

        @Override
        public void write(int b) throws IOException {
            try {
                delegate.write(b);
                for (int i = 0; i < patterns.length; i++) {
                    int j = positions[i];
                    byte[] cs = patterns[i];
                    byte patternChar = cs[j];
                    if (patternChar == '~' && Character.isDigit(b)) {
                        // nothing to do...
                    } else {
                        if (patternChar == '~') {
                            patternChar = cs[++positions[i]];
                        }
                        if (b == patternChar) {
                            positions[i]++;
                        } else {
                            positions[i] = 0;
                        }
                    }
                    if (positions[i] == patterns[i].length) {
                        positions[i] = 0;
                        patternFound(i);
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace(delegate);
                throw e;
            }
        }
    }

    public static void initialize(final HotSpotJVMCIRuntime jvmciRuntime, OptionValues options) {
        final class BenchmarkCountersOutputStream extends CallbackOutputStream {

            private long startTime;
            private boolean running;
            private boolean waitingForEnd;

            private BenchmarkCountersOutputStream(PrintStream delegate, String start, String end) {
                super(delegate, new String[]{"\n", end, start});
            }

            @Override
            protected void patternFound(int index) {
                switch (index) {
                    case 2:
                        startTime = System.nanoTime();
                        BenchmarkCounters.clear(jvmciRuntime.collectCounters());
                        running = true;
                        break;
                    case 1:
                        if (running) {
                            waitingForEnd = true;
                        }
                        break;
                    case 0:
                        if (waitingForEnd) {
                            waitingForEnd = false;
                            running = false;
                            try (PrintStreamScope scope = getPrintStream(options)) {
                                BenchmarkCounters.dump(options, scope.out, (System.nanoTime() - startTime) / 1000000000d, jvmciRuntime.collectCounters(), 100);
                            }
                        }
                        break;
                }
            }
        }

        if (Options.BenchmarkDynamicCounters.getValue(options) != null) {
            String[] arguments = Options.BenchmarkDynamicCounters.getValue(options).split(",");
            if (arguments.length == 0 || (arguments.length % 3) != 0) {
                throw new GraalError("invalid arguments to BenchmarkDynamicCounters: (err|out),start,end,(err|out),start,end,... (~ matches multiple digits)");
            }
            for (int i = 0; i < arguments.length; i += 3) {
                if (arguments[i].equals("err")) {
                    System.setErr(new PrintStream(new BenchmarkCountersOutputStream(System.err, arguments[i + 1], arguments[i + 2])));
                } else if (arguments[i].equals("out")) {
                    System.setOut(new PrintStream(new BenchmarkCountersOutputStream(System.out, arguments[i + 1], arguments[i + 2])));
                } else {
                    throw new GraalError("invalid arguments to BenchmarkDynamicCounters: err|out");
                }
            }
            enabled = true;
        }
        if (Options.GenericDynamicCounters.getValue(options)) {
            enabled = true;
        }
        if (Options.TimedDynamicCounters.getValue(options) > 0) {
            Thread thread = new Thread() {
                long lastTime = System.nanoTime();

                @Override
                public void run() {
                    try (PrintStreamScope scope = getPrintStream(options)) {
                        while (true) {
                            try {
                                Thread.sleep(Options.TimedDynamicCounters.getValue(options));
                            } catch (InterruptedException e) {
                            }
                            long time = System.nanoTime();
                            dump(options, scope.out, (time - lastTime) / 1000000000d, jvmciRuntime.collectCounters(), 10);
                            lastTime = time;
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
            enabled = true;
        }
        if (enabled) {
            clear(jvmciRuntime.collectCounters());
        }
    }

    public static void shutdown(HotSpotJVMCIRuntime jvmciRuntime, OptionValues options, long compilerStartTime) {
        if (Options.GenericDynamicCounters.getValue(options)) {
            try (PrintStreamScope scope = getPrintStream(options)) {
                dump(options, scope.out, (System.nanoTime() - compilerStartTime) / 1000000000d, jvmciRuntime.collectCounters(), 100);
            }
        }
    }

    static class PrintStreamScope implements AutoCloseable {
        final PrintStream out;

        PrintStreamScope(OptionValues options) {
            PrintStream ps = TTY.out;
            if (Options.BenchmarkCountersFile.getValue(options) != null) {
                try {
                    File file = new File(Options.BenchmarkCountersFile.getValue(options));
                    TTY.println("Writing benchmark counters to '%s'", file.getAbsolutePath());
                    ps = new PrintStream(file);
                } catch (IOException e) {
                    TTY.out().println(e.getMessage());
                    TTY.out().println("Fallback to default");
                }
            }
            this.out = ps;
        }

        @Override
        public void close() {
            if (out != TTY.out) {
                this.out.close();
            }
        }
    }

    private static PrintStreamScope getPrintStream(OptionValues options) {
        return new PrintStreamScope(options);
    }
}
