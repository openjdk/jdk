/*
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6274276
 * @key intermittent
 * @summary JLI JAR manifest processing should ignore leading and trailing white space.
 *
 * @library /test/lib
 * @build ManifestTestApp ManifestTestAgent ExampleForBootClassPath
 * @run main/othervm/timeout=900 ManifestTestDriver
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ManifestTestDriver {

    static final String AGENT = "ManifestTestAgent";
    static String testClasses;
    static Path outOfTheWay;
    static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        testClasses = System.getProperty("test.classes");
        String testSrc = System.getProperty("test.src");

        // Move ExampleForBootClassPath out of the way
        outOfTheWay = Path.of(testClasses, "out_of_the_way");
        Files.createDirectories(outOfTheWay);
        Files.move(Path.of(testClasses, "ExampleForBootClassPath.class"),
            outOfTheWay.resolve("ExampleForBootClassPath.class"));

        // Create bad version
        Path badSrc = Path.of("badsrc");
        Files.createDirectories(badSrc);
        String badSource = Files.readString(Path.of(testSrc, "ExampleForBootClassPath.java"))
            .replace("return 15", "return 42");
        Files.writeString(badSrc.resolve("ExampleForBootClassPath.java"), badSource);
        Path badOut = Path.of(testClasses, "badout");
        Files.createDirectories(badOut);
        CompilerUtils.compile(badSrc, badOut);
        Files.move(badOut.resolve("ExampleForBootClassPath.class"),
            outOfTheWay.resolve("ExampleForBootClassPath.class.bad"));

        // Compile agent in working directory
        CompilerUtils.compile(Path.of(testSrc, AGENT + ".java"), Path.of("."));

        String[] tokens = {
            "defaults",
            "version_line1", "version_line2", "version_line3",
            "premain_line1", "premain_line2", "premain_line3",
            "boot_cp_line1", "boot_cp_line2", "boot_cp_line3", "boot_cp_line4", "boot_cp_line5",
            "can_redef_line1", "can_redef_line2", "can_redef_line3", "can_redef_line4",
            "can_redef_line5", "can_redef_line6", "can_redef_line7", "can_redef_line8",
            "can_redef_line10", "can_redef_line11",
            "can_retrans_line1", "can_retrans_line2", "can_retrans_line3", "can_retrans_line4",
            "can_retrans_line5", "can_retrans_line6", "can_retrans_line7", "can_retrans_line8",
            "can_retrans_line10", "can_retrans_line11",
            "can_set_nmp_line1", "can_set_nmp_line2", "can_set_nmp_line3", "can_set_nmp_line4",
            "can_set_nmp_line5", "can_set_nmp_line6", "can_set_nmp_line7", "can_set_nmp_line8",
            "can_set_nmp_line10", "can_set_nmp_line11"
        };

        for (String token : tokens) {
            System.out.println("\n===== begin test case: " + token + " =====");
            runTestCase(token);
            System.out.println("===== end test case: " + token + " =====\n");
        }

        if (failures > 0) {
            throw new RuntimeException(failures + " test case(s) failed");
        }
        System.out.println("All test cases passed.");
    }

    static void runTestCase(String token) throws Exception {
        String versionLine = "Manifest-Version: 1.0";
        String premainLine = "Premain-Class: " + AGENT;
        String bootCpLine = null;
        String expectBootCp = "ExampleForBootClassPath was not loaded.";
        String canRedefLine = null;
        String expectRedef = "isRedefineClassesSupported()=false";
        String canRetransLine = null;
        String expectRetrans = "isRetransformClassesSupported()=false";
        String canSetNmpLine = null;
        String expectSetNmp = "isNativeMethodPrefixSupported()=false";
        String toBeDeleted = null;

        switch (token) {
            case "defaults": break;
            case "version_line1": versionLine = "Manifest-Version:  1.0"; break;
            case "version_line2": versionLine = "Manifest-Version: 1.0 "; break;
            case "version_line3": versionLine = "Manifest-Version:  1.0 "; break;
            case "premain_line1": premainLine = "Premain-Class:  " + AGENT; break;
            case "premain_line2": premainLine = "Premain-Class: " + AGENT + " "; break;
            case "premain_line3": premainLine = "Premain-Class:  " + AGENT + " "; break;
            case "boot_cp_line1":
                bootCpLine = "Boot-Class-Path: no_white_space";
                expectBootCp = "ExampleForBootClassPath was loaded.";
                Files.createDirectories(Path.of("no_white_space"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class"),
                    Path.of("no_white_space/ExampleForBootClassPath.class"));
                break;
            case "boot_cp_line2":
                bootCpLine = "Boot-Class-Path:  has_leading_blank";
                expectBootCp = "ExampleForBootClassPath was loaded.";
                toBeDeleted = " has_leading_blank";
                Files.createDirectories(Path.of("has_leading_blank"));
                Files.createDirectories(Path.of(" has_leading_blank"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class"),
                    Path.of("has_leading_blank/ExampleForBootClassPath.class"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class.bad"),
                    Path.of(" has_leading_blank/ExampleForBootClassPath.class"));
                break;
            case "boot_cp_line3":
                bootCpLine = "Boot-Class-Path: has_trailing_blank ";
                expectBootCp = "ExampleForBootClassPath was loaded.";
                Files.createDirectories(Path.of("has_trailing_blank"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class"),
                    Path.of("has_trailing_blank/ExampleForBootClassPath.class"));
                if (!IS_WINDOWS) {
                    toBeDeleted = "has_trailing_blank ";
                    Files.createDirectories(Path.of("has_trailing_blank "));
                    Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class.bad"),
                        Path.of("has_trailing_blank /ExampleForBootClassPath.class"));
                }
                break;
            case "boot_cp_line4":
                bootCpLine = "Boot-Class-Path:  has_leading_and_trailing_blank ";
                expectBootCp = "ExampleForBootClassPath was loaded.";
                Files.createDirectories(Path.of("has_leading_and_trailing_blank"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class"),
                    Path.of("has_leading_and_trailing_blank/ExampleForBootClassPath.class"));
                if (!IS_WINDOWS) {
                    toBeDeleted = " has_leading_and_trailing_blank ";
                    Files.createDirectories(Path.of(" has_leading_and_trailing_blank "));
                    Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class.bad"),
                        Path.of(" has_leading_and_trailing_blank /ExampleForBootClassPath.class"));
                }
                break;
            case "boot_cp_line5":
                bootCpLine = "Boot-Class-Path: has_embedded blank";
                expectBootCp = "ExampleForBootClassPath was loaded.";
                toBeDeleted = "has_embedded blank";
                Files.createDirectories(Path.of("has_embedded"));
                Files.createDirectories(Path.of("has_embedded blank"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class"),
                    Path.of("has_embedded/ExampleForBootClassPath.class"));
                Files.copy(outOfTheWay.resolve("ExampleForBootClassPath.class.bad"),
                    Path.of("has_embedded blank/ExampleForBootClassPath.class"));
                break;
            case "can_redef_line1": canRedefLine = "Can-Redefine-Classes: true"; expectRedef = "isRedefineClassesSupported()=true"; break;
            case "can_redef_line2": canRedefLine = "Can-Redefine-Classes:  true"; expectRedef = "isRedefineClassesSupported()=true"; break;
            case "can_redef_line3": canRedefLine = "Can-Redefine-Classes: true "; expectRedef = "isRedefineClassesSupported()=true"; break;
            case "can_redef_line4": canRedefLine = "Can-Redefine-Classes:  true "; expectRedef = "isRedefineClassesSupported()=true"; break;
            case "can_redef_line5": canRedefLine = "Can-Redefine-Classes: false"; break;
            case "can_redef_line6": canRedefLine = "Can-Redefine-Classes:  false"; break;
            case "can_redef_line7": canRedefLine = "Can-Redefine-Classes: false "; break;
            case "can_redef_line8": canRedefLine = "Can-Redefine-Classes:  false "; break;
            case "can_redef_line10": canRedefLine = "Can-Redefine-Classes: "; break;
            case "can_redef_line11": canRedefLine = "Can-Redefine-Classes:  "; break;
            case "can_retrans_line1": canRetransLine = "Can-Retransform-Classes: true"; expectRetrans = "isRetransformClassesSupported()=true"; break;
            case "can_retrans_line2": canRetransLine = "Can-Retransform-Classes:  true"; expectRetrans = "isRetransformClassesSupported()=true"; break;
            case "can_retrans_line3": canRetransLine = "Can-Retransform-Classes: true "; expectRetrans = "isRetransformClassesSupported()=true"; break;
            case "can_retrans_line4": canRetransLine = "Can-Retransform-Classes:  true "; expectRetrans = "isRetransformClassesSupported()=true"; break;
            case "can_retrans_line5": canRetransLine = "Can-Retransform-Classes: false"; break;
            case "can_retrans_line6": canRetransLine = "Can-Retransform-Classes:  false"; break;
            case "can_retrans_line7": canRetransLine = "Can-Retransform-Classes: false "; break;
            case "can_retrans_line8": canRetransLine = "Can-Retransform-Classes:  false "; break;
            case "can_retrans_line10": canRetransLine = "Can-Retransform-Classes: "; break;
            case "can_retrans_line11": canRetransLine = "Can-Retransform-Classes:  "; break;
            case "can_set_nmp_line1": canSetNmpLine = "Can-Set-Native-Method-Prefix: true"; expectSetNmp = "isNativeMethodPrefixSupported()=true"; break;
            case "can_set_nmp_line2": canSetNmpLine = "Can-Set-Native-Method-Prefix:  true"; expectSetNmp = "isNativeMethodPrefixSupported()=true"; break;
            case "can_set_nmp_line3": canSetNmpLine = "Can-Set-Native-Method-Prefix: true "; expectSetNmp = "isNativeMethodPrefixSupported()=true"; break;
            case "can_set_nmp_line4": canSetNmpLine = "Can-Set-Native-Method-Prefix:  true "; expectSetNmp = "isNativeMethodPrefixSupported()=true"; break;
            case "can_set_nmp_line5": canSetNmpLine = "Can-Set-Native-Method-Prefix: false"; break;
            case "can_set_nmp_line6": canSetNmpLine = "Can-Set-Native-Method-Prefix:  false"; break;
            case "can_set_nmp_line7": canSetNmpLine = "Can-Set-Native-Method-Prefix: false "; break;
            case "can_set_nmp_line8": canSetNmpLine = "Can-Set-Native-Method-Prefix:  false "; break;
            case "can_set_nmp_line10": canSetNmpLine = "Can-Set-Native-Method-Prefix: "; break;
            case "can_set_nmp_line11": canSetNmpLine = "Can-Set-Native-Method-Prefix:  "; break;
        }

        // Write manifest with raw bytes to preserve exact whitespace
        try (FileOutputStream out = new FileOutputStream(AGENT + ".mf")) {
            out.write((versionLine + "\n").getBytes());
            out.write((premainLine + "\n").getBytes());
            if (bootCpLine != null) out.write((bootCpLine + "\n").getBytes());
            if (canRedefLine != null) out.write((canRedefLine + "\n").getBytes());
            if (canRetransLine != null) out.write((canRetransLine + "\n").getBytes());
            if (canSetNmpLine != null) out.write((canSetNmpLine + "\n").getBytes());
        }

        // Create agent jar
        String jar = Path.of(System.getProperty("test.jdk"), "bin", "jar").toString();
        ProcessBuilder pb = new ProcessBuilder(jar, "cvfm",
            AGENT + ".jar", AGENT + ".mf", AGENT + ".class");
        pb.inheritIO();
        int rc = pb.start().waitFor();
        if (rc != 0) throw new RuntimeException("jar failed");

        // Run test
        OutputAnalyzer output = ProcessTools.executeTestJava(
            "-javaagent:" + AGENT + ".jar",
            "-classpath", testClasses,
            "ManifestTestApp");

        String stdout = output.getStdout() + output.getStderr();

        boolean failed = false;
        if (output.getExitValue() != 0) {
            System.out.println("FAIL: exit code " + output.getExitValue());
            failed = true;
        }
        if (!stdout.contains("Hello from ManifestTestAgent!")) {
            System.out.println("FAIL: agent message not found");
            failed = true;
        }
        if (!stdout.contains(expectBootCp)) {
            System.out.println("FAIL: expected '" + expectBootCp + "' not found");
            failed = true;
        }
        if (!stdout.contains(expectRedef)) {
            System.out.println("FAIL: expected '" + expectRedef + "' not found");
            failed = true;
        }
        if (!stdout.contains(expectRetrans)) {
            System.out.println("FAIL: expected '" + expectRetrans + "' not found");
            failed = true;
        }
        if (!stdout.contains(expectSetNmp)) {
            System.out.println("FAIL: expected '" + expectSetNmp + "' not found");
            failed = true;
        }

        if (failed) {
            System.out.println("OUTPUT: " + stdout);
            failures++;
        } else {
            System.out.println("PASS");
        }

        // Cleanup directories with spaces
        if (toBeDeleted != null) {
            deleteRecursive(Path.of(toBeDeleted));
        }
    }

    static void deleteRecursive(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        deleteRecursive(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best effort cleanup
        }
    }
}
