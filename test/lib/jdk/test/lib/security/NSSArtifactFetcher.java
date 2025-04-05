/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.security;

import jdk.test.lib.Platform;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.security.artifacts.ThirdPartyArtifacts;
import jtreg.SkippedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

public class NSSArtifactFetcher {

    public static final String DEFAULT_NSS_LIBRARY =  "softokn3";

     private static Class<?> getNssLibClass(String osId){
        switch (osId) {
            case "Windows-amd64-64":
                return ThirdPartyArtifacts.NSS_WINDOWS_X64.class;

            case "MacOSX-x86_64-64":
                return ThirdPartyArtifacts.NSS_MACOSX_X64.class;

            case "MacOSX-aarch64-64":
                return ThirdPartyArtifacts.NSS_MACOSX_AARCH64.class;

            case "Linux-amd64-64":
                if (Platform.isOracleLinux7()) {
                    throw new SkippedException("Skipping Oracle Linux prior to v8");
                } else {
                    return ThirdPartyArtifacts.NSS_LINUX_X64.class;
                }

            case "Linux-aarch64-64":
                if (Platform.isOracleLinux7()) {
                    throw new SkippedException("Skipping Oracle Linux prior to v8");
                } else {
                    return ThirdPartyArtifacts.NSS_LINUX_AARCH64.class;
                }
            default:
                return null;
        }
    }

    /**
     * This method returns the path to the directory containing library
     * (.so/.dll/.dylib) files, not the parent directory.
     *
     * @param library library name
     * @return NSS lib path
     */
    public static String getNSSLibDir(String library) throws Exception {
        final Path libPath = getNSSLibPath(library);

        final String libDir = String.valueOf(libPath.getParent()) + File.separatorChar;
        System.out.println("nssLibDir: " + libDir);
        System.setProperty("pkcs11test.nss.libdir", libDir);
        return libDir;
    }

    /**
     * This method returns the NSS parent folder path containing all NSS Libraries
     * that might be installed.
     * Automatically detects the architecture, hence no parameter needed.
     *
     * @return NSS parent dir path
     */
    public static Path getNSSDirectory() {
        final String osId = getOsId();
        final Class<?> clazz = getNssLibClass(osId);
        if (clazz == null) {
            throw new SkippedException("Warning: unsupported OS: " + osId
                                       + ", please initialize NSS library location, skipping test");
        }
        return ArtifactResolver.fetchOne(clazz);
    }

    public static Path getNSSLibPath(String library) throws Exception {
        final Path dir = getNSSDirectory();

        return findNSSLibrary(dir, Path.of(System.mapLibraryName(library)));
    }

    private static String getOsId() {

        final Properties props = System.getProperties();

        String osName = props.getProperty("os.name");
        if (osName.startsWith("Win")) {
            osName = "Windows";
        } else if (osName.equals("Mac OS X")) {
            osName = "MacOSX";
        }
        return osName + "-" + props.getProperty("os.arch") + "-"
               + props.getProperty("sun.arch.data.model");
    }

    private static Path findNSSLibrary(Path path, Path libraryName) throws IOException {
        try(Stream<Path> files = Files.find(path, 10,
                (tp, attr) -> tp.getFileName().equals(libraryName))) {

            return files.findAny()
                    .orElseThrow(() ->
                            new RuntimeException("NSS library \"" + libraryName + "\" was not found in " + path));
        }
    }

}
