/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MacDmgSystemEnvironmentTest {

    @ParameterizedTest
    @MethodSource
    void test_findSetFileUtility(FindSetFileUtilityTestSpec test) {
        test.run();
    }

    private static List<FindSetFileUtilityTestSpec> test_findSetFileUtility() {
        var data = new ArrayList<FindSetFileUtilityTestSpec>();

        var succeed = CommandActionSpecs.build().exit().create();

        for (var failureCause : List.of(CommandMockExit.EXIT_1, CommandMockExit.THROW_MOCK_IO_EXCEPTION)) {

            var fail = CommandActionSpecs.build().exit(failureCause).create();

            for (var i = 0; i != MacDmgSystemEnvironment.SETFILE_KNOWN_PATHS.size(); i++) {

                var expected = MacDmgSystemEnvironment.SETFILE_KNOWN_PATHS.get(i);

                var mocks = new ArrayList<CommandMockSpec>();

                MacDmgSystemEnvironment.SETFILE_KNOWN_PATHS.subList(0, i).stream().map(failureSetFilePath -> {
                    return new CommandMockSpec(failureSetFilePath, fail);
                }).forEach(mocks::add);

                mocks.add(new CommandMockSpec(expected, succeed));

                data.add(new FindSetFileUtilityTestSpec(Optional.of(expected), mocks));
            }

            var lastMocks = data.getLast().mockSpecs();
            var lastSucceedMock = lastMocks.getLast();
            var lastFailMock = new CommandMockSpec(lastSucceedMock.name(), lastSucceedMock.mockName(), fail);

            var mocks = new ArrayList<>(lastMocks);
            mocks.set(mocks.size() - 1, lastFailMock);

            for (var xcrunOutout : List.<Map.Entry<Optional<String>, Boolean>>of(
                    // Use the path to the command of the current process
                    // as an output mock for the /usr/bin/xcrun command.
                    // MacDmgSystemEnvironment.findSetFileUtility() reads the command output
                    // and checks whether it is an executable file,
                    // so the hardcoded value is not an option for the output mock.
                    Map.entry(Optional.of(ProcessHandle.current().info().command().orElseThrow()), true),
                    // "/usr/bin/xcrun" outputs a path to non-executable file.
                    Map.entry(Optional.of("/dev/null"), false),
                    // "/usr/bin/xcrun" outputs '\0' making subsequent Path.of("\0") fail.
                    Map.entry(Optional.of("\0"), false),
                    // "/usr/bin/xcrun" doesn't output anything.
                    Map.entry(Optional.empty(), false)
            )) {


                mocks.add(new CommandMockSpec("/usr/bin/xcrun", CommandActionSpecs.build().mutate(builder -> {
                    xcrunOutout.getKey().ifPresent(builder::stdout);
                }).exit(CommandMockExit.SUCCEED).create()));

                Optional<String> expected;
                if (xcrunOutout.getValue()) {
                    expected = xcrunOutout.getKey();
                } else {
                    expected = Optional.empty();
                }

                data.add(new FindSetFileUtilityTestSpec(expected.map(Path::of), List.copyOf(mocks)));

                mocks.removeLast();
            }

            // The last test case: "/usr/bin/xcrun" fails
            mocks.add(new CommandMockSpec("/usr/bin/xcrun", fail));
            data.add(new FindSetFileUtilityTestSpec(Optional.empty(), mocks));
        }

        return data;
    }

    record FindSetFileUtilityTestSpec(Optional<Path> expected, List<CommandMockSpec> mockSpecs) {

        FindSetFileUtilityTestSpec {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(mockSpecs);
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            expected.ifPresent(v -> {
                tokens.add(String.format("expect=%s", v));
            });
            tokens.add(mockSpecs.toString());
            return tokens.stream().collect(Collectors.joining(", "));
        }

        void run() {

            var script = Script.build().mutate(builder -> {
                mockSpecs.forEach(builder::map);
            }).createSequence();

            Globals.main(() -> {
                MockUtils.buildJPackage().script(script).applyToGlobals();

                var actual = MacDmgSystemEnvironment.findSetFileUtility();

                assertEquals(expected, actual);
                assertEquals(List.of(), script.incompleteMocks());

                return 0;
            });
        }
    }
}
