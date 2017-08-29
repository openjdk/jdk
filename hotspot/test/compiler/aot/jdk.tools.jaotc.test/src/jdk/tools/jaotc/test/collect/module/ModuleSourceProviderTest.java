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

/**
 * @test
 * @requires vm.aot
 * @modules jdk.aot/jdk.tools.jaotc
 *          jdk.aot/jdk.tools.jaotc.collect
 *          jdk.aot/jdk.tools.jaotc.collect.module
 * @compile ../Utils.java
 * @run junit/othervm jdk.tools.jaotc.test.collect.module.ModuleSourceProviderTest
 */

package jdk.tools.jaotc.test.collect.module;

import jdk.tools.jaotc.collect.FileSupport;
import jdk.tools.jaotc.collect.module.ModuleSource;
import jdk.tools.jaotc.collect.module.ModuleSourceProvider;
import jdk.tools.jaotc.test.collect.Utils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;

import static jdk.tools.jaotc.test.collect.Utils.mkpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class ModuleSourceProviderTest {
    private ClassLoader classLoader;
    private ModuleSourceProvider target;
    private FileSupport fileSupport;
    private BiFunction<Path, Path, Path> getSubDirectory = null;

    @Before
    public void setUp() {
        classLoader = new FakeClassLoader();
        fileSupport = new FileSupport() {

            @Override
            public boolean isDirectory(Path path) {
                return true;
            }

            @Override
            public Path getSubDirectory(FileSystem fileSystem, Path root, Path path) throws IOException {
                if (getSubDirectory == null) {
                    throw new IOException("Nope");
                }
                return getSubDirectory.apply(root, path);
            }
        };
        target = new ModuleSourceProvider(FileSystems.getDefault(), classLoader, fileSupport);
    }

    @Test
    public void itShouldUseFileSupport() {
        getSubDirectory = (root, path) -> {
            if (root.toString().equals("modules") && path.toString().equals("test.module")) {
                return Paths.get("modules/test.module");
            }
            return null;
        };

        ModuleSource source = (ModuleSource) target.findSource("test.module", null);
        assertEquals(mkpath("modules/test.module"), source.getModulePath().toString());
        assertEquals("module:" + mkpath("modules/test.module"), source.toString());
    }

    private static class FakeClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return null;
        }
    }
}
