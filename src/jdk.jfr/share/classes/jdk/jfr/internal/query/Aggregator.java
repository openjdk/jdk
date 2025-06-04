/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

/**
 * Enum describing the different ways values can be aggregated.
 */
enum Aggregator {
    /**
     * Dummy to indicate no aggregation is being used.
     */
    MISSING(" "),
    /**
     * Calculate the average value of all finite numeric values.
     */
    AVERAGE("AVG"),
    /**
     * Calculate the number of elements, including {@code null}.
     */
    COUNT("COUNT"),
    /**
     * Calculate the difference between the last and first finite numeric value.
     */
    DIFFERENCE("DIFF"),
    /**
     * The first value, including {@code null}.
     */
    FIRST("FIRST"),
    /**
     * The last value, including {@code null}.
     */
    LAST("LAST"),
    /**
     * Aggregate values into a comma-separated list, including {@code null}.
     */
    LIST("LIST"),
    /**
     * Aggregate unique values into a comma-separated list, including {@code null}.
     */
    SET("SET"),
    /**
     * The highest numeric value.
     */
    MAXIMUM("MAX"),
    /**
     * The median of all finite numeric values.
     */
    MEDIAN("MEDIAN"),
    /**
     * The lowest numeric value.
     */
    MINIMUM("MIN"),
    /**
     * Calculate the 90th percentile of all finite numeric values.
     */
    P90("P90"),
    /**
     * Calculate the 95th percentile of all finite numeric values.
     */
    P95("P95"),
    /**
     * Calculate the 99th percentile of all finite numeric values.
     */
    P99("P99"),
    /**
     * Calculate the 99.9th percentile of all finite numeric values.
     */
    P999("P999"),
    /**
     * Calculate the standard deviation of all finite numeric values.
     */
    STANDARD_DEVIATION("STDEV"),
    /**
     * Calculate the sum of all finite numeric values.
     */
    SUM("SUM"),
    /**
     * Calculates the number of distinct values determined by invoking Object.equals.
     */
    UNIQUE("UNIQUE"),
    /**
     * The last elements, for an event type, that all share the same end timestamp.
     */
    LAST_BATCH("LAST_BATCH");

    public final String name;

    private Aggregator(String name) {
        this.name = name;
    }
}