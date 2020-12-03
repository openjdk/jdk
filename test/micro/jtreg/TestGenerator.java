/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.*;

/**
 * @test THIS IS NOT A TEST.
 * This is a test generator, should be run only when microbenchmarks are changed.
 *
 * @library /test/lib /test/hotspot/jtreg /
 * @run main/timeout=0 TestGenerator
 */

/**
 * Use microbenchmarks corpus to generates jtreg tests in 'test.src' or current
 * directory.
 *
 * Each generated jtreg test file will contain several tests. Subdirectories are
 * used to allow running all tests from a file using command line. 'app', 'api',
 * 'jvm', ..., and 'other' tests will be generated (see full list of tests defined in MicrosGroup).
 *
 * This generator depends on testlibrary, therefore it should be compiled and
 * added to classpath. One can replace @notest by @test in jtreg test
 * description above to run this class with jtreg.
 */
public class TestGenerator {
    private static final String COPYRIGHT;
    private static final String MICROBENCHMARK_JAR_FILE;
    
    static {
        String years;
        final int firstYear = 2020;
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        if (firstYear < currentYear) {
            years = String.format("%d, %d", firstYear, currentYear);
        } else {
            years = "" + firstYear;
        }
        COPYRIGHT = String.format(
                  "/*\n"
                + "  * Copyright (c) %s, Oracle and/or its affiliates. All rights reserved.\n"
                + "  * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n"
                + "  *\n"
                + "  * This code is free software; you can redistribute it and/or modify it\n"
                + "  * under the terms of the GNU General Public License version 2 only, as\n"
                + "  * published by the Free Software Foundation.\n"
                + "  *\n"
                + "  * This code is distributed in the hope that it will be useful, but WITHOUT\n"
                + "  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n"
                + "  * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n"
                + "  * version 2 for more details (a copy is included in the LICENSE file that\n"
                + "  * accompanied this code).\n"
                + "  *\n"
                + "  * You should have received a copy of the GNU General Public License version\n"
                + "  * 2 along with this work; if not, write to the Free Software Foundation,\n"
                + "  * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n"
                + "  *\n"
                + "  * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n"
                + "  * or visit www.oracle.com if you need additional information or have any\n"
                + "  * questions.\n"
                + "  */\n\n", years);
                
                
        MICROBENCHMARK_JAR_FILE = System.getenv("TEST_IMAGE_MICROBENCHMARK_JAR");
        if (MICROBENCHMARK_JAR_FILE == null || MICROBENCHMARK_JAR_FILE.isEmpty())
            throw new RuntimeException("ERROR: path to microbenchmarks jar file is not specified, please set TEST_IMAGE_MICROBENCHMARK_JAR environment variable.");
    }

    private static Map<String, String> EXTRAS = Map.of(
            // there is no NativePRNG on windows
            "org.openjdk.bench.javax.crypto.full.generated.SecureRandomBench.nextBytes", " * @requires os.family != \"windows\"\n",
            "org.openjdk.bench.javax.crypto.small.generated.SecureRandomBench.nextBytes",  " * @requires os.family != \"windows\"\n"
    );

    private static final long DEF_JTREG_TIMEOUT = 120L;  // 2 minutes
    private static final long FILE_TIME_LIMIT   = 3600L; // 1 hour

    private enum MicrosGroup {
        JAVA_IO("java.io"),
        JAVA_LANG("java.lang"),
        JAVA_MATH("java.math"),
        JAVA_NET("java.net"),
        JAVA_NIO("java.nio"),
        JAVA_SECURITY("java.security"),
        JAVA_TEXT("java.text"),
        JAVA_UTIL("java.util"),
        JAVAX_CRYPTO("javax.crypto"),
        JAVAX_TOOLS("javax.tools"),
        JAVAX_XML("javax.xml"),
        JDK_INCUBATOR_FOREIGN("jdk.incubator.foreign"),
        VM_COMPILER("vm.compiler"),
        VM_GC("vm.gc"),
        VM_LAMBDA("vm.lambda"),
        VM_LANG("vm.lang"),
        OTHER("other", MicrosGroup.otherFilter());

