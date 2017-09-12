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
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TimeUnit8Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(TimeUnit8Test.class);
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
