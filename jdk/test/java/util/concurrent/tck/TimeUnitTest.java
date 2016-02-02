/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TimeUnitTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(TimeUnitTest.class);
    }

    // (loops to 88888 check increments at all time divisions.)

    /**
     * convert correctly converts sample values across the units
     */
    public void testConvert() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*60*60*24,
                         SECONDS.convert(t, DAYS));
            assertEquals(t*60*60,
                         SECONDS.convert(t, HOURS));
            assertEquals(t*60,
                         SECONDS.convert(t, MINUTES));
            assertEquals(t,
                         SECONDS.convert(t, SECONDS));
            assertEquals(t,
                         SECONDS.convert(1000L*t, MILLISECONDS));
            assertEquals(t,
                         SECONDS.convert(1000000L*t, MICROSECONDS));
            assertEquals(t,
                         SECONDS.convert(1000000000L*t, NANOSECONDS));

            assertEquals(1000L*t*60*60*24,
                         MILLISECONDS.convert(t, DAYS));
            assertEquals(1000L*t*60*60,
                         MILLISECONDS.convert(t, HOURS));
            assertEquals(1000L*t*60,
                         MILLISECONDS.convert(t, MINUTES));
            assertEquals(1000L*t,
                         MILLISECONDS.convert(t, SECONDS));
            assertEquals(t,
                         MILLISECONDS.convert(t, MILLISECONDS));
            assertEquals(t,
                         MILLISECONDS.convert(1000L*t, MICROSECONDS));
            assertEquals(t,
                         MILLISECONDS.convert(1000000L*t, NANOSECONDS));

            assertEquals(1000000L*t*60*60*24,
                         MICROSECONDS.convert(t, DAYS));
            assertEquals(1000000L*t*60*60,
                         MICROSECONDS.convert(t, HOURS));
            assertEquals(1000000L*t*60,
                         MICROSECONDS.convert(t, MINUTES));
            assertEquals(1000000L*t,
                         MICROSECONDS.convert(t, SECONDS));
            assertEquals(1000L*t,
                         MICROSECONDS.convert(t, MILLISECONDS));
            assertEquals(t,
                         MICROSECONDS.convert(t, MICROSECONDS));
            assertEquals(t,
                         MICROSECONDS.convert(1000L*t, NANOSECONDS));

            assertEquals(1000000000L*t*60*60*24,
                         NANOSECONDS.convert(t, DAYS));
            assertEquals(1000000000L*t*60*60,
                         NANOSECONDS.convert(t, HOURS));
            assertEquals(1000000000L*t*60,
                         NANOSECONDS.convert(t, MINUTES));
            assertEquals(1000000000L*t,
                         NANOSECONDS.convert(t, SECONDS));
            assertEquals(1000000L*t,
                         NANOSECONDS.convert(t, MILLISECONDS));
            assertEquals(1000L*t,
                         NANOSECONDS.convert(t, MICROSECONDS));
            assertEquals(t,
                         NANOSECONDS.convert(t, NANOSECONDS));
        }
    }

    /**
     * toNanos correctly converts sample values in different units to
     * nanoseconds
     */
    public void testToNanos() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*1000000000L*60*60*24,
                         DAYS.toNanos(t));
            assertEquals(t*1000000000L*60*60,
                         HOURS.toNanos(t));
            assertEquals(t*1000000000L*60,
                         MINUTES.toNanos(t));
            assertEquals(1000000000L*t,
                         SECONDS.toNanos(t));
            assertEquals(1000000L*t,
                         MILLISECONDS.toNanos(t));
            assertEquals(1000L*t,
                         MICROSECONDS.toNanos(t));
            assertEquals(t,
                         NANOSECONDS.toNanos(t));
        }
    }

    /**
     * toMicros correctly converts sample values in different units to
     * microseconds
     */
    public void testToMicros() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*1000000L*60*60*24,
                         DAYS.toMicros(t));
            assertEquals(t*1000000L*60*60,
                         HOURS.toMicros(t));
            assertEquals(t*1000000L*60,
                         MINUTES.toMicros(t));
            assertEquals(1000000L*t,
                         SECONDS.toMicros(t));
            assertEquals(1000L*t,
                         MILLISECONDS.toMicros(t));
            assertEquals(t,
                         MICROSECONDS.toMicros(t));
            assertEquals(t,
                         NANOSECONDS.toMicros(t*1000L));
        }
    }

    /**
     * toMillis correctly converts sample values in different units to
     * milliseconds
     */
    public void testToMillis() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*1000L*60*60*24,
                         DAYS.toMillis(t));
            assertEquals(t*1000L*60*60,
                         HOURS.toMillis(t));
            assertEquals(t*1000L*60,
                         MINUTES.toMillis(t));
            assertEquals(1000L*t,
                         SECONDS.toMillis(t));
            assertEquals(t,
                         MILLISECONDS.toMillis(t));
            assertEquals(t,
                         MICROSECONDS.toMillis(t*1000L));
            assertEquals(t,
                         NANOSECONDS.toMillis(t*1000000L));
        }
    }

    /**
     * toSeconds correctly converts sample values in different units to
     * seconds
     */
    public void testToSeconds() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*60*60*24,
                         DAYS.toSeconds(t));
            assertEquals(t*60*60,
                         HOURS.toSeconds(t));
            assertEquals(t*60,
                         MINUTES.toSeconds(t));
            assertEquals(t,
                         SECONDS.toSeconds(t));
            assertEquals(t,
                         MILLISECONDS.toSeconds(t*1000L));
            assertEquals(t,
                         MICROSECONDS.toSeconds(t*1000000L));
            assertEquals(t,
                         NANOSECONDS.toSeconds(t*1000000000L));
        }
    }

    /**
     * toMinutes correctly converts sample values in different units to
     * minutes
     */
    public void testToMinutes() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*60*24,
                         DAYS.toMinutes(t));
            assertEquals(t*60,
                         HOURS.toMinutes(t));
            assertEquals(t,
                         MINUTES.toMinutes(t));
            assertEquals(t,
                         SECONDS.toMinutes(t*60));
            assertEquals(t,
                         MILLISECONDS.toMinutes(t*1000L*60));
            assertEquals(t,
                         MICROSECONDS.toMinutes(t*1000000L*60));
            assertEquals(t,
                         NANOSECONDS.toMinutes(t*1000000000L*60));
        }
    }

    /**
     * toHours correctly converts sample values in different units to
     * hours
     */
    public void testToHours() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t*24,
                         DAYS.toHours(t));
            assertEquals(t,
                         HOURS.toHours(t));
            assertEquals(t,
                         MINUTES.toHours(t*60));
            assertEquals(t,
                         SECONDS.toHours(t*60*60));
            assertEquals(t,
                         MILLISECONDS.toHours(t*1000L*60*60));
            assertEquals(t,
                         MICROSECONDS.toHours(t*1000000L*60*60));
            assertEquals(t,
                         NANOSECONDS.toHours(t*1000000000L*60*60));
        }
    }

    /**
     * toDays correctly converts sample values in different units to
     * days
     */
    public void testToDays() {
        for (long t = 0; t < 88888; ++t) {
            assertEquals(t,
                         DAYS.toDays(t));
            assertEquals(t,
                         HOURS.toDays(t*24));
            assertEquals(t,
                         MINUTES.toDays(t*60*24));
            assertEquals(t,
                         SECONDS.toDays(t*60*60*24));
            assertEquals(t,
                         MILLISECONDS.toDays(t*1000L*60*60*24));
            assertEquals(t,
                         MICROSECONDS.toDays(t*1000000L*60*60*24));
            assertEquals(t,
                         NANOSECONDS.toDays(t*1000000000L*60*60*24));
        }
    }

    /**
     * convert saturates positive too-large values to Long.MAX_VALUE
     * and negative to LONG.MIN_VALUE
     */
    public void testConvertSaturate() {
        assertEquals(Long.MAX_VALUE,
                     NANOSECONDS.convert(Long.MAX_VALUE / 2, SECONDS));
        assertEquals(Long.MIN_VALUE,
                     NANOSECONDS.convert(-Long.MAX_VALUE / 4, SECONDS));
        assertEquals(Long.MAX_VALUE,
                     NANOSECONDS.convert(Long.MAX_VALUE / 2, MINUTES));
        assertEquals(Long.MIN_VALUE,
                     NANOSECONDS.convert(-Long.MAX_VALUE / 4, MINUTES));
        assertEquals(Long.MAX_VALUE,
                     NANOSECONDS.convert(Long.MAX_VALUE / 2, HOURS));
        assertEquals(Long.MIN_VALUE,
                     NANOSECONDS.convert(-Long.MAX_VALUE / 4, HOURS));
        assertEquals(Long.MAX_VALUE,
                     NANOSECONDS.convert(Long.MAX_VALUE / 2, DAYS));
        assertEquals(Long.MIN_VALUE,
                     NANOSECONDS.convert(-Long.MAX_VALUE / 4, DAYS));
    }

    /**
     * toNanos saturates positive too-large values to Long.MAX_VALUE
     * and negative to LONG.MIN_VALUE
     */
    public void testToNanosSaturate() {
        assertEquals(Long.MAX_VALUE,
                     MILLISECONDS.toNanos(Long.MAX_VALUE / 2));
        assertEquals(Long.MIN_VALUE,
                     MILLISECONDS.toNanos(-Long.MAX_VALUE / 3));
    }

    /**
     * toString returns name of unit
     */
    public void testToString() {
        assertEquals("SECONDS", SECONDS.toString());
    }

    /**
     * name returns name of unit
     */
    public void testName() {
        assertEquals("SECONDS", SECONDS.name());
    }

    /**
     * Timed wait without holding lock throws
     * IllegalMonitorStateException
     */
    public void testTimedWait_IllegalMonitorException() {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Object o = new Object();
                TimeUnit tu = MILLISECONDS;

                try {
                    tu.timedWait(o, LONG_DELAY_MS);
                    threadShouldThrow();
                } catch (IllegalMonitorStateException success) {}
            }});

        awaitTermination(t);
    }

    /**
     * timedWait throws InterruptedException when interrupted
     */
    public void testTimedWait_Interruptible() {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Object o = new Object();
                TimeUnit tu = MILLISECONDS;

                Thread.currentThread().interrupt();
                try {
                    synchronized (o) {
                        tu.timedWait(o, LONG_DELAY_MS);
                    }
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    synchronized (o) {
                        tu.timedWait(o, LONG_DELAY_MS);
                    }
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * timedJoin throws InterruptedException when interrupted
     */
    public void testTimedJoin_Interruptible() {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        final Thread s = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.sleep(LONG_DELAY_MS);
            }});
        final Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                TimeUnit tu = MILLISECONDS;
                Thread.currentThread().interrupt();
                try {
                    tu.timedJoin(s, LONG_DELAY_MS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    tu.timedJoin(s, LONG_DELAY_MS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        s.interrupt();
        awaitTermination(s);
    }

    /**
     * timedSleep throws InterruptedException when interrupted
     */
    public void testTimedSleep_Interruptible() {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                TimeUnit tu = MILLISECONDS;
                Thread.currentThread().interrupt();
                try {
                    tu.sleep(LONG_DELAY_MS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    tu.sleep(LONG_DELAY_MS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * a deserialized serialized unit is the same instance
     */
    public void testSerialization() throws Exception {
        for (TimeUnit x : TimeUnit.values())
            assertSame(x, serialClone(x));
    }

    /**
     * tests for toChronoUnit.
     */
    public void testToChronoUnit() throws Exception {
        assertSame(ChronoUnit.NANOS,   NANOSECONDS.toChronoUnit());
        assertSame(ChronoUnit.MICROS,  MICROSECONDS.toChronoUnit());
        assertSame(ChronoUnit.MILLIS,  MILLISECONDS.toChronoUnit());
        assertSame(ChronoUnit.SECONDS, SECONDS.toChronoUnit());
        assertSame(ChronoUnit.MINUTES, MINUTES.toChronoUnit());
        assertSame(ChronoUnit.HOURS,   HOURS.toChronoUnit());
        assertSame(ChronoUnit.DAYS,    DAYS.toChronoUnit());

        // Every TimeUnit has a defined ChronoUnit equivalent
        for (TimeUnit x : TimeUnit.values())
            assertSame(x, TimeUnit.of(x.toChronoUnit()));
    }

    /**
     * tests for TimeUnit.of(ChronoUnit).
     */
    public void testTimeUnitOf() throws Exception {
        assertSame(NANOSECONDS,  TimeUnit.of(ChronoUnit.NANOS));
        assertSame(MICROSECONDS, TimeUnit.of(ChronoUnit.MICROS));
        assertSame(MILLISECONDS, TimeUnit.of(ChronoUnit.MILLIS));
        assertSame(SECONDS,      TimeUnit.of(ChronoUnit.SECONDS));
        assertSame(MINUTES,      TimeUnit.of(ChronoUnit.MINUTES));
        assertSame(HOURS,        TimeUnit.of(ChronoUnit.HOURS));
        assertSame(DAYS,         TimeUnit.of(ChronoUnit.DAYS));

        assertThrows(NullPointerException.class,
                     () -> TimeUnit.of((ChronoUnit)null));

        // ChronoUnits either round trip to their TimeUnit
        // equivalents, or throw IllegalArgumentException.
        for (ChronoUnit cu : ChronoUnit.values()) {
            final TimeUnit tu;
            try {
                tu = TimeUnit.of(cu);
            } catch (IllegalArgumentException acceptable) {
                continue;
            }
            assertSame(cu, tu.toChronoUnit());
        }
    }

}
