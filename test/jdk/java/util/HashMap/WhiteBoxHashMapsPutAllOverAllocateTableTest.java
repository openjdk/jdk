/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/*
 * @test
 * @bug 8281631
 * @summary HashMap copy constructor and putAll can over-allocate table
 * @author  Xeno Amess
 *
 * @run junit/othervm/timeout=1000
 *      --add-opens java.base/java.lang=ALL-UNNAMED
 *      --add-opens java.base/java.util=ALL-UNNAMED
 *      WhiteBoxHashMapsPutAllOverAllocateTableTest
 */
@RunWith(Parameterized.class)
public class WhiteBoxHashMapsPutAllOverAllocateTableTest<T extends Map> {

    private static final int TEST_SIZE = 128;
    private static final Integer[] INTEGER_ARRAY = new Integer[TEST_SIZE];

    static {
        for (int i = 0; i < TEST_SIZE; ++i) {
            INTEGER_ARRAY[i] = i;
        }
    }

    private static <T extends Map> Object[] testCase(
            WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite,
            int mapSize
    ) {
        return new Object[]{
                whiteBoxHashMapTestSuite,
                mapSize
        };
    }

    @Parameterized.Parameters
    public static List<Object[]> testFunctionsList() {
        List<Object[]> testParameters = new ArrayList<>(TEST_SIZE * 3);
        for (int i = 0; i <= TEST_SIZE; ++i) {
            testParameters.add(
                    testCase(
                            WhiteBoxHashMapTestUtil.HASH_MAP_TEST_SUITE,
                            i
                    )
            );
            testParameters.add(
                    testCase(
                            WhiteBoxHashMapTestUtil.LINKED_HASH_MAP_TEST_SUITE,
                            i
                    )
            );
            testParameters.add(
                    testCase(
                            WhiteBoxHashMapTestUtil.WEAK_HASH_MAP_TEST_SUITE,
                            i
                    )
            );
        }
        return testParameters;
    }

    private final WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite;

    private final int mapSize;

    private final String testName;

    public WhiteBoxHashMapsPutAllOverAllocateTableTest(
            WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite,
            int mapSize
    ) {
        this.whiteBoxHashMapTestSuite = whiteBoxHashMapTestSuite;
        this.mapSize = mapSize;
        this.testName = this.whiteBoxHashMapTestSuite.getMapClass().getName();
    }

    public static void fillN(int mapSize, Map<Object, Object> map) {
        for (int i = 0; i < mapSize; i++) {
            map.put(INTEGER_ARRAY[i], INTEGER_ARRAY[i]);
        }
    }

    @Test
    public void test() throws IllegalAccessException {

        T a = whiteBoxHashMapTestSuite.getCreateNewMap().get();
        fillN(mapSize, a);
        int lengthA = whiteBoxHashMapTestSuite.getGetArrayLength().apply(a);
        {
            T b = whiteBoxHashMapTestSuite.getCreateNewMapWithCapacity().apply(mapSize);
            fillN(mapSize, b);
            int length = whiteBoxHashMapTestSuite.getGetArrayLength().apply(b);
            Assert.assertTrue(
                    testName + " : " + "length b larger than length a!",
                    length <= lengthA
            );
        }

        {
            T c = whiteBoxHashMapTestSuite.getCreateNewMapWithMap().apply(a);
            int length = whiteBoxHashMapTestSuite.getGetArrayLength().apply(c);
            Assert.assertTrue(
                    testName + " : " + "length c larger than length a!",
                    length <= lengthA
            );
        }

        {
            T d = whiteBoxHashMapTestSuite.getCreateNewMap().get();
            d.putAll(a);
            int length = whiteBoxHashMapTestSuite.getGetArrayLength().apply(d);
            Assert.assertTrue(
                    testName + " : " + "length d larger than length a!",
                    length <= lengthA
            );
        }

    }

}
