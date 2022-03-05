/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

/*
 * @test
 * @bug 8210280
 * @summary White box tests for hashMap Initial capacity
 *
 * @run junit/othervm/timeout=1000
 *      --add-opens java.base/java.lang=ALL-UNNAMED
 *      --add-opens java.base/java.util=ALL-UNNAMED
 *      WhiteBoxHashMapInitialCapacityTest
 */
@RunWith(Parameterized.class)
public class WhiteBoxHashMapInitialCapacityTest<T extends Map> {

    @Parameterized.Parameters
    public static List<Object[]> testFunctionsList() {
        return List.of(
                new Object[]{WhiteBoxHashMapTestUtil.HASH_MAP_TEST_SUITE},
                new Object[]{WhiteBoxHashMapTestUtil.LINKED_HASH_MAP_TEST_SUITE}
        );
    }

    private final WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite;

    public WhiteBoxHashMapInitialCapacityTest(
            WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite
    ) {
        this.whiteBoxHashMapTestSuite = whiteBoxHashMapTestSuite;
    }

    @Test
    public void capacityTestInitialCapacity() {
        int initialCapacity = ThreadLocalRandom.current().nextInt(2, 128);
        capacityTestInitialCapacitySingleMap(
                whiteBoxHashMapTestSuite.getCreateNewMapWithCapacity().apply(initialCapacity),
                initialCapacity
        );
        capacityTestInitialCapacitySingleMap(
                whiteBoxHashMapTestSuite.getCreateNewMapWithCapacityAndFactor().apply(initialCapacity, 0.75F),
                initialCapacity
        );
    }

    public void capacityTestInitialCapacitySingleMap(
            T map,
            int initialCapacity
    ) {
        assertEquals(-1, (int) whiteBoxHashMapTestSuite.getGetArrayLength().apply(map));
        map.put(1, 1);
        assertEquals(WhiteBoxHashMapTestUtil.tableSizeFor(initialCapacity), (int) whiteBoxHashMapTestSuite.getGetArrayLength().apply(map));
    }

}
