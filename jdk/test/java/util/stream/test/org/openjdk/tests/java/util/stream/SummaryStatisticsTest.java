/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.Collectors;
import java.util.stream.OpTestCase;

import org.testng.annotations.Test;

import static java.util.stream.LambdaTestHelpers.countTo;

/**
 * TestSummaryStatistics
 *
 * @author Brian Goetz
 */
@Test
public class SummaryStatisticsTest extends OpTestCase {
    public void testIntStatistics() {
        List<IntSummaryStatistics> instances = new ArrayList<>();
        instances.add(countTo(1000).stream().collect(Collectors.summarizingInt(i -> i)));
        instances.add(countTo(1000).stream().mapToInt(i -> i).summaryStatistics());
        instances.add(countTo(1000).parallelStream().collect(Collectors.summarizingInt(i -> i)));
        instances.add(countTo(1000).parallelStream().mapToInt(i -> i).summaryStatistics());

        for (IntSummaryStatistics stats : instances) {
            assertEquals(stats.getCount(), 1000);
            assertEquals(stats.getSum(), countTo(1000).stream().mapToInt(i -> i).sum());
            assertEquals(stats.getMax(), 1000);
            assertEquals(stats.getMin(), 1);
        }
    }

    public void testLongStatistics() {
        List<LongSummaryStatistics> instances = new ArrayList<>();
        instances.add(countTo(1000).stream().collect(Collectors.summarizingLong(i -> i)));
        instances.add(countTo(1000).stream().mapToLong(i -> i).summaryStatistics());
        instances.add(countTo(1000).parallelStream().collect(Collectors.summarizingLong(i -> i)));
        instances.add(countTo(1000).parallelStream().mapToLong(i -> i).summaryStatistics());

        for (LongSummaryStatistics stats : instances) {
            assertEquals(stats.getCount(), 1000);
            assertEquals(stats.getSum(), (long) countTo(1000).stream().mapToInt(i -> i).sum());
            assertEquals(stats.getMax(), 1000L);
            assertEquals(stats.getMin(), 1L);
        }
    }

    public void testDoubleStatistics() {
        List<DoubleSummaryStatistics> instances = new ArrayList<>();
        instances.add(countTo(1000).stream().collect(Collectors.summarizingDouble(i -> i)));
        instances.add(countTo(1000).stream().mapToDouble(i -> i).summaryStatistics());
        instances.add(countTo(1000).parallelStream().collect(Collectors.summarizingDouble(i -> i)));
        instances.add(countTo(1000).parallelStream().mapToDouble(i -> i).summaryStatistics());

        for (DoubleSummaryStatistics stats : instances) {
            assertEquals(stats.getCount(), 1000);
            assertEquals(stats.getSum(), (double) countTo(1000).stream().mapToInt(i -> i).sum());
            assertEquals(stats.getMax(), 1000.0);
            assertEquals(stats.getMin(), 1.0);
        }
    }
}
