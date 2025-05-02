/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.internal.settings.CPUThrottleSetting;

/**
 * A rate or fixed period, see {@link jdk.jfr.internal.Rate}
 */
public record TimespanRate(double rate, boolean autoadapt) {

    public static TimespanRate of(String text) {
        if (text.equals("off")) {
            text = CPUThrottleSetting.DEFAULT_VALUE;
        }
        boolean isPeriod = !text.contains("/");
        if (isPeriod) {
            var period = ValueParser.parseTimespanWithInfinity(text, Long.MAX_VALUE);
            if (period == Long.MAX_VALUE) {
                return null;
            }
            if (period == 0) {
                return new TimespanRate(0, false);
            }
            return new TimespanRate(Runtime.getRuntime().availableProcessors() / (period / 1_000_000_000.0), false);
        }
        Rate r = Rate.of(text);
        if (r == null) {
            return null;
        }
        return new TimespanRate(r.perSecond(), true);
    }

    public boolean isHigher(TimespanRate that) {
        return rate() > that.rate();
    }

    @Override
    public String toString() {
        if (autoadapt) {
            return String.format("%d/ns", (long)(rate * 1_000_000_000L));
        }
        return String.format("%dns", (long)(Runtime.getRuntime().availableProcessors() / rate * 1_000_000_000L));
    }
}
