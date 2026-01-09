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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LinuxSystemEnvironmentTest {

    @ParameterizedTest
    @MethodSource
    public void test_detectNativePackageType(DetectNativePackageTypeTestSpec test) {
        test.run();
    }

    private static List<DetectNativePackageTypeTestSpec> test_detectNativePackageType() {
        var data = new ArrayList<DetectNativePackageTypeTestSpec>();
        for (var rpmExit : CommandMockExit.values()) {
            for (var debExit : CommandMockExit.values()) {
                CommandActionSpecs deb = CommandActionSpecs.build().exit(debExit).create();
                CommandActionSpecs rpm;
                Optional<StandardPackageType> expected;
                if (debExit.succeed()) {
                    expected = Optional.of(StandardPackageType.LINUX_DEB);
                    rpm = CommandActionSpecs.UNREACHABLE;
                } else {
                    rpm = CommandActionSpecs.build().exit(rpmExit).create();
                    if (rpmExit.succeed()) {
                        expected = Optional.of(StandardPackageType.LINUX_RPM);
                    } else {
                        expected = Optional.empty();
                    }
                }
                data.add(new DetectNativePackageTypeTestSpec(expected, rpm, deb));
            }
        }
        return data;
    }

    record DetectNativePackageTypeTestSpec(Optional<StandardPackageType> expect, CommandActionSpecs rpm, CommandActionSpecs deb) {

        DetectNativePackageTypeTestSpec {
            Objects.requireNonNull(expect);
            Objects.requireNonNull(rpm);
            Objects.requireNonNull(deb);
        }

        void run() {

            var script = Script.build()
                    .map(new CommandMockSpec("rpm", rpm))
                    .map(new CommandMockSpec("dpkg", deb))
                    .createLoop();

            Globals.main(() -> {

                MockUtils.buildJPackage().script(script).applyToGlobals();

                var actual = LinuxSystemEnvironment.detectNativePackageType();

                assertEquals(expect, actual);

                assertEquals(List.of(), script.incompleteMocks());

                return 0;
            });
        }
    }
}
