/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.monitoring;

import java.util.*;

/**
 * @author Hemanth Puttaswamy
 *
 * StatisticsMonitoredAttribute is provided as a convenience to collect the
 * Statistics of any entity. The getValue() call will be delegated to the
 * StatisticsAccumulator set by the user.
 */
public class StatisticMonitoredAttribute extends MonitoredAttributeBase {


    // Every StatisticMonitoredAttribute will have a StatisticAccumulator. User
    // will use Statisticsaccumulator to accumulate the samples associated with
    // this Monitored Attribute
    private StatisticsAccumulator statisticsAccumulator;

    // Mutex is passed from the user class which is providing the sample values.
    // getValue() and clearState() is synchronized on this user provided mutex
    private Object  mutex;


  ///////////////////////////////////////
  // operations


/**
 * Constructs the StaisticMonitoredAttribute, builds the required
 * MonitoredAttributeInfo with Long as the class type and is always
 * readonly attribute.
 *
 * @param name Of this attribute
 * @param desc should provide a good description on the kind of statistics
 * collected, a good example is "Connection Response Time Stats will Provide the
 * detailed stats based on the samples provided from every request completion
 * time"
 * @param s is the StatisticsAcumulator that user will use to accumulate the
 * samples and this Attribute Object will get the computed statistics values
 * from.
 * @param mutex using which clearState() and getValue() calls need to be locked.
 */
    public  StatisticMonitoredAttribute(String name, String desc,
        StatisticsAccumulator s, Object mutex)
    {
        super( name );
        MonitoredAttributeInfoFactory f =
            MonitoringFactories.getMonitoredAttributeInfoFactory();
        MonitoredAttributeInfo maInfo = f.createMonitoredAttributeInfo(
                desc, String.class, false, true );

        this.setMonitoredAttributeInfo( maInfo );
        this.statisticsAccumulator = s;
        this.mutex = mutex;
    } // end StatisticMonitoredAttribute



/**
 *  Gets the value from the StatisticsAccumulator, the value will be a formatted
 *  String with the computed statistics based on the samples accumulated in the
 *  Statistics Accumulator.
 */
    public Object getValue( ) {
        synchronized( mutex ) {
            return statisticsAccumulator.getValue( );
        }
    }

/**
 *  Clears the state on Statistics Accumulator, After this call all samples are
 *  treated fresh and the old sample computations are disregarded.
 */
    public void clearState( ) {
        synchronized( mutex ) {
            statisticsAccumulator.clearState( );
        }
    }

/**
 *  Gets the statistics accumulator associated with StatisticMonitoredAttribute.
 *  Usually, the user don't need to use this method as they can keep the handle
 *  to Accumulator to collect the samples.
 */
    public StatisticsAccumulator getStatisticsAccumulator( ) {
        return statisticsAccumulator;
    }
} // end StatisticMonitoredAttribute
