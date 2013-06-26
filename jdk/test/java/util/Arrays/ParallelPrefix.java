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

/**
 * @test
 * @summary unit test for Arrays.ParallelPrefix().
 * @author Tristan Yan
 * @run testng ParallelPrefix
 */

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ParallelPrefix {
    //Array size less than MIN_PARTITION
    private final static int SMALL_ARRAY_SIZE = 1 << 3;

    //Array size equals MIN_PARTITION
    private final static int THRESHOLD_ARRAY_SIZE = 1 << 4;

    //Array size greater than MIN_PARTITION
    private final static int MEDIUM_ARRAY_SIZE = 1 << 8;

    //Array size much greater than MIN_PARTITION
    private final static int LARGE_ARRAY_SIZE = 1 << 12;

    private final static int[] ARRAY_SIZE_COLLECTION  = new int[]{
        SMALL_ARRAY_SIZE, THRESHOLD_ARRAY_SIZE,MEDIUM_ARRAY_SIZE, LARGE_ARRAY_SIZE};

    @DataProvider
    public static Object[][] intSet(){
        return genericData(size -> IntStream.range(0, size).toArray(), new IntBinaryOperator[]{Integer::sum, Integer::min});
    }

    @DataProvider
    public static Object[][] longSet(){
        return genericData(size -> LongStream.range(0, size).toArray(), new LongBinaryOperator[]{Long::sum, Long::min});
    }

    @DataProvider
    public static Object[][] doubleSet(){
        return genericData(size -> IntStream.range(0, size).mapToDouble(i -> (double)i).toArray(),
                new DoubleBinaryOperator[]{Double::sum, Double::min});
    }

    @DataProvider
    public static Object[][] stringSet(){
        Function<Integer, String[]> stringsFunc = size ->
                IntStream.range(0, size).mapToObj(Integer::toString).toArray(String[]::new);
        BinaryOperator<String> cancatBop = String::concat;
        return genericData(stringsFunc,  new BinaryOperator[]{cancatBop});
    }

    private static <T, OPS> Object[][] genericData(Function<Integer, T> generateFunc, OPS[] ops) {
        //test arrays which size is equals n-1, n, n+1, test random data
        Object[][] data = new Object[ARRAY_SIZE_COLLECTION.length * 3 * ops.length][4];
        for(int n = 0; n < ARRAY_SIZE_COLLECTION.length; n++ ) {
            for(int testValue = -1 ; testValue <= 1; testValue++) {
                int array_size = ARRAY_SIZE_COLLECTION[n] + testValue;
                for(int opsN = 0; opsN < ops.length; opsN++) {
                    int index = n * 3 * ops.length + (testValue + 1) * ops.length + opsN;
                    data[index][0] = generateFunc.apply(array_size);
                    data[index][1] = array_size / 3;
                    data[index][2] = 2 * array_size / 3;
                    data[index][3] = ops[opsN];
                }
            }
        }
        return data;
    }

    @Test(dataProvider="intSet")
    public void testParallelPrefixForInt(int[] data, int fromIndex, int toIndex, IntBinaryOperator op) {
        int[] sequentialResult = data.clone();
        for (int index = fromIndex + 1; index < toIndex; index++) {
            sequentialResult[index ] = op.applyAsInt(sequentialResult[index  - 1], sequentialResult[index]);
        }

        int[] parallelResult = data.clone();
        Arrays.parallelPrefix(parallelResult, fromIndex, toIndex, op);
        assertEquals(parallelResult, sequentialResult);

        int[] parallelRangeResult = Arrays.copyOfRange(data, fromIndex, toIndex);
        Arrays.parallelPrefix(parallelRangeResult, op);
        assertEquals(parallelRangeResult, Arrays.copyOfRange(sequentialResult, fromIndex, toIndex));
    }

    @Test(dataProvider="longSet")
    public void testParallelPrefixForLong(long[] data, int fromIndex, int toIndex, LongBinaryOperator op) {
        long[] sequentialResult = data.clone();
        for (int index = fromIndex + 1; index < toIndex; index++) {
            sequentialResult[index ] = op.applyAsLong(sequentialResult[index  - 1], sequentialResult[index]);
        }

        long[] parallelResult = data.clone();
        Arrays.parallelPrefix(parallelResult, fromIndex, toIndex, op);
        assertEquals(parallelResult, sequentialResult);

        long[] parallelRangeResult = Arrays.copyOfRange(data, fromIndex, toIndex);
        Arrays.parallelPrefix(parallelRangeResult, op);
        assertEquals(parallelRangeResult, Arrays.copyOfRange(sequentialResult, fromIndex, toIndex));
    }

    @Test(dataProvider="doubleSet")
    public void testParallelPrefixForDouble(double[] data, int fromIndex, int toIndex, DoubleBinaryOperator op) {
        double[] sequentialResult = data.clone();
        for (int index = fromIndex + 1; index < toIndex; index++) {
            sequentialResult[index ] = op.applyAsDouble(sequentialResult[index  - 1], sequentialResult[index]);
        }

        double[] parallelResult = data.clone();
        Arrays.parallelPrefix(parallelResult, fromIndex, toIndex, op);
        assertEquals(parallelResult, sequentialResult);

        double[] parallelRangeResult = Arrays.copyOfRange(data, fromIndex, toIndex);
        Arrays.parallelPrefix(parallelRangeResult, op);
        assertEquals(parallelRangeResult, Arrays.copyOfRange(sequentialResult, fromIndex, toIndex));
    }

    @Test(dataProvider="stringSet")
    public void testParallelPrefixForStringr(String[] data , int fromIndex, int toIndex, BinaryOperator<String> op) {
        String[] sequentialResult = data.clone();
        for (int index = fromIndex + 1; index < toIndex; index++) {
            sequentialResult[index ] = op.apply(sequentialResult[index  - 1], sequentialResult[index]);
        }

        String[] parallelResult = data.clone();
        Arrays.parallelPrefix(parallelResult, fromIndex, toIndex, op);
        assertEquals(parallelResult, sequentialResult);

        String[] parallelRangeResult = Arrays.copyOfRange(data, fromIndex, toIndex);
        Arrays.parallelPrefix(parallelRangeResult, op);
        assertEquals(parallelRangeResult, Arrays.copyOfRange(sequentialResult, fromIndex, toIndex));
    }
}

