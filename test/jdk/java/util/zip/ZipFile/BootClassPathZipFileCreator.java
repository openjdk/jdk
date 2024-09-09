/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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
 * BootClassPathZipFileCreator is a driver that writes to disk
 * multiple ZIP files each containing a single class file.
 * BootClassPathZipFileTest is invoked on each of the ZIP files to
 * confirm proper bootstrap class path handling of various ZIP
 * features.  File descriptions are as follows:
 * <ul>
 *   <li>NonZip64.zip: This ZIP file has no ZIP64 extensions.  It
 *   validates that 8334048 did not break bootstrap class path
 *   handling of non-ZIP64 ZIP files.</li>
 *   <li>TotalMagicZip64.zip: This ZIP file has ZIP64 extensions added
 *   due to the total number of entries (files added) reaching 65535,
 *   the maximum number expressible in the 16-bit "total entries"
 *   field of the central directory header (CEN).  It validates that
 *   8334048 did not break bootstrap class path handling of ZIP files
 *   containing ZIP64 magic values.</li>
 *   <li>NoMagicZip64.zip: Without 8334048's zip_util.c fix, when this
 *   ZIP file is specified on the bootstrap class path, HotSpot does
 *   not find the class file it contains.  The ZIP file has ZIP64
 *   extensions but does not have any magic values in its central
 *   directory header; prior to 8334048, zip_util.c failed to
 *   recognize as valid such ZIP files.</li>
 * </ul>
 */

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

public class BootClassPathZipFileCreator {

    static final String CLASS_NAME = "Test8334048";
    private static final String CLASS_FILE = CLASS_NAME + ".class";
    private static final int ZIP64_MAGICCOUNT = 0xFFFF;
    private static byte[] classBytes;

    private static void createClassBytes() {
        String code = "class " + CLASS_NAME + "{}";
        classBytes = new InMemoryJavaCompiler().compile(CLASS_NAME, code);
    }

    static Path zipPath(String basename) {
        return Paths.get(System.getProperty("test.classes",
                                            System.getProperty("user.dir")),
                         basename);
    }

    private static void createZip(String type) throws Exception {
        HashMap<String, Object> env = new HashMap<>();
        env.put("create", "true");
        if (type.equals("NoMagicZip64")) {
            env.put("forceZIP64End", "true");
        }
        Path zip = zipPath(type + ".zip");
        // Delete any existing ZIP file.
        Files.deleteIfExists(zip);
        // Create ZIP file.
        URI uri = URI.create("jar:" + zip.toUri());
        FileSystem fs = FileSystems.newFileSystem(uri, env);
        if (type.equals("TotalMagicZip64")) {
            byte[] empty = { };
            for (int i = 0; i < ZIP64_MAGICCOUNT - 1; i++) {
                Files.write(fs.getPath("" + i), empty);
            }
            // CLASS_FILE is the 65535th file, pushing the end central
            // header's "total entries" field to 0xffff, also known as
            // ZIP64_MAGICCOUNT.
        }
        Files.write(fs.getPath(CLASS_FILE), classBytes);
        fs.close();
    }

    public static void main(String[] args) throws Exception {
        createClassBytes();
        createZip("NonZip64");
        createZip("TotalMagicZip64");
        createZip("NoMagicZip64");
    }

}
