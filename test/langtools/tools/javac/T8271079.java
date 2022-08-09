/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8271079
 * @summary JavaFileObject#toUri in MR-JAR returns real path
 * @modules java.compiler
 *          jdk.compiler
 * @run main T8271079
 */

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import javax.tools.*;

public class T8271079 {

    public static void main(String[] args) throws Exception {
        new T8271079().run();
    }

    final PrintStream out;

    T8271079() {
        this.out = System.out;
    }

    void run() throws Exception {
        Path mr = generateMultiReleaseJar();
        try {
            testT8271079(mr);
        } finally {
            Files.deleteIfExists(mr);
        }
    }

    // $ echo 'module hello {}' > module-info.java
    // $ javac -d classes --release 9 module-info.java
    // $ jar --create --file mr.jar --release 9 -C classes .
    Path generateMultiReleaseJar() throws Exception {
        Files.writeString(Path.of("module-info.java"), "module hello {}");
        java.util.spi.ToolProvider.findFirst("javac").orElseThrow()
            .run(out, System.err, "-d", "classes", "--release", "9", "module-info.java");
        Path mr = Path.of("mr.jar");
        java.util.spi.ToolProvider.findFirst("jar").orElseThrow()
            .run(out, System.err, "--create", "--file", mr.toString(), "--release", "9", "-C", "classes", ".");
        out.println("Created: " + mr.toUri());
        out.println(" Exists: " + Files.exists(mr));
        return mr;
    }

    void testT8271079(Path path) throws Exception {
        StandardJavaFileManager fileManager =
            ToolProvider.getSystemJavaCompiler()
                .getStandardFileManager(null, Locale.ENGLISH, StandardCharsets.UTF_8);
        fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(path));
        Iterator<String> options = Arrays.asList("--multi-release", "9").iterator();
        fileManager.handleOption(options.next(), options);

        Iterable<JavaFileObject> list =
            fileManager.list(
                StandardLocation.CLASS_PATH, "", EnumSet.allOf(JavaFileObject.Kind.class), false);

        for (JavaFileObject f : list) {
            out.println("JavaFileObject#getName: " + f.getName());
            out.println("JavaFileObject#toUri: " + f.toUri());
            openUsingUri(f.toUri());
        }
        System.gc(); // JDK-8224794
    }

    void openUsingUri(URI uri) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        connection.setUseCaches(false); // JDK-8224794
        if (connection instanceof JarURLConnection jar) {
            try {
                JarEntry entry = jar.getJarEntry();
                out.println("JarEntry#getName: " + entry.getName());
                connection.getInputStream().close(); // JDK-8224794
            } catch (FileNotFoundException e) {
                throw e;
            }
        }
    }
}
