/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.model.AppImageLayout.toPathGroup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.RuntimeLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


public class BuildEnvTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "image"})
    public void testUnresolvedAppImageLayout(Path appImageDir) {
        final var rootDir = Path.of("");

        final var env = BuildEnv.create(rootDir, Optional.empty(), true,
                BuildEnvTest.class, RuntimeLayout.DEFAULT.resolveAt(appImageDir).resetRootDirectory());

        assertEquals(env.appImageDir(), env.appImageLayout().rootDirectory());

        assertEquals(Path.of(""), env.appImageDir());
        assertEquals(toPathGroup(RuntimeLayout.DEFAULT.resolveAt(appImageDir)), toPathGroup(env.appImageLayout()));
        assertEquals(rootDir, env.buildRoot());
        assertEquals(rootDir.resolve("config"), env.configDir());
        assertEquals(Optional.empty(), env.resourceDir());
        assertTrue(env.verbose());
    }

    @Test
    public void testResolvedAppImageLayout() {
        final var rootDir = Path.of("/oof");
        final var appImageDir = Path.of("/foo/bar");

        final var layout = RuntimeLayout.DEFAULT.resolveAt(appImageDir);
        final var env = BuildEnv.create(rootDir, Optional.empty(), true, BuildEnvTest.class, layout);

        assertSame(layout, env.appImageLayout());
        assertEquals(env.appImageDir(), env.appImageLayout().rootDirectory());

        assertEquals(Path.of("/foo/bar"), env.appImageDir());
        assertEquals(toPathGroup(RuntimeLayout.DEFAULT.resolveAt(appImageDir)), toPathGroup(env.appImageLayout()));
        assertEquals(rootDir, env.buildRoot());
        assertEquals(rootDir.resolve("config"), env.configDir());
        assertEquals(Optional.empty(), env.resourceDir());
        assertTrue(env.verbose());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/foo/bar"})
    public void test_withAppImageDir(Path appImageDir) {
        final var rootDir = Path.of("/oof");

        final var layout = RuntimeLayout.DEFAULT;
        final var env = BuildEnv.withAppImageDir(BuildEnv.create(rootDir,
                Optional.empty(), false, BuildEnvTest.class, layout), appImageDir);

        assertNotSame(layout, env.appImageLayout());
        assertEquals(env.appImageDir(), env.appImageLayout().rootDirectory());

        assertEquals(appImageDir, env.appImageDir());
        assertEquals(toPathGroup(RuntimeLayout.DEFAULT.resolveAt(appImageDir)), toPathGroup(env.appImageLayout()));
        assertEquals(rootDir, env.buildRoot());
        assertEquals(rootDir.resolve("config"), env.configDir());
        assertEquals(Optional.empty(), env.resourceDir());
        assertFalse(env.verbose());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_withAppImageLayout(boolean resolved) {
        final var rootDir = Path.of("/oof");

        final var appImageDir = Path.of("/foo/bar");

        final AppImageLayout layout;
        if (resolved) {
            layout = RuntimeLayout.DEFAULT.resolveAt(appImageDir);
        } else {
            layout = RuntimeLayout.DEFAULT.resolveAt(appImageDir).resetRootDirectory();
        }

        final var env = BuildEnv.withAppImageLayout(BuildEnv.create(rootDir,
                Optional.empty(), false, BuildEnvTest.class, RuntimeLayout.DEFAULT), layout);

        assertSame(layout, env.appImageLayout());
        assertEquals(env.appImageDir(), env.appImageLayout().rootDirectory());

        assertEquals(toPathGroup(RuntimeLayout.DEFAULT.resolveAt(appImageDir)), toPathGroup(env.appImageLayout()));
        assertEquals(rootDir, env.buildRoot());
        assertEquals(rootDir.resolve("config"), env.configDir());
        assertEquals(Optional.empty(), env.resourceDir());
        assertFalse(env.verbose());
    }

    @Test
    public void test_asApplicationLayout() {
        final var rootDir = Path.of("r");

        assertTrue(BuildEnv.create(rootDir, Optional.empty(), false,
                BuildEnvTest.class, RuntimeLayout.DEFAULT).asApplicationLayout().isEmpty());

        var layout = ApplicationLayout.build().setAll("foo").create();
        assertSame(layout, BuildEnv.create(rootDir, Optional.empty(), false,
                BuildEnvTest.class, layout).asApplicationLayout().orElseThrow());
    }
}
