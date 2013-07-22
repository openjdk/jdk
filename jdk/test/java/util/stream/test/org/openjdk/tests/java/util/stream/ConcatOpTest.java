/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.stream.OpTestCase;
import java.util.stream.StreamTestDataProvider;

import org.testng.annotations.Test;

import java.util.stream.Stream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;
import java.util.stream.TestData;

import static java.util.stream.LambdaTestHelpers.*;

public class ConcatOpTest extends OpTestCase {

    // Sanity to make sure all type of stream source works
    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testOpsSequential(String name, TestData.OfRef<Integer> data) {
        exerciseOpsInt(data,
                       s -> Stream.concat(s, data.stream()),
                       s -> IntStream.concat(s, data.stream().mapToInt(Integer::intValue)),
                       s -> LongStream.concat(s, data.stream().mapToLong(Integer::longValue)),
                       s -> DoubleStream.concat(s, data.stream().mapToDouble(Integer::doubleValue)));
    }
}
