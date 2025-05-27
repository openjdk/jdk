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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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

        final var pathGroup = AppImageLayout.toPathGroup(layout);

        assertEquals(Set.of("runtimeDirectory"), pathGroup.keys());
        assertEquals(List.of(layout.runtimeDirectory()), pathGroup.paths());
    }

    @Test
    public void testResolveAt() {
        final var dir = Path.of("foo/bar");

        final var layout = new AppImageLayout.Stub(Path.of(""), Path.of("runtime"));

        final var resolvedLayout = layout.resolveAt(dir);

        assertNotSame(layout, resolvedLayout);

        assertEquals(dir.resolve(layout.rootDirectory()), resolvedLayout.rootDirectory());
        assertEquals(dir.resolve(layout.runtimeDirectory()), resolvedLayout.runtimeDirectory());
    }
}
