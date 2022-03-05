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
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;

/*
 * @test
 * @bug 8210280
 * @summary White box tests for HashMap internals around table resize
 *
 * @run junit/othervm/timeout=1000
 *      --add-opens java.base/java.lang=ALL-UNNAMED
 *      --add-opens java.base/java.util=ALL-UNNAMED
 *      WhiteBoxHashMapDefaultConstructorCapacityTest
 */
@RunWith(Parameterized.class)
public class WhiteBoxHashMapDefaultConstructorCapacityTest<T extends Map> {

    @Parameterized.Parameters
    public static List<Object[]> testFunctionsList() {
        return List.of(
                new Object[]{WhiteBoxHashMapTestUtil.HASH_MAP_TEST_SUITE},
                new Object[]{WhiteBoxHashMapTestUtil.LINKED_HASH_MAP_TEST_SUITE}
        );
    }

    private final WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite;

    public WhiteBoxHashMapDefaultConstructorCapacityTest(
            WhiteBoxHashMapTestUtil.WhiteBoxHashMapTestSuite<T> whiteBoxHashMapTestSuite
    ) {
        this.whiteBoxHashMapTestSuite = whiteBoxHashMapTestSuite;
    }

    @Test
    public void capacityTestDefaultConstructor() {
        T map = whiteBoxHashMapTestSuite.getCreateNewMap().get();
        assertEquals(-1, (int) whiteBoxHashMapTestSuite.getGetArrayLength().apply(map));

        map.put(1, 1);
        assertEquals(16, (int) whiteBoxHashMapTestSuite.getGetArrayLength().apply(map)); // default initial capacity

        map.putAll(IntStream.range(0, 64).boxed().collect(toMap(i -> i, i -> i)));
        assertEquals(128, (int) whiteBoxHashMapTestSuite.getGetArrayLength().apply(map));
    }

}
