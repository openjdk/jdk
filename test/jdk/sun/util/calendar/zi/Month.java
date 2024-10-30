/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Month enum handles month related manipulation.
 *
 * @since 1.4
 */
enum Month {
    JANUARY("Jan"),
    FEBRUARY("Feb"),
    MARCH("Mar"),
    APRIL("Apr"),
    MAY("May"),
    JUNE("Jun"),
    JULY("Jul"),
    AUGUST("Aug"),
    SEPTEMBER("Sep"),
    OCTOBER("Oct"),
    NOVEMBER("Nov"),
    DECEMBER("Dec");

    private final String abbr;

    private Month(String abbr) {
        this.abbr = abbr;
    }

    int value() {
        return ordinal() + 1;
    }

    /**
     * Parses the specified string as a month abbreviation.
     * @param name the month abbreviation
     * @return the Month value
     */
    static Month parse(String name) {
        int len = name.length();

        if (name.regionMatches(true, 0, "January", 0, len)) return Month.JANUARY;
        if (name.regionMatches(true, 0, "February", 0, len)) return Month.FEBRUARY;
        if (name.regionMatches(true, 0, "March", 0, len)) return Month.MARCH;
        if (name.regionMatches(true, 0, "April", 0, len)) return Month.APRIL;
        if (name.regionMatches(true, 0, "May", 0, len)) return Month.MAY;
        if (name.regionMatches(true, 0, "June", 0, len)) return Month.JUNE;
        if (name.regionMatches(true, 0, "July", 0, len)) return Month.JULY;
        if (name.regionMatches(true, 0, "August", 0, len)) return Month.AUGUST;
        if (name.regionMatches(true, 0, "September", 0, len)) return Month.SEPTEMBER;
        if (name.regionMatches(true, 0, "October", 0, len)) return Month.OCTOBER;
        if (name.regionMatches(true, 0, "November", 0, len)) return Month.NOVEMBER;
        if (name.regionMatches(true, 0, "December", 0, len)) return Month.DECEMBER;

        throw new IllegalArgumentException("Unknown month: " + name);
    }

    /**
     * @param month the nunmth number (1-based)
     * @return the month name in uppercase of the specified month
     */
    static String toString(int month) {
        if (month >= JANUARY.value() && month <= DECEMBER.value()) {
            return "Calendar." + Month.values()[month - 1];
        }
        throw new IllegalArgumentException("wrong month number: " + month);
    }
}
