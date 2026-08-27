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


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedExitCodeException;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LinuxPackageBuilderTest {

    @ParameterizedTest
    @EnumSource(value = CommandMockExit.class)
    void test_menuGroupNameValidation(CommandMockExit exit, @TempDir Path workDir) {

        var desktop_file_validate = CommandActionSpecs.build().exit(exit).toCommandMockBuilder().name("desktop-file-validate-mock").create();

        Globals.main(() -> {
            Globals.instance().executorFactory(() -> {
                return new Executor().mapper(executor -> {
                    return executor.copy().mapper(null).toolProvider(desktop_file_validate);
                });
            });

            var builder = dummy().menuGroupName("bar").probeMenuGroupNameFile(workDir.resolve("probe.desktop"));

            switch (exit) {
                case THROW_MOCK_IO_EXCEPTION, SUCCEED -> assertDoesNotThrow(builder::create);
                case EXIT_1 -> {
                    var ex = assertThrowsExactly(ConfigException.class, builder::create);

                    assertEquals(I18N.format("error.parameter-invalid-value", "bar", "--linux-menu-group"), ex.getMessage()); 
                    assertEquals(I18N.format("error.invalid-desktop-category.advice"), ex.getAdvice());

                    assertEquals(UnexpectedExitCodeException.class, ex.getCause().getClass());
                }
            }

            assertTrue(Files.isRegularFile(workDir.resolve("probe.desktop")));

            return 0;
        });
    }

    private static LinuxPackageBuilder dummy() {
        var app = new Application.Stub(
                "foo",
                "Foo App",
                null,
                null,
                null,
                List.of(),
                ApplicationLayout.build().setAll("").create(),
                Optional.empty(),
                List.of(),
                Map.of());

        return new LinuxPackageBuilder(new PackageBuilder(app, StandardPackageType.LINUX_DEB)).arch(new LinuxPackageArch("acme"));
    }
}
