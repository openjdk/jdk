/*
 * Copyright (c) 2026 SAP SE. All rights reserved.
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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.test.lib.Platform;

/*
 * @test
 * @summary Check for unwanted files (types/extensions) in the jdk image
 * @library /test/lib
 * @requires !vm.debug
 * @run main CheckFiles
 */
public class CheckFiles {

    // Set this property on command line to scan an alternate dir or file:
    // JTREG=JAVA_OPTIONS=-Djdk.test.build.CheckFiles.dir=/path/to/dir
    public static final String DIR_PROPERTY = "jdk.test.build.CheckFiles.dir";

    private static boolean isGpl(Path myFile) {
        if (myFile == null || !Files.exists(myFile)) {
            return false;
        }

        try {
            String firstLine = Files.readAllLines(myFile).stream()
                                    .findFirst().orElse("");
            return firstLine.contains("The GNU General Public License (GPL)");
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        String jdkPathString = System.getProperty("test.jdk");
        Path jdkHome = Paths.get(jdkPathString);

        Path mainDirToScan = jdkHome;
        String overrideDir = System.getProperty(DIR_PROPERTY);
        if (overrideDir != null) {
            mainDirToScan = Paths.get(overrideDir);
        }

        System.out.println("Main directory to scan:" + mainDirToScan);
        Path binDir = mainDirToScan.resolve("bin");
        Path libDir = mainDirToScan.resolve("lib");
        Path includeDir = mainDirToScan.resolve("include");
        Path jmodsDir = mainDirToScan.resolve("jmods");

        System.out.println("Bin directory to scan:" + binDir);
        ArrayList<String> allowedEndingsBinDir = new ArrayList<>();
        // UNIX - no extensions are allowed; Windows : .dll, .exe, .pdb, .jsa
        if (Platform.isWindows()) {
            allowedEndingsBinDir.add(".dll");
            allowedEndingsBinDir.add(".exe");
            allowedEndingsBinDir.add(".pdb");
            allowedEndingsBinDir.add(".jsa");
        }
        boolean binDirRes = scanFiles(binDir, allowedEndingsBinDir);

        System.out.println("Lib directory to scan:" + libDir);
        ArrayList<String> allowedEndingsLibDir = new ArrayList<>();
        allowedEndingsLibDir.add(".jfc");  // jfr config files
        allowedEndingsLibDir.add("cacerts");
        allowedEndingsLibDir.add("blocked.certs");
        allowedEndingsLibDir.add("public_suffix_list.dat");
        allowedEndingsLibDir.add("classlist");
        allowedEndingsLibDir.add("fontconfig.bfc");
        allowedEndingsLibDir.add("fontconfig.properties.src");
        allowedEndingsLibDir.add("ct.sym");
        allowedEndingsLibDir.add("jrt-fs.jar");
        allowedEndingsLibDir.add("jvm.cfg");
        allowedEndingsLibDir.add("modules");
        allowedEndingsLibDir.add("psfontj2d.properties");
        allowedEndingsLibDir.add("psfont.properties.ja");
        allowedEndingsLibDir.add("src.zip");
        allowedEndingsLibDir.add("tzdb.dat");
        allowedEndingsLibDir.add("javafx-swt.jar");
        allowedEndingsLibDir.add("javafx.properties");
        if (Platform.isWindows()) {
            allowedEndingsLibDir.add(".lib");
            allowedEndingsLibDir.add("tzmappings");
        } else {
            allowedEndingsLibDir.add("jexec");
            allowedEndingsLibDir.add("jspawnhelper");
            allowedEndingsLibDir.add(".jsa");
            if (Platform.isOSX()) {
                allowedEndingsLibDir.add("shaders.metallib");
                allowedEndingsLibDir.add(".dylib");
            } else {
                allowedEndingsLibDir.add(".so");
            }
            if (Platform.isAix()) {
                allowedEndingsLibDir.add("tzmappings");
            }
        }
        boolean libDirRes = scanFiles(libDir, allowedEndingsLibDir);

        if (binDirRes) {
            System.out.println("Bin directory scan successful.");
        } else {
            throw new Error("bin dir scan failed");
        }

        if (libDirRes) {
            System.out.println("Lib directory scan successful.");
        } else {
            throw new Error("lib dir scan failed");
        }

        if (Files.isDirectory(includeDir)) {
            System.out.println("Include directory to scan:" + includeDir);
            ArrayList<String> allowedEndingsIncludeDir = new ArrayList<>();
            allowedEndingsIncludeDir.add(".h");
            allowedEndingsIncludeDir.add(".hpp");
            boolean includeDirRes = scanFiles(includeDir, allowedEndingsIncludeDir);
            if (includeDirRes) {
                System.out.println("Include directory scan successful.");
            } else {
                throw new Error("include dir scan failed");
            }
        }

        // when enabling "JEP 493: Linking Run-Time Images without JMODs" we do not
        // have the jmods folder at all, so first test the presence of the folder
        if (Files.isDirectory(jmodsDir)) {
            System.out.println("Jmods directory to scan:" + jmodsDir);
            ArrayList<String> allowedEndingsJmodsDir = new ArrayList<>();
            allowedEndingsJmodsDir.add(".jmod");
            boolean jmodsDirRes = scanFiles(jmodsDir, allowedEndingsJmodsDir);
            if (jmodsDirRes) {
                System.out.println("Jmods directory scan successful.");
            } else {
                throw new Error("jmods dir scan failed");
            }
        }

        Path legalDir = mainDirToScan.resolve("legal");
        Path javabaseLicenseFile = mainDirToScan.resolve("legal/java.base/LICENSE");
        if (isGpl(javabaseLicenseFile)) { // for now check only legal dir of GPL based images; other ones might have other content
            System.out.println("GPL info found in java.base LICENSE file");
            ArrayList<String> allowedEndingsLegalDir = new ArrayList<>();
            allowedEndingsLegalDir.add(".md");
            allowedEndingsLegalDir.add("ADDITIONAL_LICENSE_INFO");
            allowedEndingsLegalDir.add("ASSEMBLY_EXCEPTION");
            allowedEndingsLegalDir.add("LICENSE");

            ArrayList<String> requiredFilesInLegalSubdirs = new ArrayList<>();
            requiredFilesInLegalSubdirs.add("LICENSE");
            requiredFilesInLegalSubdirs.add("ADDITIONAL_LICENSE_INFO");
            requiredFilesInLegalSubdirs.add("ASSEMBLY_EXCEPTION");

            System.out.println("Legal directory to scan:" + legalDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(legalDir)) {
                for (Path subfolder : stream) {
                    if (Files.isDirectory(subfolder)) {
                        System.out.println("Checking legal dir subfolder for required files: " + subfolder.getFileName());

                        for (String fileName : requiredFilesInLegalSubdirs) {
                            Path filePath = subfolder.resolve(fileName);
                            if (Files.exists(filePath)) {
                                System.out.println("  Found " + fileName);
                            } else {
                                System.out.println("  Missing " + fileName);
                                throw new Error("legal dir scan for required files failed");
                            }
                        }
                    }
                }
            }

            boolean legalDirRes = scanFiles(legalDir, allowedEndingsLegalDir);
            if (legalDirRes) {
                System.out.println("Legal directory scan successful.");
            } else {
                throw new Error("Legal dir scan failed");
            }
        }
    }

    private static boolean scanFiles(Path root, ArrayList<String> allowedEndings) throws IOException {
        AtomicBoolean badFileFound = new AtomicBoolean(false);

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fullFileName = file.toString();
                String fileName = file.getFileName().toString();
                System.out.println("  visiting file:" + fullFileName);
                checkFile(fileName, allowedEndings);
                return super.visitFile(file, attrs);
            }

            private void checkFile(String name, ArrayList<String> allowedEndings) {
                if (allowedEndings.isEmpty()) {  // no file extensions allowed
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot > 0) {
                        System.out.println("  --> ERROR this file is not allowed:" + name);
                        badFileFound.set(true);
                    }
                } else {
                    boolean allowed = allowedEndings.stream().anyMatch(name::endsWith);
                    if (! allowed) {
                        System.out.println("  --> ERROR this file is not allowed:" + name);
                        badFileFound.set(true);
                    }
                }
            }
        });

        return !badFileFound.get();
    }
}
