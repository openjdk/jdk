/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Instant;

import jdk.jfr.internal.util.Output;

/**
 * Holds information on how a query should be rendered.
 */
public final class Configuration {
    public static final int MAX_PREFERRED_WIDTH = 120;
    public static final int MIN_PREFERRED_WIDTH = 40;
    public static final int PREFERRED_WIDTH = 80;

    public enum Truncate {
        BEGINNING, END
    }

    /**
     * Where the rendered result should be printed.
     */
    public Output output;

    /**
     * The title of the table or form.
     * <p>
     * {@code null) means no title.
     */
    public String title;

    /**
     * Truncation mode if text overflows.
     * <p>
     * If truncate is not set, it will be determined by heuristics.
     */
    public Truncate truncate;

    /**
     * Height of table cells.
     * <p>
     * If cellHeight is not set, it will be determined by heuristics.
     */
    public int cellHeight;

    /**
     * Width of a table or form.
     * <p>
     * If width is not set, it will be determined by heuristics.
     */
    public int width;

    /**
     * If additional information should be printed.
     */
    public boolean verbose;

    /**
     * If symbolic names should be printed for table headers.
     */
    public boolean verboseHeaders;

    /**
     * If the timespan of the table or form should be printed.
     */
    public boolean verboseTimespan;

    /**
     * If the title of the table or form should be printed.
     */
    public boolean verboseTitle;

    /**
     * The start time for the query.
     * <p>
     * {@code null) means no start time.
     */
    public Instant startTime;

    /**
     * The end time for the query.
     * <p>
     * {@code null) means no end time.
     */
    public Instant endTime;
}
