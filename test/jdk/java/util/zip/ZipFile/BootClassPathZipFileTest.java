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
 * @test
 * @bug 8334048
 * @summary -Xbootclasspath can not read some ZIP64 ZIP files
 * @library /test/lib
 * @run driver BootClassPathZipFileCreator
 * @run main/othervm -Xbootclasspath/a:${test.classes}/NonZip64.zip
 *       BootClassPathZipFileTest NonZip64.zip
 * @run main/othervm -Xbootclasspath/a:${test.classes}/TotalMagicZip64.zip
 *       BootClassPathZipFileTest TotalMagicZip64.zip
 * @run main/othervm -Xbootclasspath/a:${test.classes}/NoMagicZip64.zip
 *       BootClassPathZipFileTest NoMagicZip64.zip
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BootClassPathZipFileTest {

    public static void main(String[] args) throws Exception {
        ClassLoader loader = BootClassPathZipFileTest.class.getClassLoader();
        // Ensure the ZIP file exists, otherwise the failure signature of
        // the ZIP file not existing is the same as the ZIP file not being
        // readable, that is, ClassNotFoundException on CLASS_NAME.
        Path zip = BootClassPathZipFileCreator.zipPath(args[0]);
        if (!Files.exists(zip)) {
            throw new RuntimeException(zip + " does not exist");
        }
        Class c = loader.loadClass(BootClassPathZipFileCreator.CLASS_NAME);
    }

}
