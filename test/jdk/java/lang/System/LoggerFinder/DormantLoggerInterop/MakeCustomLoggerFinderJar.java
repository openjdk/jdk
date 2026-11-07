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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class MakeCustomLoggerFinderJar {
    public static void main(String[] args) throws Exception {
        String testClasses = System.getProperty("test.classes");
        if (testClasses == null) {
            throw new RuntimeException("test.classes not set");
        }

        Path outJar = Path.of(testClasses, "custom-loggerfinder.jar");
        Files.deleteIfExists(outJar);

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outJar))) {
            addClass(jos, testClasses, "CustomLoggerFinder.class");
            addClass(jos, testClasses, "CustomSystemLogger.class");

            JarEntry svc = new JarEntry("META-INF/services/java.lang.System$LoggerFinder");
            jos.putNextEntry(svc);
            jos.write("CustomLoggerFinder\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }

    private static void addClass(JarOutputStream jos, String testClassesDir, String classFile) throws IOException {
        Path p = Path.of(testClassesDir, classFile);
        if (!Files.exists(p)) {
            throw new FileNotFoundException("Missing compiled class: " + p);
        }
        jos.putNextEntry(new JarEntry(classFile));
        Files.copy(p, jos);
        jos.closeEntry();
    }
}
