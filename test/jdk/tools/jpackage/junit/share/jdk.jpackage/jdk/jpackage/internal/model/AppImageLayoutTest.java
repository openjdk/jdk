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

package jdk.jpackage.internal.model;

import static jdk.jpackage.internal.model.AppImageLayout.toPathGroup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import jdk.jpackage.internal.util.PathGroup;
import org.junit.jupiter.api.Test;


public class AppImageLayoutTest {

    @Test
    public void testStub() {
        final var root = Path.of("root");
        final var runtime = Path.of("runtime");
        final var layout = new AppImageLayout.Stub(root, runtime);

        assertEquals(root, layout.rootDirectory());
        assertEquals(runtime, layout.runtimeDirectory());
    }

    @Test
    public void testPathGroup() {
        final var layout = new AppImageLayout.Stub(Path.of("root"), Path.of("runtime"));

        final var pathGroup = toPathGroup(layout);

        assertEquals(Set.of("runtimeDirectory"), pathGroup.keys());
        assertEquals(List.of(layout.runtimeDirectory()), pathGroup.paths());
    }

    @Test
    public void testResolveAt() {
        testResolveAt(new AppImageLayout.Stub(Path.of("foo")));
    }

    @Test
    public void testResolveAtRepeat() {
        testResolveAtRepeat(new AppImageLayout.Stub(Path.of("foo")));
    }

    @Test
    public void testUnresolve() {
        testUnresolve(new AppImageLayout.Stub(Path.of("runtime")));
    }

    @Test
    public void testEmptyRootDirectory() {
        testEmptyRootDirectory(new AppImageLayout.Stub(Path.of("rt")));
    }

    public static void testResolveAt(AppImageLayout testee) {

        var dir = Path.of("foo/bar");

        assertLayout(testee.resolveAt(dir), true, testee, dir);
    }

    public static void testResolveAtRepeat(AppImageLayout testee) {

        var resolvedLayout = testee.resolveAt(Path.of("b/c")).resolveAt(Path.of("a"));

        assertLayout(resolvedLayout, true, testee, Path.of("a/b/c"));
    }

    public static void testUnresolve(AppImageLayout testee) {
        if (testee.isResolved()) {
            throw new IllegalArgumentException();
        }

        var resolvedLayout = testee.resolveAt(Path.of("foo/bar"));
        var layout = resolvedLayout.unresolve();

        assertLayout(layout, false, testee, Path.of(""));

        resolvedLayout = testee.resolveAt(Path.of("").toAbsolutePath());
        layout = resolvedLayout.unresolve();

        assertLayout(layout, false, testee, Path.of(""));

        assertSame(testee, testee.unresolve());
    }

    public static void testEmptyRootDirectory(AppImageLayout testee) {
        if (testee.isResolved()) {
            throw new IllegalArgumentException();
        }

        assertEmptyRootDirectory(testee);

        final var resolved = testee.resolveAt(Path.of("t"));

        assertEmptyRootDirectory(resolved);
    }

    private static void assertEmptyRootDirectory(AppImageLayout testee) {
        if (testee.isResolved()) {
            var newLayout = testee.resetRootDirectory();
            assertLayout(newLayout, false, Path.of(""), toPathGroup(testee));
        } else {
            assertSame(testee, testee.resetRootDirectory());
        }
    }

    private static void assertLayout(AppImageLayout actual, boolean expectedResolved,
            AppImageLayout base, Path baseResolveAt) {
        assertLayout(actual, expectedResolved, baseResolveAt.resolve(base.rootDirectory()),
                toPathGroup(base).resolveAt(baseResolveAt));
    }

    private static void assertLayout(AppImageLayout actual, boolean expectedResolved,
            Path expectedRootDir, PathGroup expectedPaths) {
        assertEquals(expectedResolved, actual.isResolved());
        assertEquals(expectedRootDir, actual.rootDirectory());
        assertEquals(expectedPaths, toPathGroup(actual));
    }

}
