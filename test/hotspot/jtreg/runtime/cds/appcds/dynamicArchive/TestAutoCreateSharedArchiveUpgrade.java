/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds
 * @library /test/lib
 * @compile -source 1.8 -target 1.8 ../test-classes/HelloJDK8.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar Hello.jar HelloJDK8
 * @run driver TestAutoCreateSharedArchiveUpgrade
 */

import java.io.File;
import java.util.Properties;
import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.HashMap;

public class TestAutoCreateSharedArchiveUpgrade {

    static final Properties props = System.getProperties();

    // The JDK being tested
    private static final String TEST_JDK = System.getProperty("test.jdk", null);

    // If you're running this test manually, specify the location of a previous version of
    // the JDK using "jtreg -vmoption:-Dtest.previous.jdk=${JDK19_HOME} ..."
    private static final String PREV_JDK = System.getProperty("test.previous.jdk", null);

    // If you're unning this test using something like
    // "make test TEST=test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/TestAutoCreateSharedArchiveUpgrade.java",
    // the test.boot.jdk property is normally passed by make/RunTests.gmk
    // now it is pulled by the artifactory
    private static String BOOT_JDK;

    private static final String USER_DIR = System.getProperty("user.dir", ".");
    private static final String FS = System.getProperty("file.separator", "/");

    private static final String JAR = ClassFileInstaller.getJarPath("Hello.jar");
    private static final String JSA = USER_DIR + FS + "Hello.jsa";

    private static String oldJVM;
    private static String newJVM;

    public static void main(String[] args) throws Throwable {
        // Earliest testable version is 19
        int n = java.lang.Runtime.version().major() - 1;
        for (int i = 19; i < n; i++) {
            BOOT_JDK = fetchBootJDK(getOsId(), i);
            setupJVMs();
            doTest();
        }
    }

    static void setupJVMs() throws Throwable {
        if (TEST_JDK == null) {
            throw new RuntimeException("-Dtest.jdk should point to the JDK being tested");
        }

        newJVM = TEST_JDK + FS + "bin" + FS + "java";

        if (PREV_JDK != null) {
            oldJVM = PREV_JDK + FS + "bin" + FS + "java";
        } else if (BOOT_JDK != null) {
            oldJVM = BOOT_JDK + FS + "bin" + FS + "java";
        } else {
            throw new RuntimeException("Use -Dtest.previous.jdk or -Dtest.boot.jdk to specify a " +
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
        assertJSAVersionMismatch(output);
        assertCreatedJSA(output);

        output = run(oldJVM);
        assertUsedJSA(output);

        // NEW JDK -- should reject the JSA created by OLD JDK, and create its own
        output = run(newJVM);
        assertJSAVersionMismatch(output);
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

    static void assertJSAVersionMismatch(OutputAnalyzer output) {
        output.shouldContain("does not match the required version");
    }

    static void assertUsedJSA(OutputAnalyzer output) {
        output.shouldContain("Mapped dynamic region #0");
    }

    // Fetch JDK artifact depending on platform
    private static String fetchBootJDK(String osID, int version) {
        int build;
        HashMap<String, Object> jdkArtifactMap = new HashMap<>();
        jdkArtifactMap.put("server", "jpg");
        jdkArtifactMap.put("product", "jdk");

        switch(version) {
            case 19:
                build = 36;
                break;
            case 20:
                build = 29;
                break;
            default:
                build = 0;
                break;
        }

        switch (osID) {
        case "Windows-amd64-64":
            jdkArtifactMap.put("version", version);
            jdkArtifactMap.put("build_number", build);
            jdkArtifactMap.put("file", "bundles/windows-x64/jdk-" + version + "_windows-x64_bin.zip");
            return fetchBootJDK(jdkArtifactMap, version);

        case "MacOSX-x86_64-64":
            jdkArtifactMap.put("version", version);
            jdkArtifactMap.put("build_number", build);
            jdkArtifactMap.put("file", "bundles/macos-x64/jdk-" + version + "_macos-x64_bin.tar.gz");
            return fetchBootJDK(jdkArtifactMap, version);
        case "Linux-amd64-64":
            jdkArtifactMap.put("version", version);
            jdkArtifactMap.put("build_number", build);
            jdkArtifactMap.put("file", "bundles/linux-x64/jdk-" + version + "_linux-x64_bin.tar.gz");
            return fetchBootJDK(jdkArtifactMap, version);

        default:
            return null;
        }
    }

    // Fetch JDK version from artifactory
    // private static String fetchBootJDK(Class<?> clazz, HashMap<String, Object> jdkArtifactMap, int version) {
    private static String fetchBootJDK(HashMap<String, Object> jdkArtifactMap, int version) {
        String path = null;
        try {
            path = ArtifactResolver.resolve("jdk", jdkArtifactMap, true) + "/jdk-" + version;
            System.out.println("path: " + path);
        } catch (ArtifactResolverException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                System.out.println("Cannot resolve artifact, "
                        + "please check if JIB jar is present in classpath.");
            } else {
                throw new RuntimeException("Fetch artifact failed: "
                        + "\nPlease make sure the artifact is available.", e);
            }
        }
        return path;
    }

    private static String getOsId() {
        String osName = props.getProperty("os.name");
        if (osName.startsWith("Win")) {
            osName = "Windows";
        } else if (osName.equals("Mac OS X")) {
            osName = "MacOSX";
        }
        String osid = osName + "-" + props.getProperty("os.arch") + "-"
                + props.getProperty("sun.arch.data.model");
        return osid;
    }

    /*@Artifact(
            server = "jpg",
            product = "jdk",
            version = "19",
            build_number = "36",
            file = "bundles/linux-x64/jdk-19_linux-x64_bin.tar.gz",
            unpack = true)
    private static class LINUX_X64_19 { }

    @Artifact(
            server = "jpg",
            product = "jdk",
            version = "20",
            build_number = "29",
            file = "bundles/linux-x64/jdk-20_linux-x64_bin.tar.gz",
            unpack = true)
    private static class LINUX_X64_20 { }

    @Artifact(
            server = "jpg",
            product = "jdk",
            version = "19",
            build_number = "36",
            file = "bundles/macosx-x64/jdk-19_macosx-x64_bin.tar.gz",
            unpack = true)
    private static class MACOSX_X64_19 { }

    @Artifact(
            server = "jpg",
            product = "jdk",
            version = "20",
            build_number = "29",
            file = "bundles/macosx-x64/jdk-20_macosx-x64_bin.tar.gz",
            unpack = true)
    private static class MACOSX_X64_20 { }

    @Artifact(
            server = "jpg",
            product = "jdk",
            version = "19",
            build_number = "36",
            file = "bundles/windows-x64/jdk-19_windows-x64_bin.zip",
            unpack = true)
    private static class WINDOWS_X64_19 { }

    @Artifact(
            server = "jpg",
            product = "jdk",
            version = "20",
            build_number = "29",
            file = "bundles/windows-x64/jdk-20_windows-x64_bin.zip",
            unpack = true)
    private static class WINDOWS_X64_20 { }*/
}
