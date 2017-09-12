/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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



package com.sun.org.glassfish.external.statistics;

/**
 * Specifies standard timing measurements.
 */
public interface TimeStatistic extends Statistic {
    /**
     * Number of times the operation was invoked since the beginning of this measurement.
     */
    long getCount();

    /**
     * The maximum amount of time taken to complete one invocation of this operation since the beginning of this measurement.
     */
    long getMaxTime();

    /**
     * The minimum amount of time taken to complete one invocation of this operation since the beginning of this measurement.
     */
    long getMinTime();

    /**
     * This is the sum total of time taken to complete every invocation of this operation since the beginning of this measurement. Dividing totalTime by count will give you the average execution time for this operation.
     */
    long getTotalTime();
}
