/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Datadog, Inc. All rights reserved.
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
package jdk.jfr.internal.settings;

import java.util.concurrent.TimeUnit;

enum ThrottleUnit {
        NANOSECONDS("ns", TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toMillis(1)),
        MICROSECONDS("us", TimeUnit.SECONDS.toNanos(1) / 1000, TimeUnit.SECONDS.toMillis(1)),
        MILLISECONDS("ms", TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1)),
        SECONDS("s", 1, TimeUnit.SECONDS.toMillis(1)),
        MINUTES("m", 1, TimeUnit.MINUTES.toMillis(1)),
        HOUR("h", 1, TimeUnit.HOURS.toMillis(1)),
        DAY("d", 1, TimeUnit.DAYS.toMillis(1));

        private final String text;
        private final long factor;
        private final long millis;

        ThrottleUnit(String t, long factor, long millis) {
            this.text = t;
            this.factor = factor;
            this.millis = millis;
        }

        private static ThrottleUnit parse(String s) {
            if (s.equals(ThrottleSetting.OFF_TEXT)) {
                return MILLISECONDS;
            }
            return unit(ThrottleSetting.parseThrottleString(s, false));
        }

        private static ThrottleUnit unit(String s) {
            if (s.endsWith("ns") || s.endsWith("us") || s.endsWith("ms")) {
                return value(s.substring(s.length() - 2));
            }
            if (s.endsWith("s") || s.endsWith("m") || s.endsWith("h") || s.endsWith("d")) {
                return value(s.substring(s.length() - 1));
            }
            throw new NumberFormatException("'" + s + "' is not a valid time unit.");
        }

        private static ThrottleUnit value(String s) {
            for (ThrottleUnit t : values()) {
                if (t.text.equals(s)) {
                    return t;
                }
            }
            throw new NumberFormatException("'" + s + "' is not a valid time unit.");
        }

        static long asMillis(String s) {
            return parse(s).millis;
        }

        static long normalizeValueAsMillis(long value, String s) {
            return value * parse(s).factor;
        }
    }