/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.Configuration;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Basic test of ClassLoader getResource and getResourceAsStream when
 * invoked from code in named modules.
 */

public class Main {

    static final String NAME = "myresource";

    public static void main(String[] args) throws IOException {

        // create m1/myresource containing "m1"
        Path file = directoryFor("m1").resolve(NAME);
        Files.write(file, "m1".getBytes("UTF-8"));

        // create m2/myresource containing "m2"
        file = directoryFor("m2").resolve(NAME);
        Files.write(file, "m2".getBytes("UTF-8"));

        // check that m3/myresource does not exist
        assertTrue(Files.notExists(directoryFor("m3").resolve(NAME)));

        // invoke ClassLoader getResource from the unnamed module
        assertNull(Main.class.getClassLoader().getResource("/" + NAME));

        // invoke ClassLoader getResource from modules m1-m3
        // Resources in a named module are private to that module.
        // ClassLoader.getResource should not find resource in named modules.
        assertNull(p1.Main.getResourceInClassLoader("/" + NAME));
        assertNull(p2.Main.getResourceInClassLoader("/" + NAME));
        assertNull(p3.Main.getResourceInClassLoader("/" + NAME));

        // invoke ClassLoader getResourceAsStream from the unnamed module
        assertNull(Main.class.getClassLoader().getResourceAsStream("/" + NAME));

        // invoke ClassLoader getResourceAsStream from modules m1-m3
        // Resources in a named module are private to that module.
        // ClassLoader.getResourceAsStream should not find resource in named modules.
        assertNull(p1.Main.getResourceAsStreamInClassLoader("/" + NAME));
        assertNull(p2.Main.getResourceAsStreamInClassLoader("/" + NAME));
        assertNull(p3.Main.getResourceAsStreamInClassLoader("/" + NAME));

        // SecurityManager case
        System.setSecurityManager(new SecurityManager());

        assertNull(Main.class.getClassLoader().getResource("/" + NAME));
        assertNull(p1.Main.getResourceInClassLoader("/" + NAME));
        assertNull(p2.Main.getResourceInClassLoader("/" + NAME));
        assertNull(p3.Main.getResourceInClassLoader("/" + NAME));

        assertNull(Main.class.getClassLoader().getResourceAsStream("/" + NAME));
        assertNull(p1.Main.getResourceAsStreamInClassLoader("/" + NAME));
        assertNull(p2.Main.getResourceAsStreamInClassLoader("/" + NAME));
        assertNull(p3.Main.getResourceAsStreamInClassLoader("/" + NAME));

        System.out.println("Success!");
    }

    /**
     * Returns the directory for the given module (by name).
     */
    static Path directoryFor(String mn) {
        Configuration cf = Layer.boot().configuration();
        ResolvedModule resolvedModule = cf.findModule(mn).orElse(null);
        if (resolvedModule == null)
            throw new RuntimeException("not found: " + mn);
        Path dir = Paths.get(resolvedModule.reference().location().get());
        if (!Files.isDirectory(dir))
            throw new RuntimeException("not a directory: " + dir);
        return dir;
    }

    static void assertTrue(boolean condition) {
        if (!condition) throw new RuntimeException();
    }

    static void assertNull(Object o) {
        assertTrue(o == null);
    }
}

