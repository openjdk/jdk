/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Dump a class file for a class on the class path in the current directory
 */
public class ClassFileInstaller {
    /**
     * @param args The names of the classes to dump
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        for (String arg : args) {
            ClassLoader cl = ClassFileInstaller.class.getClassLoader();

            // Convert dotted class name to a path to a class file
            String pathName = arg.replace('.', '/').concat(".class");
            InputStream is = cl.getResourceAsStream(pathName);

            // Create the class file's package directory
            Path p = Paths.get(pathName);
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Create the class file
            Files.copy(is, p, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
