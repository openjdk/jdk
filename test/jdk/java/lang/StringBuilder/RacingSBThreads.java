/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test multi-threaded use of StringBuilder
 * @compile --release 8 RacingSBThreads.java
 * @run main/othervm -esa RacingSBThreads read
 * @run main/othervm -esa RacingSBThreads insert
 * @run main/othervm -esa RacingSBThreads append
 * @run main/othervm -Xcomp RacingSBThreads
 */

import java.nio.CharBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Test racing accesses in StringBuilder.
 * Test source code should be compatible with JDK 8 to allow testing on older versions.
 */
public class RacingSBThreads {

    private static final int TIMEOUT_SEC = 1;   // Duration to run each test case
    private static final int N = 1_000_000;     // static number of iterations for writes and modifies
    private static final int LEN = 100_000;     // Length of initial SB

    // Strings available to be used as the initial contents of a StringBuilder
    private static final String UTF16_CHARS = initString('\u1000', LEN);
    private static final String LATIN1_CHARS = initString('a', LEN);

    // Cache jtreg timeout factor to allow test to be run as a standalone main()
    private static final double TIMEOUT_FACTOR = Double.parseDouble(System.getProperty("test.timeout.factor", "1.0"));

    // Constant arguments available to be passed to StringBuilder operations
    private static final StringBuilder otherSB = new StringBuilder("ab\uFF21\uFF22");
    private static final StringBuilder otherLongerSB = new StringBuilder("abcde\uFF21\uFF22\uFF23\uFF24\uFF25");

