/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */


/*
 * @test
 * @summary Check that -XX:+AutoCreateSharedArchive automatically recreates an archive when you change the JDK version.
 * @requires vm.cds & vm.bits == "64"
 * @library /test/lib
 * @compile -source 1.8 -target 1.8 ../test-classes/HelloJDK8.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar Hello.jar HelloJDK8
 * @run driver/timeout=600 TestAutoCreateSharedArchiveUpgrade
 */

import java.io.File;
import java.util.HashMap;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

public class TestAutoCreateSharedArchiveUpgrade {

    // The JDK being tested
    private static final String TEST_JDK = System.getProperty("test.jdk", null);

    // If you're running this test manually, specify the location of a previous version of
    // the JDK using "jtreg -vmoption:-Dtest.previous.jdk=${JDK19_HOME} ..."
    private static final String PREV_JDK = System.getProperty("test.previous.jdk", null);

    // If you're running this test using something like
    // "make test TEST=test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/TestAutoCreateSharedArchiveUpgrade.java",
    // the test.boot.jdk property is normally passed by make/RunTests.gmk
    private static String BOOT_JDK = System.getProperty("test.boot.jdk", null);

    // Comma separated list of JDK major versions that will be tested
    private static String JDK_VERSIONS = System.getProperty("test.autocreatesharedarchive.jdk.version", null);

    private static final String USER_DIR = System.getProperty("user.dir", ".");
    private static final String FS = System.getProperty("file.separator", "/");

    private static final String JAR = ClassFileInstaller.getJarPath("Hello.jar");
    private static final String JSA = USER_DIR + FS + "Hello.jsa";

    private static String oldJVM;
    private static String newJVM;

    public static void main(String[] args) throws Throwable {
        // Earliest testable version is 19
        int n = java.lang.Runtime.version().major();

        // If JDK_VERSIONS is specified, test against each specified version;
        // otherwise test with PREV_JDK if specified;
        // otherwise test with BOOT_JDK if specified;
        // otherwise throw SkippedException.
        if (JDK_VERSIONS == null) {
            System.out.println("JDK_VERSIONS not specified");
            setupJVMs(0);
            doTest();
            return;
        }

        String[] versions = JDK_VERSIONS.split(",");
        for (int i = 0; i < versions.length; i++) {
            System.out.println("Testing JDK: " + versions[i]);
            try {
                setupJVMs(Integer.parseInt(versions[i]));
                doTest();
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid AutoCreateSharedArchive JDK version: " + versions[i]);
            }
        }
    }

    static void setupJVMs(int fetchVersion) throws Throwable {
        if (TEST_JDK == null) {
            throw new RuntimeException("-Dtest.jdk should point to the JDK being tested");
        }

        newJVM = TEST_JDK + FS + "bin" + FS + "java";

        // Version 0 is used here to indicate that no version is supplied so that
        // PREV_JDK or BOOT_JDK are used
        if (fetchVersion >= 19) {
            oldJVM = fetchJDK(fetchVersion) + FS + "bin" + FS + "java";
        } else if (fetchVersion > 0) {
            throw new RuntimeException("Unsupported JDK version " + fetchVersion);
        } else if (PREV_JDK != null) {
            oldJVM = PREV_JDK + FS + "bin" + FS + "java";
        } else if (BOOT_JDK != null) {
            oldJVM = BOOT_JDK + FS + "bin" + FS + "java";
        } else {
            throw new SkippedException("Use -Dtest.previous.jdk or -Dtest.boot.jdk to specify a " +
                                       "previous version of the JDK that supports " +
                                       "-XX:+AutoCreateSharedArchive");
        }

        System.out.println("Using newJVM = " + newJVM);
        System.out.println("Using oldJVM = " + oldJVM);
    }

