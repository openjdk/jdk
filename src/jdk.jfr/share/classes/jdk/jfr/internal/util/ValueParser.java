/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class ValueParser {
    private static final String INFINITY = "infinity";

    public static long parseTimespanWithInfinity(String s) {
        if (INFINITY.equals(s)) {
            return Long.MAX_VALUE;
        }
        return parseTimespan(s);
    }

    public static long parseTimespan(String s) {
        if (s.endsWith("ns")) {
            return Long.parseLong(s.substring(0, s.length() - 2).trim());
        }
        if (s.endsWith("us")) {
            return MICROSECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        }
        if (s.endsWith("ms")) {
            return MILLISECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 2).trim()));
        }
        if (s.endsWith("s")) {
            return SECONDS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("m")) {
            return MINUTES.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("h")) {
            return HOURS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }
        if (s.endsWith("d")) {
            return DAYS.toNanos(Long.parseLong(s.substring(0, s.length() - 1).trim()));
        }

        try {
            Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("'" + s + "' is not a valid timespan. Should be numeric value followed by a unit, i.e. 20 ms. Valid units are ns, us, s, m, h and d.");
        }
        // Only accept values with units
        throw new NumberFormatException("Timespan + '" + s + "' is missing unit. Valid units are ns, us, s, m, h and d.");
    }
}
