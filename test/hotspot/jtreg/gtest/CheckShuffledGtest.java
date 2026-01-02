/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Runs all GTest with a random shuffle
 * @comment Used to identify GTests which have an order dependencies.
 * @key randomness
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.flagless
 * @run main/native/timeout=480 CheckShuffledGtest
 */

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.StringJoiner;

import jdk.test.lib.Utils;

public class CheckShuffledGtest {
    static record TestFilter(String name, boolean enabled) {}

    static TestFilter TF(String name, boolean enabled) {
        return new TestFilter(name, enabled);
    }

    static TestFilter TF(String name) {
        return TF(name, true);
    }

    // List of tests which have dependencies, these should have bugs associated
    // with them, and should be removed once they are resolved.
    static final TestFilter[] TEST_FILTERS = {
        // JDK-8372242
        TF("LogTagSet.defaults__vm"),
    };

    public static Stream<String> getFilteredTests() {
        return Stream.concat(// All tests which have known issues when ran alone
                             // These will have issues if shuffled before their
                             // dependency.
                             CheckGtestDependencies.getFilteredTests(),
                             // gtests which have an order dependency
                             Arrays.stream(TEST_FILTERS)
                                   .filter(testFilter -> testFilter.enabled)
                                   .map(testFilter -> testFilter.name));
    }

    static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) throws Throwable {
        // Create a negative filter of all test with known issues.
        var filterCollector = Collector.of(
            () -> new StringJoiner(":", "--gtest_filter=-", "").setEmptyValue(""),
            StringJoiner::add,
            StringJoiner::merge,
            StringJoiner::toString);
        var filter = getFilteredTests().collect(filterCollector);

        // Generate a random gtest seed
        var gtestSeedLimit = 100_000;
        var seed = RANDOM.nextInt(gtestSeedLimit);

        // Delegate running the gtest to the GTestWrapper using this filter
        // and a random seed with gtest shuffling.
        GTestWrapper.main(Stream.concat(Arrays.stream(args),
                                        Stream.of(
                                            filter,
                                            "--gtest_shuffle",
                                            "--gtest_random_seed=" + seed))
                                .filter(arg -> !arg.isEmpty())
                                .toArray(String[]::new));
    }
}