        private final String groupName;
        private final Predicate<String> filter;

        MicrosGroup(String groupName, Predicate<String> filter) {
            this.groupName = groupName;
            this.filter = filter;
        }

        MicrosGroup(String groupName) {
            this(groupName, MicrosGroup.nameFilter(groupName));
        }

        private static Predicate<String> nameFilter(String group) {
            return s -> s.startsWith("org.openjdk.bench." + group + ".");
        }

        private static Predicate<String> otherFilter() {
            return (s) -> {
                for (MicrosGroup g : EnumSet.complementOf(EnumSet.of(OTHER))) {
                    if (g.filter.test(s)) {
                        return false;
                    }
                }
                return true;
            };
        }
    }

    private static class MicroMetadata {
        int subtestsCount = 0;
        long execTime = 0;
        boolean testGenerated = false; // this flag is used mostly for debugging purpose

        MicroMetadata(int subtestsCount, int execTime, boolean testGenerated) {
            this.subtestsCount = subtestsCount;
            this.execTime = execTime;
            this.testGenerated = testGenerated;
        }
    }

    // The class contains information about jdk microbenchmarks used to generate tests for
    private static class MicrosJdk {
        private static final Map<String, MicroMetadata> micros = new HashMap<>();

        static void init() {
            Path path = Paths.get(MICROBENCHMARK_JAR_FILE);
            loadBenchmarks(path);
            loadTimes(path);
        }

        // Load list of microbenchmarks from jar file
        private static void loadBenchmarks(Path mbPath) {
            // "param" lines format: '  param "<name>" = {(<value>(, <value>)*)?}', e.g.:
            // '  param "size" = {64, 16777216}'
            // '  param "provider" = {}'
            // '  param "keyGen" = {EC}'
            Pattern MB_PARAM_LINE = Pattern.compile(
                    " {2}param \"[^\"]+\" = \\{([^{},]+(, [^{},]+)*)?\\}");

            try {
                Path output = Files.createTempFile("micros_jdk", ".out");
                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                        "--add-opens", "java.base/java.io=ALL-UNNAMED",
                        "-jar",
                        mbPath.toAbsolutePath().toString(),
                        "-lp",
                        // exclude "script" benchmarks as there is no JS engine anymore
                        "-e", ".*\\.script\\..*");
                pb.redirectOutput(output.toFile());
                new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);

