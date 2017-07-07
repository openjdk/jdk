/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
     * Create jar file with specified files. If a specified file does not exist,
     * a new jar entry will be created with the file name itself as the content.
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
                } catch (FileNotFoundException e) {
                    jos.write(file.getBytes());
                }
            }
        }
        System.out.println();
    }

    /**
     * Add or remove specified files to existing jar file. If a specified file
     * to be updated or added does not exist, the jar entry will be created
     * with the file name itself as the content.
     *
     * @param src the original jar file name
     * @param dest the new jar file name
     * @param files the files to update. The list is broken into 2 groups
     *              by a "-" string. The files before in the 1st group will
     *              be either updated or added. The files in the 2nd group
     *              will be removed. If no "-" exists, all files belong to
     *              the 1st group.
     */
    public static void updateJar(String src, String dest, String... files)
            throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(dest))) {

            // copy each old entry into destination unless the entry name
            // is in the updated list
            List<String> updatedFiles = new ArrayList<>();
            try (JarFile srcJarFile = new JarFile(src)) {
                Enumeration<JarEntry> entries = srcJarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    boolean found = false;
                    boolean update = true;
                    for (String file : files) {
                        if (file.equals("-")) {
                            update = false;
                        } else if (name.equals(file)) {
                            updatedFiles.add(file);
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        if (update) {
                            System.out.println(String.format("Updating %s with %s",
                                    dest, name));
                            jos.putNextEntry(new JarEntry(name));
                            try (FileInputStream fis = new FileInputStream(name)) {
                                fis.transferTo(jos);
                            } catch (FileNotFoundException e) {
                                jos.write(name.getBytes());
                            }
                        } else {
                            System.out.println(String.format("Removing %s from %s",
                                    name, dest));
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
                if (file.equals("-")) {
                    break;
                }
                if (!updatedFiles.contains(file)) {
                    System.out.println(String.format("Adding %s with %s",
                            dest, file));
                    jos.putNextEntry(new JarEntry(file));
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.transferTo(jos);
                    } catch (FileNotFoundException e) {
                        jos.write(file.getBytes());
                    }
                }
            }
        }
        System.out.println();
    }

}
