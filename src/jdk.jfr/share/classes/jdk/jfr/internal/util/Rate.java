/*
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

public record Rate(long amount, TimespanUnit unit) {

    public static Rate of(String text) {
        String[] splitted = text.split("/");
        if (splitted.length != 2) {
            return null;
        }
        String value = splitted[0].strip();
        String unit = splitted[1].strip();
        TimespanUnit tu = TimespanUnit.fromText(unit);
        if (unit == null) {
            return null;
        }
        try {
            long v = Long.parseLong(value);
            if (v >= 0) {
                return new Rate(v, tu);
            }
        } catch (NumberFormatException nfe) {
            // Ignore
        }
        return null;
    }

    public boolean isHigher(Rate that) {
        return this.inNanos() > that.inNanos();
    }

    private double inNanos() {
        return (double) amount / unit.nanos;
    }

    public double perSecond() {
        return inNanos() * 1_000_000_000.0;
    }
}
