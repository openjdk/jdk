/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jaotc.test.collect.module;

import jdk.tools.jaotc.*;
import jdk.tools.jaotc.test.collect.FakeSearchPath;
import jdk.tools.jaotc.test.collect.Utils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.FileSystems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ModuleSourceProviderTest {
    private ClassLoader classLoader;
    private ModuleSourceProvider target;

    @Before
    public void setUp() {
        classLoader = new FakeClassLoader();
        target = new ModuleSourceProvider(FileSystems.getDefault(), classLoader);
    }

    @Test
    public void itShouldUseSearchPath() {
        FakeSearchPath searchPath = new FakeSearchPath("blah/java.base");
        ModuleSource source = (ModuleSource) target.findSource("java.base", searchPath);
        assertEquals(Utils.set("java.base"), searchPath.entries);
        assertEquals("blah/java.base", source.getModulePath().toString());
        assertEquals("module:blah/java.base", source.toString());
    }

    @Test
    public void itShouldReturnNullIfSearchPathReturnsNull() {
        FakeSearchPath searchPath = new FakeSearchPath(null);
        ModuleSource source = (ModuleSource) target.findSource("jdk.base", searchPath);
        assertEquals(Utils.set("jdk.base"), searchPath.entries);
        assertNull(source);
    }

    private static class FakeClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return null;
        }
    }
}
