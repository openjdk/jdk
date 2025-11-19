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
 * @summary Runs all not VM GTests together
 * @comment Used to identify not VM GTests which may have a VM dependency.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.flagless
 * @run main/native AllNotVMGtest
 */

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.Platform;

public class AllNotVMGtest {
    static record TestFilter(String name, boolean enabled) {}

    static TestFilter TF(String name, boolean enabled) {
        return new TestFilter(name, enabled);
    }

    static final TestFilter TF(String name) {
        return TF(name, true);
    }

    // List of tests which have dependencies, these should have bugs associated
    // with them, and should be removed once they are resolved.
    static TestFilter[] TEST_FILTERS = {
        TF("globalDefinitions.format_specifiers"),
        TF("LogOutputList.is_level_multiple_outputs"),
        TF("LogOutputList.is_level_single_output"),
        TF("LogOutputList.level_for"),
        TF("os_linux.addr_to_function_valid"),
        TF("Semaphore.trywait", Platform.isOSX()),
    };

    public static Stream<String> getFilteredTests() {
        return Arrays.stream(TEST_FILTERS)
                     .filter(testFilter -> testFilter.enabled)
                     .map(testFilter -> testFilter.name);
    }

    public static void main(String[] args) throws Throwable {
        // Create a negative filter which matches all VM gtests
        // and append all test with known issues.
        var filter = Stream.concat(Stream.of("--gtest_filter=-*_vm:*_vm_assert"),
                                   getFilteredTests())
                           .collect(Collectors.joining(":"));

        // Delegate running the gtest to the GTestWrapper using this filter.
        GTestWrapper.main(Stream.concat(Arrays.stream(args),
                                        Stream.of(filter))
                                .toArray(String[]::new));
    }
}
