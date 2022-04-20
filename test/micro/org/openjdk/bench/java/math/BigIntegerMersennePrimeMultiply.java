package org.openjdk.bench.java.math;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BinaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

/**
 * Benchmark for checking performance difference between sequential and parallel
 * multiply of very large Mersenne primes using BigInteger. We want to measure
 * real time, user time, system time and the amount of memory allocated. To
 * calculate this, we create our own thread factory for the common ForkJoinPool
 * and then use that to measure user time, cpu time and bytes allocated.
 * <p>
 * We use reflection to discover all methods that match "*ultiply", and use them
 * to multiply two very large Mersenne primes together.
 * <p>
 * <h3>Results on a 1-6-2 machine running Ubuntu linux</h3>
 * <p>
 * Memory allocation increased from 83.9GB to 84GB, for both the sequential and
 * parallel versions. This is an increase of just 0.1%. On this machine, the
 * parallel version was 3.8x faster in latency (real time), but it used 2.7x
 * more CPU resources.
 * <p>
 * Testing multiplying Mersenne primes of 2^57885161-1 and 2^82589933-1
 * <p>
 * <pre>
 * openjdk version "18-internal" 2022-03-15
 * BigInteger.parallelMultiply()
 * real  0m6.288s
 * user  1m3.010s
 * sys   0m0.027s
 * mem   84.0GB
 * BigInteger.multiply()
 * real  0m23.682s
 * user  0m23.530s
 * sys   0m0.004s
 * mem   84.0GB
 *
 * openjdk version "1.8.0_302"
 * BigInteger.multiply()
 * real  0m25.657s
 * user  0m25.390s
 * sys   0m0.001s
 * mem   83.9GB
 *
 * openjdk version "9.0.7.1"
 * BigInteger.multiply()
 * real  0m24.907s
 * user  0m24.700s
 * sys   0m0.001s
 * mem   83.9GB
 *
 * openjdk version "10.0.2" 2018-07-17
 * BigInteger.multiply()
 * real  0m24.632s
 * user  0m24.380s
 * sys   0m0.004s
 * mem   83.9GB
 *
 * openjdk version "11.0.12" 2021-07-20 LTS
 * BigInteger.multiply()
 * real  0m22.114s
 * user  0m21.930s
 * sys   0m0.001s
 * mem   83.9GB
 *
 * openjdk version "12.0.2" 2019-07-16
 * BigInteger.multiply()
 * real  0m23.015s
 * user  0m22.830s
 * sys   0m0.000s
 * mem   83.9GB
 *
 * openjdk version "13.0.9" 2021-10-19
 * BigInteger.multiply()
 * real  0m23.548s
 * user  0m23.350s
 * sys   0m0.005s
 * mem   83.9GB
 *
 * openjdk version "14.0.2" 2020-07-14
 * BigInteger.multiply()
 * real  0m22.918s
 * user  0m22.530s
 * sys   0m0.131s
 * mem   83.9GB
 *
 * openjdk version "15.0.5" 2021-10-19
 * BigInteger.multiply()
 * real  0m22.038s
 * user  0m21.750s
 * sys   0m0.003s
 * mem   83.9GB
 *
 * openjdk version "16.0.2" 2021-07-20
 * BigInteger.multiply()
 * real  0m23.049s
 * user  0m22.760s
 * sys   0m0.006s
 * mem   83.9GB
 *
 * openjdk version "17" 2021-09-14
 * BigInteger.multiply()
 * real  0m22.580s
 * user  0m22.310s
 * sys   0m0.001s
 * mem   83.9GB
 *</pre>
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
public class BigIntegerMersennePrimeMultiply implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    // Large Mersenne prime discovered by Curtis Cooper in 2013
    private static final int EXPONENT_1 = 57885161;
    private static final BigInteger MERSENNE_1 =
            BigInteger.ONE.shiftLeft(EXPONENT_1).subtract(BigInteger.ONE);
    // Largest Mersenne prime number discovered by Patrick Laroche in 2018
    private static final int EXPONENT_2 = 82589933;
    private static final BigInteger MERSENNE_2 =
            BigInteger.ONE.shiftLeft(EXPONENT_2).subtract(BigInteger.ONE);
    private static boolean DEBUG = false;

    public static void main(String... args) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory",
                BigIntegerMersennePrimeMultiply.class.getName());
        System.out.println("Testing multiplying Mersenne primes of " +
                "2^" + EXPONENT_1 + "-1 and 2^" + EXPONENT_2 + "-1");
        addCounters(Thread.currentThread());
        System.out.println("Using the following multiply methods:");
        List<Method> methods = Arrays.stream(BigInteger.class.getMethods())
                .filter(method -> method.getName().endsWith("ultiply") &&
                        method.getParameterCount() == 1 &&
                        method.getParameterTypes()[0] == BigInteger.class)
                .peek(method -> System.out.println("    " + method))
                .collect(Collectors.toList());

        for (int i = 0; i < 3; i++) {
            System.out.println();
            methods.forEach(BigIntegerMersennePrimeMultiply::test);
        }
    }

    private static void test(Method method) {
        BinaryOperator<BigInteger> multiplyOperator = (a, b) -> {
            try {
                return (BigInteger) method.invoke(a, b);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            } catch (InvocationTargetException e) {
                throw new AssertionError(e.getCause());
            }
        };
        test(method.getName(), multiplyOperator);
    }

    private static void test(String description,
                             BinaryOperator<BigInteger> multiplyOperator) {
        System.out.println("BigInteger." + description + "()");
        resetAllCounters();
        long elapsedTimeInNanos = System.nanoTime();
        try {
            BigInteger result1 = multiplyOperator.apply(MERSENNE_1, MERSENNE_2);
            BigInteger result2 = multiplyOperator.apply(MERSENNE_2, MERSENNE_1);
            if (result1.bitLength() != 140475094)
                throw new AssertionError("Expected bitLength: 140475094, " +
                        "but was " + result1.bitLength());
            if (result2.bitLength() != 140475094)
                throw new AssertionError("Expected bitLength: 140475094, " +
                        "but was " + result1.bitLength());
        } finally {
            elapsedTimeInNanos = System.nanoTime() - elapsedTimeInNanos;
        }

        LongSummaryStatistics userTimeStatistics = getStatistics(userTime);
        LongSummaryStatistics cpuTimeStatistics = getStatistics(cpuTime);
        LongSummaryStatistics memoryAllocationStatistics = getStatistics(bytes);
        System.out.println("real  " + formatTime(elapsedTimeInNanos));
        System.out.println("user  " + formatTime(userTimeStatistics.getSum()));
        System.out.println("sys   " +
                formatTime(cpuTimeStatistics.getSum() - userTimeStatistics.getSum()));
        System.out.println("mem   " + formatMemory(memoryAllocationStatistics.getSum(), 1));
    }

    private static LongSummaryStatistics getStatistics(Map<Thread, AtomicLong> timeMap) {
        return timeMap.entrySet()
                .stream()
                .peek(entry -> {
                    long timeInMs = (counterExtractorMap.get(timeMap)
                            .applyAsLong(entry.getKey().getId())
                            - entry.getValue().get());
                    entry.getValue().set(timeInMs);
                })
                .peek(BigIntegerMersennePrimeMultiply::printTime)
                .map(Map.Entry::getValue)
                .mapToLong(AtomicLong::get)
                .summaryStatistics();
    }

    private static void printTime(Map.Entry<Thread, AtomicLong> threadCounter) {
        if (DEBUG)
            System.out.printf("%s %d%n", threadCounter.getKey(), threadCounter.getValue()
                    .get());
    }

    private static void addCounters(Thread thread) {
        counterExtractorMap.forEach((map, timeExtractor) -> add(map, thread, timeExtractor));
    }

    private static void add(Map<Thread, AtomicLong> time, Thread thread,
                            LongUnaryOperator timeExtractor) {
        time.put(thread, new AtomicLong(timeExtractor.applyAsLong(thread.getId())));
    }

    private static void resetAllCounters() {
        counterExtractorMap.forEach(BigIntegerMersennePrimeMultiply::resetTimes);
    }

    private static void resetTimes(Map<Thread, AtomicLong> timeMap, LongUnaryOperator timeMethod) {
        timeMap.forEach((thread, time) ->
                time.set(timeMethod.applyAsLong(thread.getId())));
    }

    private static final Map<Thread, AtomicLong> userTime =
            new ConcurrentHashMap<>();
    private static final Map<Thread, AtomicLong> cpuTime =
            new ConcurrentHashMap<>();
    private static final Map<Thread, AtomicLong> bytes =
            new ConcurrentHashMap<>();
    private static final ThreadMXBean tmb = ManagementFactory.getThreadMXBean();

    private static final Map<Map<Thread, AtomicLong>, LongUnaryOperator> counterExtractorMap =
            new IdentityHashMap<>();

    static {
        counterExtractorMap.put(userTime, tmb::getThreadUserTime);
        counterExtractorMap.put(cpuTime, tmb::getThreadCpuTime);
        counterExtractorMap.put(bytes, BigIntegerMersennePrimeMultiply::threadAllocatedBytes);
    }

    public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        ForkJoinWorkerThread thread = defaultForkJoinWorkerThreadFactory.newThread(pool);
        addCounters(thread);
        return thread;
    }

    private static final String[] SIGNATURE = new String[]{long.class.getName()};
    private static final MBeanServer mBeanServer;
    private static final ObjectName name;

    static {
        try {
            name = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } catch (MalformedObjectNameException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static long threadAllocatedBytes(long threadId) {
        try {
            return (long) mBeanServer.invoke(
                    name,
                    "getThreadAllocatedBytes",
                    new Object[]{threadId},
                    SIGNATURE
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String formatMemory(double bytes, int decimals) {
        double val;
        String unitStr;
        if (bytes < 1024) {
            val = bytes;
            unitStr = "B";
        } else if (bytes < 1024 * 1024) {
            val = bytes / 1024;
            unitStr = "KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            val = bytes / (1024 * 1024);
            unitStr = "MB";
        } else if (bytes < 1024 * 1024 * 1024 * 1024L) {
            val = bytes / (1024 * 1024 * 1024L);
            unitStr = "GB";
        } else {
            val = bytes / (1024 * 1024 * 1024 * 1024L);
            unitStr = "TB";
        }
        return String.format(Locale.US, "%." + decimals + "f%s", val, unitStr);
    }

    public static String formatTime(long nanos) {
        if (nanos < 0) nanos = 0;
        long timeInMs = TimeUnit.NANOSECONDS.toMillis(nanos);
        long minutes = timeInMs / 60_000;
        double remainingMs = (timeInMs % 60_000) / 1000.0;
        return String.format(Locale.US, "%dm%.3fs", minutes, remainingMs);
    }
}