    static void doTest() throws Throwable {
        File jsaF = new File(JSA);
        jsaF.delete();
        OutputAnalyzer output;

        // NEW JDK -- create and then use the JSA
        output = run(newJVM);
        assertJSANotFound(output);
        assertCreatedJSA(output);

        output = run(newJVM);
        assertUsedJSA(output);

        // OLD JDK -- should reject the JSA created by NEW JDK, and create its own
        output = run(oldJVM);
        assertCreatedJSA(output);

        output = run(oldJVM);
        assertUsedJSA(output);

        // NEW JDK -- should reject the JSA created by OLD JDK, and create its own
        output = run(newJVM);
        assertCreatedJSA(output);

        output = run(newJVM);
        assertUsedJSA(output);
    }

    static OutputAnalyzer run(String jvm) throws Throwable {
        OutputAnalyzer output =
            ProcessTools.executeCommand(jvm, "-XX:+AutoCreateSharedArchive",
                                        "-XX:SharedArchiveFile=" + JSA,
                                        "-Xlog:cds",
                                        "-cp", JAR, "HelloJDK8");
        output.shouldHaveExitValue(0);
        return output;
    }

    static void assertJSANotFound(OutputAnalyzer output) {
        output.shouldContain("Specified shared archive not found");
    }

    static void assertCreatedJSA(OutputAnalyzer output) {
        output.shouldContain("Dumping shared data to file");
    }

    static void assertUsedJSA(OutputAnalyzer output) {
        output.shouldContain("Mapped dynamic region #0");
    }

    // Fetch JDK artifact depending on platform
    // If the artifact cannot be found, throw RuntimeException
    private static String fetchJDK(int version) throws Throwable {
        int build;
        String architecture;
        HashMap<String, Object> jdkArtifactMap = new HashMap<>();
        jdkArtifactMap.put("server", "jpg");
        jdkArtifactMap.put("product", "jdk");

        // Select the correct release build number for each version
        // *UPDATE THIS* after each release
        switch(version) {
            case 19:
                build = 36;
                break;
            case 20:
                build = 29;
                break;
            case 21:
                build = 35;
                break;
            default:
                throw new RuntimeException("Unsupported JDK version " + version);
        }
        jdkArtifactMap.put("version", version);
        jdkArtifactMap.put("build_number", build);

        // Get correct file name for architecture
        if (Platform.isX64()) {
            architecture = "x64";
        } else if (Platform.isAArch64()) {
            architecture = "aarch64";
        } else {
            throw new RuntimeException("Unsupported architecture " + Platform.getOsArch());
        }

        // File name is bundles/<os>-<architecture>/jdk-<version>_<os>-<architecture>_bin.<extension>
        // Ex: bundles/linux-x64/jdk-19_linux-x64_bin.tar.gz
        if (Platform.isWindows()) {
            jdkArtifactMap.put("file", "bundles/windows-" + architecture + "/jdk-" + version + "_windows-" + architecture + "_bin.zip");
            return fetchJDK(jdkArtifactMap, version);
        } else if (Platform.isOSX()) {
            jdkArtifactMap.put("file", "bundles/macos-" + architecture + "/jdk-" + version + "_macos-" + architecture + "_bin.tar.gz");
            return fetchJDK(jdkArtifactMap, version) +  ".jdk" + FS + "Contents" + FS + "Home";
        } else if (Platform.isLinux()) {
            jdkArtifactMap.put("file", "bundles/linux-" + architecture + "/jdk-" + version + "_linux-" + architecture + "_bin.tar.gz");
            return fetchJDK(jdkArtifactMap, version);
        } else {
            throw new RuntimeException("Unsupported operating system " + Platform.getOsName());
        }
    }

    // Fetch JDK artifact
    private static String fetchJDK(HashMap<String, Object> jdkArtifactMap, int version) {
        try {
            String path = null;
            path = ArtifactResolver.resolve("jdk", jdkArtifactMap, true) + "/jdk-" + version;
            System.out.println("Boot JDK path: " + path);
            return path;
        } catch (ArtifactResolverException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("Fetch artifact failed: "
                    + "\nPlease make sure the artifact is available.", e);
        }
    }
}
