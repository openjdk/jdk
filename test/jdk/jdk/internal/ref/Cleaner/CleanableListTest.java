/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test
 * @bug 8343704
 * @key randomness
 * @library /test/lib
 * @compile/module=java.base jdk/internal/ref/CleanableListTestHelper.java jdk/internal/ref/TestCleanable.java
 * @modules java.base/jdk.internal.ref
 * @run testng/othervm CleanableListTest
 */

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import jdk.internal.ref.CleanableListTestHelper;
import jdk.internal.ref.TestCleanable;
import jdk.test.lib.RandomFactory;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.Ignore;

public class CleanableListTest {

    static final int SINGLE_NODE_CAPACITY = CleanableListTestHelper.NODE_CAPACITY;
    static final int MULTI_NODE_CAPACITY = CleanableListTestHelper.NODE_CAPACITY * 4;

    static final Random RND = RandomFactory.getRandom();
    static final int RANDOM_ITERATIONS = 10_000_000;

    @Test
    public void testSingle() {
        CleanableListTestHelper list = new CleanableListTestHelper();
        Assert.assertTrue(list.isEmpty());
        TestCleanable tc = list.newCleanable();
        Assert.assertFalse(list.isEmpty());
        Assert.assertTrue(list.remove(tc));
        Assert.assertTrue(list.isEmpty());
        Assert.assertFalse(list.remove(tc));
    }

    @Test
    public void testSequential_Single() {
        doSequential(SINGLE_NODE_CAPACITY);
    }

    @Test
    public void testSequential_Multi() {
        doSequential(MULTI_NODE_CAPACITY);
    }

    private void doSequential(int size) {
        CleanableListTestHelper list = new CleanableListTestHelper();
        Assert.assertTrue(list.isEmpty());

        List<TestCleanable> tcs = new ArrayList<>();
        for (int c = 0; c < size; c++) {
            tcs.add(list.newCleanable());
        }
        Assert.assertFalse(list.isEmpty());

        for (TestCleanable tc : tcs) {
            Assert.assertTrue(list.remove(tc));
        }
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void testRandom_Single() {
        doRandom(SINGLE_NODE_CAPACITY);
    }

    @Test
    public void testRandom_Multi() {
        doRandom(MULTI_NODE_CAPACITY);
    }

    private void doRandom(int size) {
        CleanableListTestHelper list = new CleanableListTestHelper();
        Assert.assertTrue(list.isEmpty());

        BitSet bs = new BitSet(size);

        List<TestCleanable> tcs = new ArrayList<>();
        for (int c = 0; c < size; c++) {
            tcs.add(list.newCleanable());
            bs.set(c, true);
        }
        Assert.assertFalse(list.isEmpty());

        for (int t = 0; t < RANDOM_ITERATIONS; t++) {
            int idx = RND.nextInt(size);
            TestCleanable tc = tcs.get(idx);
            if (bs.get(idx)) {
                Assert.assertTrue(list.remove(tc));
                bs.set(idx, false);
            } else {
                Assert.assertFalse(list.remove(tc));
                list.insert(tc);
                bs.set(idx, true);
            }
        }

        for (int c = 0; c < size; c++) {
            if (bs.get(c)) {
                TestCleanable tc = tcs.get(c);
                Assert.assertTrue(list.remove(tc));
            }
        }
    }

}
