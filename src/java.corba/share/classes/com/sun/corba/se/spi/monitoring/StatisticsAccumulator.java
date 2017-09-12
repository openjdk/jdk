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
 * StatisticsAccumulator accumulates the samples provided by the user and
 * computes the value of minimum, maximum, sum and sample square sum. When
 * the StatisticMonitoredAttribute calls getValue(), it will compute all
 * the statistics for the collected samples (Which are Minimum, Maximum,
 * Average, StandardDeviation) and provides a nice printable record as a
 * String.
 *
 * Users can easily extend this class and provide the implementation of
 * toString() method to format the stats as desired. By default all the stats
 * are printed in a single line.
 */
public class StatisticsAccumulator {

  ///////////////////////////////////////
  // attributes


    // Users can extend this class to get access to current Max value
    protected double max = Double.MIN_VALUE;

    // Users can extend this class to get access to current Min value
    protected double min = Double.MAX_VALUE;

    private double sampleSum;

    private double sampleSquareSum;

    private long sampleCount;

    protected String unit;



  ///////////////////////////////////////
  // operations



/**
 * User will use this method to just register a sample with the
 * StatisticsAccumulator. This is the only method that User will use to
 * expose the statistics, internally the StatisticMonitoredAttribute will
 * collect the information when requested from the ASAdmin.
 *
 * @param value a double value to make it more precise
 */
    public void sample(double value) {
        sampleCount++;
        if( value < min )  min = value;
        if( value > max) max = value;
        sampleSum += value;
        sampleSquareSum += (value * value);
    } // end sample



    /**
     *  Computes the Standard Statistic Results based on the samples collected
     *  so far and provides the complete value as a formatted String
     */
    public String getValue( ) {
        return toString();
    }

    /**
     *  Users can extend StatisticsAccumulator to provide the complete
     *  Stats in the format they prefer, if the default format doesn't suffice.
     */
    public String toString( ) {
        return "Minimum Value = " + min + " " + unit + " " +
            "Maximum Value = " + max + " " + unit + " " +
            "Average Value = " + computeAverage() + " " +  unit + " " +
            "Standard Deviation = " + computeStandardDeviation() + " " + unit +
            " " + "Samples Collected = " + sampleCount;
    }

    /**
     *  If users choose to custom format the stats.
     */
    protected double computeAverage( ) {
        return (sampleSum / sampleCount);
    }


    /**
     * We use a derived Standard Deviation formula to compute SD. This way
     * there is no need to hold on to all the samples provided.
     *
     * The method is protected to let users extend and format the results.
     */
    protected double computeStandardDeviation( ) {
        double sampleSumSquare = sampleSum * sampleSum;
        return Math.sqrt(
            (sampleSquareSum-((sampleSumSquare)/sampleCount))/(sampleCount-1));
    }

/**
 * Construct the Statistics Accumulator by providing the unit as a String.
 * The examples of units are "Hours", "Minutes",
 * "Seconds", "MilliSeconds", "Micro Seconds" etc.
 *
 * @param unit a String representing the units for the samples collected
 */
    public StatisticsAccumulator( String unit ) {
        this.unit = unit;
        sampleCount = 0;
        sampleSum = 0;
        sampleSquareSum = 0;
    }


    /**
     *  Clears the samples and starts fresh on new samples.
     */
    void clearState( ) {
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
        sampleCount = 0;
        sampleSum = 0;
        sampleSquareSum = 0;
    }

    /**
     *  This is an internal API to test StatisticsAccumulator...
     */
    public void unitTestValidate( String expectedUnit, double expectedMin,
        double expectedMax, long expectedSampleCount, double expectedAverage,
        double expectedStandardDeviation )
    {
        if( !expectedUnit.equals( unit ) ){
            throw new RuntimeException(
                "Unit is not same as expected Unit" +
                "\nUnit = " + unit + "ExpectedUnit = " + expectedUnit );
        }
        if( min != expectedMin ) {
            throw new RuntimeException(
                "Minimum value is not same as expected minimum value" +
                "\nMin Value = " + min + "Expected Min Value = " + expectedMin);
        }
        if( max != expectedMax ) {
            throw new RuntimeException(
                "Maximum value is not same as expected maximum value" +
                "\nMax Value = " + max + "Expected Max Value = " + expectedMax);
        }
        if( sampleCount != expectedSampleCount ) {
            throw new RuntimeException(
                "Sample count is not same as expected Sample Count" +
                "\nSampleCount = " + sampleCount + "Expected Sample Count = " +
                expectedSampleCount);
        }
        if( computeAverage() != expectedAverage ) {
            throw new RuntimeException(
                "Average is not same as expected Average" +
                "\nAverage = " + computeAverage() + "Expected Average = " +
                expectedAverage);
        }
        // We are computing Standard Deviation from two different methods
        // for comparison. So, the values will not be the exact same to the last
        // few digits. So, we are taking the difference and making sure that
        // the difference is not greater than 1.
        double difference = Math.abs(
            computeStandardDeviation() - expectedStandardDeviation);
        if( difference > 1 ) {
            throw new RuntimeException(
                "Standard Deviation is not same as expected Std Deviation" +
                "\nStandard Dev = " + computeStandardDeviation() +
                "Expected Standard Dev = " + expectedStandardDeviation);
        }
    }


} // end StatisticsAccumulator
