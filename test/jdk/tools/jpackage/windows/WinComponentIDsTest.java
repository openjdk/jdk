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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Comm;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper;
import jdk.jpackage.test.Annotations.ParameterSupplier;

/**
 * Test that MSI component IDs for the same path differ between packages with
 * different upgrade codes, but match between packages with the same upgrade code.
 */

/*
 * @test
 * @bug 8394606
 * @summary Test case for JDK-8394606
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build WinComponentIDsTest
 * @requires (os.family == "windows")
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinComponentIDsTest
 */
public class WinComponentIDsTest {

    @Test
    @ParameterSupplier
    public void test(Optional<UUID> upgradeCodeA, Optional<UUID> upgradeCodeB) {
        new PackageTest()
        .forTypes(PackageType.WIN_MSI)
        .configureHelloApp()
        .addInitializer(JPackageCommand::setFakeRuntime)
        .mutate(test -> {
            upgradeCodeA.ifPresent(v -> {
                test.addInitializer(cmd -> {
                    cmd.setArgumentValue("--win-upgrade-uuid", v.toString());
                });
            });
        })
        .addBundleVerifier(cmdA -> {
            var cmdB = new JPackageCommand();
            cmdB.clearArguments().addArguments(cmdA.getAllArguments());
            upgradeCodeB.ifPresentOrElse(v -> {
                cmdB.setArgumentValue("--win-upgrade-uuid", v.toString());
            }, () -> {
                cmdB.removeArgumentWithValue("--win-upgrade-uuid");
            });

            cmdB.setArgumentValue("--dest", TKit.createTempDirectory("output-b")).execute();

            final var comm = Comm.compare(msiComponentIds(cmdA), msiComponentIds(cmdB));
            if (upgradeCodeA.equals(upgradeCodeB)) {
                TKit.assertTrue(comm.uniqueEmpty(), "Check component IDs are the same in both MSI-s");
            } else {
                TKit.assertEquals(0, comm.common().size(), "Check component IDs are unique accross MSI-s");
            }

        }).run(Action.CREATE);
    }

    public static Collection<Object[]> test() {
        return Stream.of(
                List.of(uuid(null), uuid(null)),
                List.of(uuid("9c8d7332-41a6-42d9-8e3d-56f41e8d7568"), uuid("9c8d7332-41a6-42d9-8e3d-56f41e8d7568")),
                List.of(uuid("9c8d7332-41a6-42d9-8e3d-56f41e8d7568"), uuid(null)),
                List.of(uuid("9c8d7332-41a6-42d9-8e3d-56f41e8d7568"), uuid("f37c8b4d-2c8b-4fb0-9f45-0f9b6f0c7b2e"))
        ).map(List::toArray).toList();
    }

    private static Optional<UUID> uuid(String v) {
        return Optional.ofNullable(v).map(UUID::fromString);
    }

    private static Set<UUID> msiComponentIds(JPackageCommand cmd) {
        // Use Collectors.toMap() to validate the uniqueness of component IDs.
        return WindowsHelper.getMsiComponentIds(cmd).stream().collect(Collectors.toMap(x -> x, x -> x)).keySet();
    }
}
