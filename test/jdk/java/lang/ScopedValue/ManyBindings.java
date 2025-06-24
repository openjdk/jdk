/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test ScopedValue with many bindings and rebindings
 * @library /test/lib
 * @key randomness
 * @run junit ManyBindings
 */

import java.lang.ScopedValue.Carrier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import jdk.test.lib.RandomFactory;
import jdk.test.lib.thread.VThreadRunner;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManyBindings {
    private static final Random RND = RandomFactory.getRandom();

    // number of scoped values to create
    private static final int SCOPED_VALUE_COUNT = 16;

    // recursive depth to test
    private static final int MAX_DEPTH = 24;

    /**
     * Stress test bindings on platform thread.
     */
    @Test
    void testPlatformThread() {
        test();
    }

    /**
     * Stress test bindings on virtual thread.
     */
    @Test
    void testVirtualThread() throws Exception {
        VThreadRunner.run(() -> test());
    }

    /**
     * Scoped value and its expected value (or null if not bound).
     */
    record KeyAndValue<T>(ScopedValue<T> key, T value) {
        KeyAndValue() {
            this(ScopedValue.newInstance(), null);
        }
    }

    /**
     * Stress test bindings on current thread.
     */
    private void test() {
        KeyAndValue<Integer>[] array = new KeyAndValue[SCOPED_VALUE_COUNT];
        for (int i = 0; i < array.length; i++) {
            array[i] = new KeyAndValue<>();
        }
        test(array, 1);
    }

    /**
     * Test that the scoped values in the array have the expected value, then
     * recursively call this method with some of the scoped values bound to a
     * new value.
     *
     * @param array the scoped values and their expected value
     * @param depth current recurive depth
     */
    private void test(KeyAndValue<Integer>[] array, int depth) {
        if (depth > MAX_DEPTH)
            return;

        // check that the scoped values have the expected values
        check(array);

        // try to pollute the cache
        lotsOfReads(array);

        // create a Carrier to bind/rebind some of the scoped values
        int len = array.length;
        Carrier carrier = null;

        KeyAndValue<Integer>[] newArray = Arrays.copyOf(array, len);
        int n = Math.max(1, RND.nextInt(len / 2));
        while (n > 0) {
            int index = RND.nextInt(len);
            ScopedValue<Integer> key = array[index].key;
            int newValue = RND.nextInt();
            if (carrier == null) {
                carrier = ScopedValue.where(key, newValue);
            } else {
                carrier = carrier.where(key, newValue);
            }
            newArray[index] = new KeyAndValue<>(key, newValue);
            n--;
        }

        // invoke recursively
        carrier.run(() -> {
            test(newArray, depth+1);
        });

        // check that the scoped values have the original values
        check(array);
    }

    /**
     * Check that the given scoped values have the expected value.
     */
    private void check(KeyAndValue<Integer>[] array) {
        for (int i = 0; i < array.length; i++) {
            ScopedValue<Integer> key = array[i].key;
            Integer value = array[i].value;
            if (value == null) {
                assertFalse(key.isBound());
            } else {
                assertEquals(value, key.get());
            }
        }
    }

    /**
     * Do lots of reads of the scoped values, to pollute the SV cache.
     */
    private void lotsOfReads(KeyAndValue<Integer>[] array) {
        for (int k = 0; k < 1000; k++) {
            int index = RND.nextInt(array.length);
            Integer value = array[index].value;
            if (value != null) {
                ScopedValue<Integer> key = array[index].key;
                assertEquals(value, key.get());
            }
        }
    }
}
