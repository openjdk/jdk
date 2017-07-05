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

public interface Stats {

    /**
     * Get a Statistic by name.
     */
    Statistic getStatistic(String statisticName);

    /**
     * Returns an array of Strings which are the names of the attributes from the specific Stats submodel that this object supports. Attributes named in the list must correspond to attributes that will return a Statistic object of the appropriate type which contains valid performance data.  The return value of attributes in the Stats submodel that are not included in the statisticNames list must be null. For each name in the statisticNames list there must be one Statistic with the same name in the statistics list.
     */
    String [] getStatisticNames();

    /**
     * Returns an array containing all of the Statistic objects supported by this Stats object.
     */
    Statistic[] getStatistics();
}
