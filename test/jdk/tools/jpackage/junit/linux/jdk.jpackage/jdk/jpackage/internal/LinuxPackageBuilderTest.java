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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.CommandOutputControl.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class LinuxPackageBuilderTest {

    @ParameterizedTest
    @EnumSource(ValidationResult.class)
    void test_menuGroupNameValidation(ValidationResult validationResult, @TempDir Path workDir) {

        var counter = new AtomicInteger();

        var builder = dummy().menuGroupName("bar").probeMenuGroupNameFile(workDir.resolve("probe.desktop")).desktopEntryFileValidator(path -> {
            assertEquals(workDir.resolve("probe.desktop"), path);
            assertTrue(Files.isRegularFile(path));

            counter.incrementAndGet();

            return switch (validationResult) {
                case SUCCESS -> Result.build().exitCode(0).create();
                case EXIT_1 -> Result.build().exitCode(1).create();
                case EXIT_2 -> Result.build().exitCode(2).create();
                case UNAVAILABLE -> Result.build().create();
            };
        });

        switch (validationResult) {
            case SUCCESS, UNAVAILABLE -> assertDoesNotThrow(builder::create);
            case EXIT_1, EXIT_2 -> {
                var ex = assertThrowsExactly(ConfigException.class, builder::create);

                assertEquals(I18N.format("error.parameter-invalid-value", "bar", "--linux-menu-group"), ex.getMessage());
                assertEquals(I18N.format("error.invalid-desktop-category.advice"), ex.getAdvice());

                assertEquals(null, ex.getCause());
            }
        }

        assertTrue(Files.isRegularFile(workDir.resolve("probe.desktop")));
        assertEquals(1, counter.get());
    }

    @Test
    void test_menuGroupNameValidation_with_probe_file_is_directory(@TempDir Path workDir) throws IOException {

        Files.createDirectory(workDir.resolve("probe.desktop"));

        var builder = dummy().menuGroupName("bar").probeMenuGroupNameFile(workDir.resolve("probe.desktop")).desktopEntryFileValidator(_ -> {
            throw new AssertionError();
        });

        assertThrowsExactly(UncheckedIOException.class, builder::create);

        assertTrue(Files.isDirectory(workDir.resolve("probe.desktop")));
    }

    @ParameterizedTest
    @MethodSource
    void test_menuGroupNameValidation_skip(
            boolean setMenuGroupName,
            boolean setProbeMenuGroupNameFile,
            boolean setDesktopEntryFileValidator,
            @TempDir Path workDir) throws IOException {

        Files.createDirectory(workDir.resolve("probe.desktop"));

        var builder = dummy();

        if (setMenuGroupName) {
            builder.menuGroupName("bar");
        }

        if (setProbeMenuGroupNameFile) {
            builder.probeMenuGroupNameFile(workDir.resolve("probe.desktop"));
        }

        if (setDesktopEntryFileValidator) {
            builder.desktopEntryFileValidator(_ -> {
                throw new AssertionError();
            });
        }

        assertDoesNotThrow(builder::create);

        assertTrue(Files.isDirectory(workDir.resolve("probe.desktop")));
    }

    static Collection<Arguments> test_menuGroupNameValidation_skip() {

        var testCases = new ArrayList<Arguments>();

        for (var setMenuGroupName : List.of(true, false)) {
            for (var setProbeMenuGroupNameFile : List.of(true, false)) {
                for (var setDesktopEntryFileValidator : List.of(true, false)) {
                    if (Stream.of(setMenuGroupName, setProbeMenuGroupNameFile, setDesktopEntryFileValidator).allMatch(Boolean.TRUE::equals)) {
                        continue;
                    }

                    testCases.add(Arguments.of(setMenuGroupName, setProbeMenuGroupNameFile, setDesktopEntryFileValidator));
                }
            }
        }

        return testCases;
    }

    enum ValidationResult {
        SUCCESS,
        EXIT_1,
        EXIT_2,
        UNAVAILABLE,
        ;
    }

    private static LinuxPackageBuilder dummy() {
        var app = new Application.Stub(
                "foo",
                "Foo App",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                ApplicationLayout.build().setAll("").create(),
                Optional.empty(),
                List.of(),
                Map.of());

        return new LinuxPackageBuilder(new PackageBuilder(app, StandardPackageType.LINUX_DEB)).arch(new LinuxPackageArch("acme"));
    }
}
