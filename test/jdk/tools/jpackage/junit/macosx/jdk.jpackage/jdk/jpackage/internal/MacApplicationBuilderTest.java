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

import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MacApplicationBuilderTest {

    @ParameterizedTest
    @CsvSource({
        "NAME,!fo#o,foo",
        "NAME,bar,bar",
        "MAIN_LAUNCHER_CLASSNAME,foo.b$ar.Hello,foo.bar",
        "MAIN_LAUNCHER_CLASSNAME,Hello$2,Hello2",
        "NAME,!#,",
    })
    void testDerivedBundleIdentifier(ApplicationBuilderProperty type, String value, String expectedBundleIdentifier) {

        var builder = buildApplication();
        var macBuilder = new MacApplicationBuilder(builder);

        type.applyTo(value, builder, macBuilder);

        if (expectedBundleIdentifier != null) {
            assertEquals(expectedBundleIdentifier, macBuilder.create().bundleIdentifier());
        } else {
            var ex = assertThrowsExactly(ConfigException.class, macBuilder::create);
            assertEquals(I18N.format("error.invalid-derived-bundle-identifier"), ex.getMessage());
            assertEquals(I18N.format("error.invalid-derived-bundle-identifier.advice"), ex.getAdvice());
        }
    }

    private static ApplicationBuilder buildApplication() {
        return new ApplicationBuilder().appImageLayout(APPLICATION_LAYOUT);
    }

    enum ApplicationBuilderProperty {
        NAME {
            void applyTo(String value, ApplicationBuilder builder, MacApplicationBuilder macBuilder) {
                builder.name(Objects.requireNonNull(value));
            }
        },
        MAIN_LAUNCHER_CLASSNAME {
            void applyTo(String value, ApplicationBuilder builder, MacApplicationBuilder macBuilder) {
                var startupInfo = new LauncherStartupInfo.Stub(Objects.requireNonNull(value), List.of(), List.of(), List.of());
                var launcher = new Launcher.Stub(
                        startupInfo.simpleClassName(),
                        Optional.of(startupInfo),
                        List.of(),
                        false,
                        "",
                        Optional.empty(),
                        null,
                        Map.of());
                builder.launchers(new ApplicationLaunchers(launcher));
            }
        }
        ;

        abstract void applyTo(String value, ApplicationBuilder builder, MacApplicationBuilder macBuilder);
    }
}
