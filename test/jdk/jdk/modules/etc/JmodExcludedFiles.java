/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @requires jlink.packagedModules
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JmodExcludedFiles
 * @summary Test that JDK JMOD files do not include native debug symbols when it is not configured
 */

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jdk.internal.util.OperatingSystem;
import jdk.test.whitebox.WhiteBox;

public class JmodExcludedFiles {
    private static String javaHome = System.getProperty("java.home");
    private static final boolean expectSymbols = WhiteBox.getWhiteBox().shipsDebugInfo();

    public static void main(String[] args) throws Exception {
        Path jmods = Path.of(javaHome, "jmods");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jmods, "*.jmod")) {
            for (Path jmodFile : stream) {
                try (ZipFile zip = new ZipFile(jmodFile.toFile())) {
                    JModSymbolFileMatcher jsfm = new JModSymbolFileMatcher(jmodFile.toString());
                    if (zip.stream().map(ZipEntry::getName)
                                    .anyMatch(jsfm::isNativeDebugSymbol)) {
                        throw new RuntimeException(jmodFile + " is expected not to include native debug symbols");
                    }
                }
            }
        }
    }

    static class JModSymbolFileMatcher {
        private String jmod;

        JModSymbolFileMatcher(String jmod) {
            this.jmod = jmod;
        }

        boolean isNativeDebugSymbol(String name) {
            int index = name.indexOf("/");
            if (index < 0) {
                throw new RuntimeException("unexpected entry name: " + name);
            }
            String section = name.substring(0, index);
            if (section.equals("lib") || section.equals("bin")) {
                if (OperatingSystem.isMacOS()) {
                    String n = name.substring(index + 1);
                    int i = n.indexOf("/");
                    if (i != -1) {
                        if (n.substring(0, i).endsWith(".dSYM")) {
                            System.err.println("Found symbols in " + jmod + ": " + name);
                            return expectSymbols ? false: true;
                        }
                    }
                }
                if (OperatingSystem.isWindows() && name.endsWith(".pdb")) {
                    System.err.println("Found symbols in " + jmod + ": " + name);
                    return expectSymbols ? false: true;
                }
                if (name.endsWith(".diz")
                        || name.endsWith(".debuginfo")
                        || name.endsWith(".map")) {
                    System.err.println("Found symbols in " + jmod + ": " + name);
                    return expectSymbols ? false: true;
                }
            }
            return false;
        }
    }
}