    // Create a String with a repeated character
    private static String initString(char c, int len) {
        char[] chars = new char[len];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // Plain unsynchronized reference to a StringBuilder
    // Updated by the writer thread
    // Read by the reader thread
    private StringBuilder buf;

    // The current stress test case
    private final StressKind stressKind;

    // Count of faults, zero if no faults found
    private final AtomicInteger faultCount = new AtomicInteger(0);

    /**
     * Run the stress cases indicated by command line arguments or run all cases.
     * Running each for TIMEOUT_SEC seconds or until a failure.
     * The timeout/test duration can be scaled by setting System property
     * `test.timeout.factor` to a double value, for example, `-Dtest.timeout.factor=2.0`
     * @param args command line arguments
     */
    public static void main(String[] args) {
        Duration duration = Duration.ofSeconds((long)(TIMEOUT_SEC * TIMEOUT_FACTOR));

        StressKind[] kinds = StressKind.values();
        if (args.length > 0) {
            // Parse explicitly supplied StressKind arguments
            try {
                 kinds = Arrays.stream(args)
                         .map((s) -> StressKind.valueOf(s.toUpperCase(Locale.ROOT)))
                         .toArray(StressKind[]::new);
            } catch (Exception ex) {
                System.out.println("Invalid StressKind arguments: " + Arrays.toString(args));
                return;
            }
        }

        // Run each kind for the duration
        int totalFaults = 0;
        for (StressKind sk : kinds) {
            Instant end = Instant.now().plus(duration); // note clock time, not runtime
            while (Instant.now().isBefore(end)) {
                int faultCount = new RacingSBThreads(sk).stress();
                if (faultCount > 0) {
                    System.out.printf("ERROR: Test case %s, %d faults%n", sk, faultCount);
                }
                totalFaults += faultCount;
            }
        }
        if (totalFaults > 0) {
            throw new AssertionError("Total faults: " + totalFaults);
        }
    }

    // Enum of the various test cases with a lambda to invoke for each
    enum StressKind {
        /**
         * Reading characters should always be one of the known values being written to the destination
         */
        READ(LATIN1_CHARS, (sb,  chr) -> {
            char ch = sb.charAt(LEN * 4 / 5);
            if (ch != chr & ch != (chr & 0xff) & ch != chr >> 8) {
                throw new AssertionError("Unexpected characters in buffer: 0x" + Integer.toHexString(ch));
            }
        }),
        /**
         * Insert another StringBuilder; in the face of racy changes to the destination
         */
        INSERT(LATIN1_CHARS, (sb, C) -> {
            sb.insert(sb.length() - 1, otherLongerSB, 0, otherLongerSB.length());
        }),
        /**
         * Appending a StringBuilder in the face of racy changes to the destination
         */
        APPEND(LATIN1_CHARS, (sb, C) -> {
            sb.append(otherSB, 0, otherSB.length());
        }),
        ;

        private final BiConsumer<StringBuilder,Character> func;
        private final String sbInitString;

        /**
         * Defines a test case.
         * @param sbInitString the initial contents of the StringBuilder; chooses the coder
         * @param func the test BiConsumer to apply to the StringBuilder
         */
        private StressKind(String sbInitString, BiConsumer<StringBuilder,Character> func) {
            this.func = func;
            this.sbInitString = sbInitString;
        }
    }

    public RacingSBThreads(StressKind stressKind) {
        this.stressKind = stressKind;
    }

    /**
     * Run the stress case.
     * One thread continuously creates a StringBuilder and fills it before trimming it to zero.
     * The other thread performs the test case on the same StringBuilder (without any synchronization)
     * @return the count of faults
     */
    private int stress() {
        PokeBuilder r = new PokeBuilder(this, N);
        Writer w = new Writer(this, N);

        Thread writer = new Thread(w::createShrink);
        Thread reader = new Thread(r::readModify);
        writer.start();
        reader.start();
        join(reader);
        System.out.println(r);
        writer.interrupt();
        join(writer);
        System.out.println(w);
        return r.racing.faultCount.get();
    }

    /**
     * Wait for a thread to terminate.
     * @param thread a thread to wait for
     */
    private void join(Thread thread) {
        do {
            try {
                thread.join();
                break;
            } catch (InterruptedException ie) {
                // ignore and retry
            }
        } while (true);
    }

    /**
     * Run a StressKind case in a loop keeping track of exceptions.
     * The StringBuilder under test is shared with the writer task without benefit of synchronization.
     */
    private static class PokeBuilder {
        private final RacingSBThreads racing;
        private final int iterations;
        private int nulls;
        private int bounds;
        private int pokeCycles;
        private int bufChanges;

        public PokeBuilder(RacingSBThreads racing, int iterations) {
            this.racing = racing;
            this.iterations = iterations;
            nulls = 0;
            bounds = 0;
            pokeCycles = 0;
            bufChanges = 0;
        }

        // Repeatedly change the racy StringBuilder, ignoring and counting exceptions
        private void readModify() {
            System.out.println("Starting " + racing.stressKind);
            sleep(100);
            for (int i = 0; i < iterations; ++i) {
                pokeCycles++;
                StringBuilder sb = racing.buf;  // read once
                try {
                    if (sb.length() > Integer.MAX_VALUE / 4) {
                        sb.setLength(Integer.MAX_VALUE / 4);
                    }
                    // Invoke the test case
                    racing.stressKind.func.accept(sb, racing.stressKind.sbInitString.charAt(0));
                    if (sb != racing.buf) {
                        bufChanges++;
                    }
                } catch (NullPointerException e) {
                    ++nulls;
                } catch (IndexOutOfBoundsException e) {
                    ++bounds;
                } catch (AssertionError ae) {
                    racing.faultCount.incrementAndGet();
                    throw ae;
                }
            }
        }

        private static void sleep(int i) {
            try {
                Thread.sleep(i);
            } catch (InterruptedException ignored) {
            }
        }

        public String toString() {
            return String.format("pokeCycles:%d, bounds:%d, bufChanges:%d, nulls=%d",
                    pokeCycles, bounds, bufChanges, nulls);
        }
    }

    /**
     * Repeatedly create and append strings to a StringBuilder shared through fields of RacingSBThreads.
     * The StringBuilder is created new on each iteration and truncated at the end of each iteration.
     * Exceptions are counted and reported.
     */
    private static class Writer {
        private final RacingSBThreads racing;
        private final int iterations;
        private int sumWriter;
        private int writeCycles;
        private int putBounds;

        public Writer(RacingSBThreads racing, int iterations) {
            this.racing = racing;
            this.iterations = iterations;
        }

        private void createShrink() {
            for (int i = 0; i < iterations; ++i) {
                if (i % 100_000 == 0) {
                    if (Thread.interrupted()) {
                        break;
                    }
                }
                try {
                    ++writeCycles;
                    racing.buf = new StringBuilder(racing.stressKind.sbInitString);
                    racing.buf.append(UTF16_CHARS);
                    sumWriter += racing.buf.length();
                    racing.buf.setLength(0);
                    racing.buf.trimToSize();
                } catch (Exception ex) {
                    ++putBounds;
                }
            }
        }

        public String toString() {
            return String.format("writeCycles:%d, bounds:%d, sumWriter=%d", writeCycles, putBounds, sumWriter);
        }
    }
}
