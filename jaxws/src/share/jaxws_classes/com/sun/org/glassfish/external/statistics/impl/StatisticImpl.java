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


package com.sun.org.glassfish.external.statistics.impl;
import com.sun.org.glassfish.external.statistics.Statistic;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sreenivas Munnangi
 */
public abstract class StatisticImpl implements Statistic {

    private final String statisticName;
    private final String statisticUnit;
    private final String statisticDesc;
    protected long sampleTime = -1L;
    private long startTime;
    public static final String UNIT_COUNT = "count";
    public static final String UNIT_SECOND = "second";
    public static final String UNIT_MILLISECOND = "millisecond";
    public static final String UNIT_MICROSECOND = "microsecond";
    public static final String UNIT_NANOSECOND = "nanosecond";
    public static final String START_TIME = "starttime";
    public static final String LAST_SAMPLE_TIME = "lastsampletime";

    protected final Map<String, Object> statMap = new ConcurrentHashMap<String, Object> ();

    protected static final String NEWLINE = System.getProperty( "line.separator" );

    protected StatisticImpl(String name, String unit, String desc,
                          long start_time, long sample_time) {

        if (isValidString(name)) {
            statisticName = name;
        } else {
            statisticName = "name";
        }

        if (isValidString(unit)) {
            statisticUnit = unit;
        } else {
            statisticUnit = "unit";
        }

        if (isValidString(desc)) {
            statisticDesc = desc;
        } else {
            statisticDesc = "description";
        }

        startTime = start_time;
        sampleTime = sample_time;
    }

    protected StatisticImpl(String name, String unit, String desc) {
        this(name, unit, desc, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public synchronized Map getStaticAsMap() {
        if (isValidString(statisticName)) {
            statMap.put("name", statisticName);
        }
        if (isValidString(statisticUnit)) {
            statMap.put("unit", statisticUnit);
        }
        if (isValidString(statisticDesc)) {
            statMap.put("description", statisticDesc);
        }
        statMap.put(StatisticImpl.START_TIME, startTime);
        statMap.put(StatisticImpl.LAST_SAMPLE_TIME, sampleTime);
        return statMap;
    }

    public String getName() {
        return this.statisticName;
    }

    public String getDescription() {
        return this.statisticDesc;
    }

    public String getUnit() {
        return this.statisticUnit;
    }

    public synchronized long getLastSampleTime() {
        return sampleTime;
    }

    public synchronized long getStartTime() {
        return startTime;
    }

    public synchronized void reset() {
        startTime = System.currentTimeMillis();
    }

    public synchronized String toString() {
        return "Statistic " + getClass().getName() + NEWLINE +
            "Name: " + getName() + NEWLINE +
            "Description: " + getDescription() + NEWLINE +
            "Unit: " + getUnit() + NEWLINE +
            "LastSampleTime: " + getLastSampleTime() + NEWLINE +
            "StartTime: " + getStartTime();
    }

    protected static boolean isValidString(String str) {
        return (str!=null && str.length()>0);
    }

    protected void checkMethod(Method method) {
        if (method == null || method.getDeclaringClass() == null
                || !Statistic.class.isAssignableFrom(method.getDeclaringClass())
                || Modifier.isStatic(method.getModifiers())) {
            throw new RuntimeException("Invalid method on invoke");
        }
    }

}
