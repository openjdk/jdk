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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Basic test of Class getResource and getResourceAsStream when invoked from
 * code in named modules.
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

        // invoke Class getResource from the unnamed module
        URL url0 = Main.class.getResource("/" + NAME);
        assertNull(url0);

        // invoke Class getResource from modules m1-m3
        URL url1 = p1.Main.getResource("/" + NAME);
        URL url2 = p2.Main.getResource("/" + NAME);
        URL url3 = p3.Main.getResource("/" + NAME);
        assertNotNull(url1);
        assertNotNull(url2);
        assertNull(url3);

        // check contents of resurces at url1 and url2
        assertEquals(new String(readAll(url1), "UTF-8"), "m1");
        assertEquals(new String(readAll(url2), "UTF-8"), "m2");

        // invoke Class getResourceAsStream from the unnamed module
        InputStream in0 = Main.class.getResourceAsStream("/" + NAME);
        assertNull(in0);

        // invoke Class getResourceAsStream from modules m1-m3
        try (InputStream in = p1.Main.getResourceAsStream("/" + NAME)) {
            String s = new String(in.readAllBytes(), "UTF-8");
            assertEquals(s, "m1");
        }
        try (InputStream in = p2.Main.getResourceAsStream("/" + NAME)) {
            String s = new String(in.readAllBytes(), "UTF-8");
            assertEquals(s, "m2");
        }
        InputStream in3 = p3.Main.getResourceAsStream("/" + NAME);
        assertNull(in3);

        // invoke Module getResources on modules m1-m3
        InputStream in1 = p1.Main.class.getModule().getResourceAsStream("/" + NAME);
        InputStream in2 = p2.Main.class.getModule().getResourceAsStream("/" + NAME);
        in3 = p3.Main.class.getModule().getResourceAsStream("/" + NAME);
        assertNotNull(in1);
        assertNotNull(in2);
        assertNull(in3);

        // check the content of in1 and in2
        String s1 = new String(in1.readAllBytes(), "UTF-8");
        String s2 = new String(in2.readAllBytes(), "UTF-8");
        assertEquals(s1, "m1");
        assertEquals(s2, "m2");

        // SecurityManager case
        System.setSecurityManager(new SecurityManager());

        assertNull(Main.class.getResource("/" + NAME));
        assertNull(p1.Main.getResource("/" + NAME));
        assertNull(p2.Main.getResource("/" + NAME));
        assertNull(p3.Main.getResource("/" + NAME));

        assertNull(Main.class.getResourceAsStream("/" + NAME));
        assertNull(p1.Main.getResourceAsStream("/" + NAME));
        assertNull(p2.Main.getResourceAsStream("/" + NAME));
        assertNull(p3.Main.getResourceAsStream("/" + NAME));

        System.out.println("Success!");
    }

    /**
     * Returns the directory for the given module (by name).
     */
    static Path directoryFor(String name) {
        Configuration cf = Layer.boot().configuration();
        ResolvedModule resolvedModule = cf.findModule(name).orElse(null);
        if (resolvedModule == null)
            throw new RuntimeException("not found: " + name);
        Path dir = Paths.get(resolvedModule.reference().location().get());
        if (!Files.isDirectory(dir))
            throw new RuntimeException("not a directory: " + dir);
        return dir;
    }

    static byte[] readAll(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }

    static void assertTrue(boolean condition) {
        if (!condition) throw new RuntimeException();
    }

    static void assertNull(Object o) {
        assertTrue(o == null);
    }

    static void assertNotNull(Object o) {
        assertTrue(o != null);
    }

    static void assertEquals(Object actual, Object expected) {
        if (expected == null) {
            assertNull(actual);
        } else {
            assertTrue(expected.equals(actual));
        }
    }

    static void assertNotEquals(Object actual, Object expected) {
        if (expected == null) {
            assertNotNull(actual);
        } else {
            assertTrue(!expected.equals(actual));
        }
    }
}

