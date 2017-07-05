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
import com.sun.org.glassfish.external.statistics.RangeStatistic;
import java.util.Map;
import java.lang.reflect.*;

/**
 * @author Sreenivas Munnangi
 */
public final class RangeStatisticImpl extends StatisticImpl
    implements RangeStatistic, InvocationHandler {

    private long currentVal = 0L;
    private long highWaterMark = Long.MIN_VALUE;
    private long lowWaterMark = Long.MAX_VALUE;
    private final long initCurrentVal;
    private final long initHighWaterMark;
    private final long initLowWaterMark;

    private final RangeStatistic rs =
            (RangeStatistic) Proxy.newProxyInstance(
            RangeStatistic.class.getClassLoader(),
            new Class[] { RangeStatistic.class },
            this);

    public RangeStatisticImpl(long curVal, long highMark, long lowMark,
                              String name, String unit, String desc,
                              long startTime, long sampleTime) {
        super(name, unit, desc, startTime, sampleTime);
        currentVal = curVal;
        initCurrentVal = curVal;
        highWaterMark = highMark;
        initHighWaterMark = highMark;
        lowWaterMark = lowMark;
        initLowWaterMark = lowMark;
    }

    public synchronized RangeStatistic getStatistic() {
        return rs;
    }

    public synchronized Map getStaticAsMap() {
        Map m = super.getStaticAsMap();
        m.put("current", getCurrent());
        m.put("lowwatermark", getLowWaterMark());
        m.put("highwatermark", getHighWaterMark());
        return m;
    }

    public synchronized long getCurrent() {
        return currentVal;
    }

    public synchronized void setCurrent(long curVal) {
        currentVal = curVal;
        lowWaterMark = (curVal >= lowWaterMark ? lowWaterMark : curVal);
        highWaterMark = (curVal >= highWaterMark ? curVal : highWaterMark);
        sampleTime = System.currentTimeMillis();
    }

    /**
     * Returns the highest value of this statistic, since measurement started.
     */
    public synchronized long getHighWaterMark() {
        return highWaterMark;
    }

    public synchronized void setHighWaterMark(long hwm) {
        highWaterMark = hwm;
    }

    /**
     * Returns the lowest value of this statistic, since measurement started.
     */
    public synchronized long getLowWaterMark() {
        return lowWaterMark;
    }

    public synchronized void setLowWaterMark(long lwm) {
        lowWaterMark = lwm;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        currentVal = initCurrentVal;
        highWaterMark = initHighWaterMark;
        lowWaterMark = initLowWaterMark;
        sampleTime = -1L;
    }

    public synchronized String toString() {
        return super.toString() + NEWLINE +
            "Current: " + getCurrent() + NEWLINE +
            "LowWaterMark: " + getLowWaterMark() + NEWLINE +
            "HighWaterMark: " + getHighWaterMark();
    }

    // todo: equals implementation
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        checkMethod(m);

        Object result;
        try {
            result = m.invoke(this, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } catch (Exception e) {
            throw new RuntimeException("unexpected invocation exception: " +
                       e.getMessage());
        }
        return result;
    }
}
