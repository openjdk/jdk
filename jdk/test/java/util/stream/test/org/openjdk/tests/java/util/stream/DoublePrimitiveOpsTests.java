/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import static org.testng.Assert.assertEquals;

@Test
public class DoublePrimitiveOpsTests {

    // @@@ tests for double are fragile if relying on equality when accumulating and multiplying values

    public void testUnBox() {
        double sum = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0).stream().mapToDouble(i -> i).reduce(0.0, Double::sum);
        assertEquals(sum, 1.0 + 2.0 + 3.0 + 4.0 + 5.0);
    }

    public void testToArray() {
        {
            double[] array =  LongStream.range(1, 10).asDoubleStream().map(i -> i * 2).toArray();
            assertEquals(array, new double[]{2, 4, 6, 8, 10, 12, 14, 16, 18});
        }

        {
            double[] array =  LongStream.range(1, 10).parallel().asDoubleStream().map(i -> i * 2).toArray();
            assertEquals(array, new double[]{2, 4, 6, 8, 10, 12, 14, 16, 18});
        }
    }

    public void testSort() {
        Random r = new Random();

        double[] content = DoubleStream.generate(() -> r.nextDouble()).limit(10).toArray();
        double[] sortedContent = content.clone();
        Arrays.sort(sortedContent);

        {
            double[] array =  Arrays.stream(content).sorted().toArray();
            assertEquals(array, sortedContent);
        }

        {
            double[] array =  Arrays.stream(content).parallel().sorted().toArray();
            assertEquals(array, sortedContent);
        }
    }

    public void testSortSort() {
        Random r = new Random();

        double[] content = DoubleStream.generate(() -> r.nextDouble()).limit(10).toArray();
        double[] sortedContent = content.clone();
        Arrays.sort(sortedContent);

        {
            double[] array =  Arrays.stream(content).sorted().sorted().toArray();
            assertEquals(array, sortedContent);
        }

        {
            double[] array =  Arrays.stream(content).parallel().sorted().sorted().toArray();
            assertEquals(array, sortedContent);
        }
    }

    public void testLimit() {
        double[] expected = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };

        {
            double[] actual = DoubleStream.iterate(1.0, i -> i + 1.0).limit(9).toArray();
            Assert.assertTrue(Arrays.equals(expected, actual));
        }

        {
            double[] actual = LongStream.range(1, 100).parallel().asDoubleStream().limit(9).toArray();
            Assert.assertTrue(Arrays.equals(expected, actual));
        }
    }
}
