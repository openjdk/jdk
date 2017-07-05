/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Common library for various test jar file utility functions.
 */
public final class JarUtils {

    /**
     * Create jar file with specified files.
     */
    public static void createJar(String dest, String... files)
            throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(dest), new Manifest())) {
            for (String file : files) {
                System.out.println(String.format("Adding %s to %s",
                        file, dest));

                // add an archive entry, and write a file
                jos.putNextEntry(new JarEntry(file));
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(jos);
                }
            }
        }
        System.out.println();
    }

    /**
     * Add specified files to existing jar file.
     */
    public static void updateJar(String src, String dest, String... files)
            throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(dest))) {

            // copy each old entry into destination unless the entry name
            // is in the updated list
            List<String> updatedFiles = new ArrayList<>();
            try (JarFile srcJarFile = new JarFile(src)) {
                Enumeration entries = srcJarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = (JarEntry) entries.nextElement();
                    String name = entry.getName();
                    boolean found = false;
                    for (String file : files) {
                        if (name.equals(file)) {
                            updatedFiles.add(file);
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        System.out.println(String.format("Updating %s with %s",
                                dest, name));
                        jos.putNextEntry(new JarEntry(name));
                        try (FileInputStream fis = new FileInputStream(name)) {
                            fis.transferTo(jos);
                        }
                    } else {
                        System.out.println(String.format("Copying %s to %s",
                                name, dest));
                        jos.putNextEntry(entry);
                        srcJarFile.getInputStream(entry).transferTo(jos);
                    }
                }
            }

            // append new files
            for (String file : files) {
                if (!updatedFiles.contains(file)) {
                    System.out.println(String.format("Adding %s with %s",
                            dest, file));
                    jos.putNextEntry(new JarEntry(file));
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.transferTo(jos);
                    }
                }
            }
        }
        System.out.println();
    }

}
