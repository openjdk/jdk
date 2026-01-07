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

import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class LinuxPackageArchTest {

    @ParameterizedTest
    @MethodSource
    public void test(Runnable test) {
        test.run();
    }

    private static List<Runnable> test() {
        var data = new ArrayList<Runnable>();

        // "foo" stdout interleaved with "bar" stderr
        var fooArch = CommandActionSpecs.build()
                .printToStdout("f").printToStderr("b")
                .printToStdout("o").printToStderr("a")
                .printToStdout("o").printToStderr("r");

        for (var exit : CommandMockExit.values()) {
            var dpkg = fooArch.copy().printToStdout("-deb").exit(exit).create();

            data.add(new DebTestSpec(dpkg, Optional.of("foo-deb").filter(_ -> {
                return exit.succeed();
            })));
        }

        for (var rpmbuildExit : CommandMockExit.values()) {
            var rpmbuild = fooArch.copy().printToStdout("-rpmbuild").exit(rpmbuildExit).create();
            for (var rpmExit : CommandMockExit.values()) {
                var rpm = fooArch.copy().printToStdout("-rpm").exit(rpmExit).create();
                Optional<String> expect;
                if (rpmbuildExit.succeed()) {
                    expect = Optional.of("foo-rpmbuild");
                    rpm = CommandActionSpecs.UNREACHABLE;
                } else {
                    if (rpmExit.succeed()) {
                        expect = Optional.of("foo-rpm");
                    } else {
                        expect = Optional.empty();
                    }
                }

                data.add(new RpmTestSpec(rpmbuild, rpm, expect));
            }
        }

        return data;
    }

    record RpmTestSpec(CommandActionSpecs rpmbuild, CommandActionSpecs rpm, Optional<String> expect) implements Runnable {

        RpmTestSpec {
            Objects.requireNonNull(rpm);
            Objects.requireNonNull(rpmbuild);
            Objects.requireNonNull(expect);
        }

        @Override
        public void run() {

            // Create an executor factory that will:
            //  - Substitute the "rpm" command with `rpm` mock.
            //  - Substitute the "rpmbuild" command with `rpmbuild` mock.
            //  - Throw if a command with the name other than "rpm" and "rpmbuild" is requested for execution.

            var script = Script.build()
                    // LinuxPackageArch must run the "rpmbuild" command first. Put its mapping at the first position.
                    .map(new CommandMockSpec("rpmbuild", rpmbuild))
                    // LinuxPackageArch may optionally run the "rpm" command. Put its mapping after the "rpmbuild" command mapping.
                    .map(new CommandMockSpec("rpm", rpm))
                    // Create a sequential script: after every Script#map() call, the script will advance the current mapping.
                    // This means each mapping in the script will be considered only once.
                    // If "rpm" and "rpmbuild" commands are executed in reverse order, the second Script#map() will throw.
                    .createSequence();

            test(expect, LINUX_RPM, script);
        }
    }

    record DebTestSpec(CommandActionSpecs dpkg, Optional<String> expect) implements Runnable {

        DebTestSpec {
            Objects.requireNonNull(dpkg);
            Objects.requireNonNull(expect);
        }

        @Override
        public void run() {
            var script = Script.build().map(new CommandMockSpec("dpkg", dpkg)).createSequence();

            test(expect, LINUX_DEB, script);
        }
    }

    private static void test(Optional<String> expectedArch, StandardPackageType pkgType, Script script) {

        Globals.main(() -> {

            MockUtils.buildJPackage().script(script).applyToGlobals();

            Result<LinuxPackageArch> arch = LinuxPackageArch.create(pkgType);

            assertEquals(arch.hasValue(), expectedArch.isPresent());
            expectedArch.ifPresent(v -> {
                assertEquals(v, arch.orElseThrow().value());
            });

            assertEquals(List.of(), script.incompleteMocks());

            return 0;
        });
    }
}
