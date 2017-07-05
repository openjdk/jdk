/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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



package com.sun.org.glassfish.external.statistics.impl;
import com.sun.org.glassfish.external.statistics.Stats;
import com.sun.org.glassfish.external.statistics.Statistic;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author Jennifer Chou
 */
public final class StatsImpl implements Stats {

    private final StatisticImpl[] statArray;

    protected StatsImpl(StatisticImpl[] statisticArray) {
        statArray = statisticArray;
    }

    public synchronized Statistic getStatistic(String statisticName) {
        Statistic stat = null;
        for (Statistic s : statArray) {
            if (s.getName().equals(statisticName)) {
                stat = s;
                break;
            }
        }
        return stat;
    }

    public synchronized String[] getStatisticNames() {
        ArrayList list = new ArrayList();
        for (Statistic s : statArray) {
            list.add(s.getName());
        }
        String[] strArray = new String[list.size()];
        return (String[])list.toArray(strArray);
    }

    public synchronized Statistic[] getStatistics() {
        return this.statArray;
    }

    /**
     * Call reset on all of the Statistic objects contained by this Stats object
     */
    public synchronized void reset() {
        for (StatisticImpl s : statArray) {
            s.reset();
        }
    };


}