                BufferedReader reader = Files.newBufferedReader(output);
                // skip 1st line, which is 'Benchmarks:'
                reader.readLine();
                String line = reader.readLine();
                while (line != null) {
                    String mbName = line;
                    int count = 1;
                    line = reader.readLine();
                    // JMH makes cartesian product of each param, we calculate
                    // number of tuples by multiplying number of ',' plus 1 from
                    // each param line, e.g. we will get 6 ((2+1) * (1+1)) for
                    // org.openjdk.strings.indexof.IndexOfString.img2_base2__img2
                    //   param "size" = {1, 64, 4096}v
                    //   param "imageSize" = {1, 64}
                    while (line != null && line.startsWith(" ")) {
                        if (!MB_PARAM_LINE.matcher(line).matches()) {
                            throw new Error("Unexpected line : " + line);
                        }
                        count *= line.length() - line.replace(",", "").length() + 1;
                        line = reader.readLine();
                    }
                    micros.put(mbName, new MicroMetadata(count, 0,false));
                }
            } catch (Exception e) {
                throw new Error("Can not get list of jdk microbenchmarks", e);
            }
        }

        // Load microbenchmarks execution time if available
        private static void loadTimes(Path mbPath) {
            try {
                ClassLoader cl = new URLClassLoader(new URL[]{mbPath.toUri().toURL()});
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                Class<?> defaultsClass = cl.loadClass("org.openjdk.jmh.runner.Defaults");
                Object defaultWarmupTime = defaultsClass.getField("WARMUP_TIME").get(null);
                Object defaultWarmupIterations = defaultsClass.getField("WARMUP_ITERATIONS").get(null);
                Object defaultMeasurementTime = defaultsClass.getField("MEASUREMENT_TIME").get(null);
                Object defaultMeasurementIterations = defaultsClass.getField("MEASUREMENT_ITERATIONS").get(null);

                Class<?> bListEntryClass = cl.loadClass("org.openjdk.jmh.runner.BenchmarkListEntry");
                // bListEntryCtor(o) := new BenchmarkListEntry(o);
                MethodHandle bListEntryCtor = lookup.findConstructor(bListEntryClass,
                        MethodType.methodType(void.class, String.class));
                // bListEntryGetUsername(o) := o.getUsername();
                MethodHandle bListEntryGetUsername = lookup.findVirtual(bListEntryClass,
                        "getUsername", MethodType.methodType(String.class));

                Class<?> optionalClass = cl.loadClass("org.openjdk.jmh.util.Optional");
                // optionalOrElse(a,b) := a.orElse(b);
                MethodHandle optionalOrElse = lookup.findVirtual(optionalClass,
                        "orElse", MethodType.methodType(Object.class, Object.class));


                Class<?> timeValueClass = cl.loadClass("org.openjdk.jmh.runner.options.TimeValue");
                // timeValueToSeconds(o) := o.convertTo(TimeUnit.SECONDS);
                MethodHandle timeValueToSeconds = MethodHandles.insertArguments(
                        lookup.findVirtual(timeValueClass,
                                "convertTo", MethodType.methodType(long.class, TimeUnit.class)),
                        1, TimeUnit.SECONDS);

                // getWarmupIterations(o) := o.getWarmupIterations().orElse(Defaults.WARMUP_ITERATIONS);
                MethodHandle getWarmupIterations = MethodHandles.filterReturnValue(
                        lookup.findVirtual(bListEntryClass,
                                "getWarmupIterations", MethodType.methodType(optionalClass)),
                        MethodHandles.insertArguments(optionalOrElse, 1, defaultWarmupIterations));

                // getWarmupTime(o) := timeValueToSeconds(o.getWarmupTime().orElse(Defaults.WARMUP_TIME));
                MethodHandle getWarmupTime = MethodHandles.filterReturnValue(
                        MethodHandles.filterReturnValue(
                                lookup.findVirtual(bListEntryClass,
                                        "getWarmupTime", MethodType.methodType(optionalClass)),
                                MethodHandles.insertArguments(optionalOrElse, 1, defaultWarmupTime))
                                .asType(MethodType.methodType(timeValueClass, bListEntryClass)),
                        timeValueToSeconds);

                // getMeasurementIterations(o) := o.getMeasurementIterations().orElse(Defaults.MEASUREMENT_ITERATIONS);
                MethodHandle getMeasurementIterations = MethodHandles.filterReturnValue(
                        lookup.findVirtual(bListEntryClass,
                                "getMeasurementIterations", MethodType.methodType(optionalClass)),
                        MethodHandles.insertArguments(optionalOrElse, 1, defaultMeasurementIterations));

                // getMeasurementTime(o) := timeValueToSeconds(o.getMeasurementTime().orElse(Defaults.MEASUREMENT_TIME));
                MethodHandle getMeasurementTime = MethodHandles.filterReturnValue(
                        MethodHandles.filterReturnValue(
                                lookup.findVirtual(bListEntryClass, "getMeasurementTime", MethodType.methodType(optionalClass)),
                                MethodHandles.insertArguments(optionalOrElse, 1, defaultMeasurementTime))
                                .asType(MethodType.methodType(timeValueClass, bListEntryClass)),
                        timeValueToSeconds);

                Class<?> bListClass = cl.loadClass("org.openjdk.jmh.runner.BenchmarkList");
                String list = (String) bListClass.getField("BENCHMARK_LIST").get(null);
                list = list.startsWith("/") ? list.substring(1) : list;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(cl.getResourceAsStream(list)))) {
                    // can't use BenchmarkList::readBenchmarkList as it doesn't skip lines which starts w/ # or are empty
                    for (String line = br.readLine(); line != null; line = br.readLine()) {
                        if (line.startsWith("#") || line.trim().isEmpty()) {
                            continue;
                        }
                        // b = new BenchmarkListEntry(line);
                        Object b = bListEntryCtor.invoke(line);
                        // time = b.getWarmupIterations() * b.getWarmupTime().convertTo(TimeUnit.SECONDS)
                        //        + b.getMeasurementIterations() * b.getMeasurementTime().convertTo(TimeUnit.SECONDS)
                        long time = (long) getWarmupTime.invoke(b) * (long) getWarmupIterations.invoke(b)
                                + (long) getMeasurementTime.invoke(b) * (long) getMeasurementIterations.invoke(b);
                        // benchmarkTimes.put(b.getUsername(), time);
                        String mbName = (String) bListEntryGetUsername.invoke(b);
                        MicrosJdk.micros.get(mbName).execTime = time;
                    }
                }
            } catch (Throwable t) {
                throw new Error("Failed to get execution times from " + MICROBENCHMARK_JAR_FILE, t);
            }

            System.out.println("INFO: Tests with timeout more than default one: ");
            MicrosJdk.micros.entrySet().stream().
                    filter(entry -> entry.getValue().execTime > DEF_JTREG_TIMEOUT).
                    forEach(entry -> System.out.println("\t" + entry.getKey()));

            long totalTime = 0;
            totalTime = MicrosJdk.micros.entrySet().stream().
                    mapToLong(x -> x.getValue().execTime * x.getValue().subtestsCount).sum();
            System.out.println("INFO: total execution time of all tests: " + totalTime + " secs");
        }

        // Returns true if the microbenchmark is present
        private static boolean contains(String name) {
            return micros.containsKey(name);
        }

        // Mark microbenchmark in case the test was generated (for debug purpose)
        private static void markGenerated(String mbName) {
            micros.get(mbName).testGenerated = true;
        }

        // Calculates timeout factor based on test execution time and default jtreg timeout
        private static int getTimeoutFactor(String name) {
            int timeoutFactor = 1;

            if (micros.get(name).execTime > DEF_JTREG_TIMEOUT) {
                timeoutFactor = (int)Math.ceil(micros.get(name).execTime / (double)DEF_JTREG_TIMEOUT);
            }

            return timeoutFactor;
        }

        // Returns the number of sub-tests for particular benchmark
        private static int getTestsCount(String mbName) {
            return micros.get(mbName).subtestsCount;
        }

        // Returns list of benchmarks matching particular group
        private static List<String> getTestsPerGroup(MicrosGroup group) {
            List<String> groupMicros = MicrosJdk.micros.keySet()
                    .stream()
                    .filter(s -> group.filter.test(s))
                    .sorted()
                    .collect(Collectors.toList());
            return groupMicros;
        }

        // Prints tests which take more than default jtreg timeout
        private static void printLongRunning() {
            micros.entrySet()
             .stream()
             .filter(e -> e.getValue().execTime > DEF_JTREG_TIMEOUT || e.getValue().subtestsCount > 1)
             .sorted(Map.Entry.comparingByKey())
             .peek(e -> System.out.println("Long running benchmark: " + e.getKey() +
                     ", execTime=" + e.getValue().execTime +
                     ", timeoutFactor=" + getTimeoutFactor(e.getKey()) +
                     ", subTests=" + getTestsCount(e.getKey())))
             .count();
        }
    }

    // Generates jtreg test description for given benchmark name
    private static String testDescription(String name, int timeoutFactor) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
          .append("/**\n")
          .append(" * @test ").append(name).append("\n")
          .append(" * @library /test/lib /\n");
        String extra = EXTRAS.get(name);
        if (extra != null) {
            sb.append(extra);
        }
        sb.append(" * @run driver");

        if (timeoutFactor > 1) {
            // allocate timeout in case the test increases default one or there are more than 1 param
            sb.append("/timeout=").append(DEF_JTREG_TIMEOUT * timeoutFactor);
        }

        sb.append(" ").append(MicroRunner.class.getName())
          // run microbenchmark one time
          .append(" -f 1");
        if (!MicrosJdk.contains(name)) {
            // fail fast -- stop execution if there is an exception
            // can't be done for jdk6 micros as some of them are obsolete
            sb.append(" -foe true");
        }

        sb.append(" ").append(name).append("\n")
          .append(" */\n");

        return sb.toString();
    }

    // Generates tests for given microbenchmark group
    private static void generate(MicrosGroup group) throws Exception {
        String root = Utils.TEST_SRC + File.separator + "generated";
        Path testDir = Paths.get(root)
                            .resolve(group.groupName.replace(".", File.separator));

        if (testDir.toFile().mkdirs() && !Files.exists(testDir)) {
            throw new Error("Can not create directories for " + testDir);
        }

        final long maxTestsPerFile = FILE_TIME_LIMIT / DEF_JTREG_TIMEOUT;
        int fileIndex = 0;
        long tests = 0;

        // resulting test files will be unbalanced. if/when it becomes a problem,
        // a more sophisticated solution should be implemented.
        PrintStream ps = null;
        try {
            ps = openTestFile(testDir, fileIndex);

            List<String> groupMicros = MicrosJdk.getTestsPerGroup(group);
            System.out.println("INFO: number of tests matching " + group + " is " + groupMicros.size());

            for (String mbName : groupMicros) {
                int count = MicrosJdk.getTestsCount(mbName) * MicrosJdk.getTimeoutFactor(mbName);

                if (tests + count > maxTestsPerFile) {
                    ps.print("\n");
                    ps.close();
                    ++fileIndex;
                    ps = openTestFile(testDir, fileIndex);
                    tests = 0;
                }
                ps.print(testDescription(mbName, count));
                MicrosJdk.markGenerated(mbName);
                tests += count;
            }

            ps.print("\n");
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }

    private static PrintStream openTestFile(Path testDir, int index) throws FileNotFoundException {
        Path testFile = testDir.resolve("Test_" + index + ".java");
        PrintStream result = new PrintStream(testFile.toFile());
        result.print(COPYRIGHT);
        result.printf("/* DO NOT MODIFY THIS FILE. GENERATED BY %s */\n",
                TestGenerator.class.getName());
        return result;
    }

    static void printUsage() {
        System.out.println("Usage: " + TestGenerator.class.getName() + " "
                + "[-group ALL(default)|"
                + Stream.of(MicrosGroup.values()).map(Object::toString).collect(Collectors.joining("|"))
                + "]");
    }

    public static void main(String... args) {
        // By default generate tests for all groups
        MicrosGroup[] microsGroupList = MicrosGroup.values();

        int i=0;
        String arg, val;
        while (i+1 < args.length) {
            arg = args[i++];
            val = args[i++];

            switch (arg) {
                // Generate tests only for particular group if specified
                case "-group":
                    if (!val.isEmpty() && !val.toLowerCase().equals("all")) {
                       try {
                           MicrosGroup genGroup = MicrosGroup.valueOf(val);
                           microsGroupList = new MicrosGroup[]{genGroup};
                       } catch (IllegalArgumentException  ex) {
                           printUsage();
                           throw ex;
                       }
                    }
                    break;
                default:
                    System.out.println("WARN: illegal option " + arg);
                    break;
            }
        }

        try {
            // Load all microbenchmarks
            MicrosJdk.init();

            // Generate tests for test group(s)
            System.out.println("INFO: Micros groups=" + java.util.Arrays.asList(microsGroupList));
            for (MicrosGroup group : microsGroupList) {
                try {
                    System.out.println("INFO: Generate tests for group " + group);
                    generate(group);
                } catch (Exception e) {
                    throw new Error("Generating tests for " + group.name()
                            + " has failed", e);
                }
            }
        } catch (InvalidPathException e) {
            throw new Error("Can't get path for microbenchmarks, file=" + MICROBENCHMARK_JAR_FILE);
        }

        // Dump benchmarks which were skipped if any
        MicrosJdk.printLongRunning();
    }
}

