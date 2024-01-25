/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8159927
 * @modules java.base/jdk.internal.util
 * @run main JmodExcludedFiles
 * @summary Test that JDK JMOD files do not include native debug symbols
 */

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jdk.internal.util.OperatingSystem;

public class JmodExcludedFiles {
    public static void main(String[] args) throws Exception {
        String javaHome = System.getProperty("java.home");
        Path jmods = Path.of(javaHome, "jmods");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jmods, "*.jmod")) {
            for (Path jmodFile : stream) {
                try (ZipFile zip = new ZipFile(jmodFile.toFile())) {
                    if (zip.stream().map(ZipEntry::getName)
                                    .anyMatch(JmodExcludedFiles::isNativeDebugSymbol)) {
                        throw new RuntimeException(jmodFile + " is expected not to include native debug symbols");
                    }
                }
            }
        }
    }

    private static boolean isNativeDebugSymbol(String name) {
        int index = name.indexOf("/");
        if (index < 0) {
            throw new RuntimeException("unexpected entry name: " + name);
        }
        String section = name.substring(0, index);
        if (section.equals("lib") || section.equals("bin")) {
            if (OperatingSystem.isMacOS()) {
                String n = name.substring(index+1);
                int i = n.indexOf("/");
                if (i != -1) {
                    return n.substring(0, i).endsWith(".dSYM");
                }
            }
            return name.endsWith(".diz")
                    || name.endsWith(".debuginfo")
                    || name.endsWith(".map")
                    || name.endsWith(".pdb");
        }
        return false;
    }
}
