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
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Optional;

/*
 * @test
 * @summary Exercise use, absence and extension of java.security
 * @bug 8155246 8281658 8292177 8292297 8309330
 * @library /test/lib
 * @run main ConfigFileTest
 */
public class ConfigFileTest {

    private static final String EXPECTED_DEBUG_OUTPUT =
        "Initial security property: crypto.policy=unlimited";

    private static final String UNEXPECTED_DEBUG_OUTPUT =
            "Initial security property: postInitTest=shouldNotRecord";

    private static boolean overrideDetected = false;

    public static void main(String[] args) throws Exception {
        Path copyJdkDir = Path.of("./jdk-8155246-tmpdir");
        Path secPropDir = Path.of("./jdk-8309330-tmpdir");
        Path copiedJava = Optional.of(
                        Path.of(copyJdkDir.toString(), "bin", "java"))
                .orElseThrow(() -> new RuntimeException("Unable to locate new JDK")
                );
        Path javaSecurity = Path.of(copyJdkDir.toString(), "conf",
                                    "security","java.security");

        if (args.length == 1) {
            // set up is complete. Run code to exercise loading of java.security
            Provider[] provs = Security.getProviders();
            Security.setProperty("postInitTest", "shouldNotRecord");
            System.out.println(Arrays.toString(provs) + "NumProviders: " + provs.length);
            if ("propDir".equals(args[0])) {
                System.out.println("testProp3=" + Security.getProperty("testProp3"));
            }
            if ("propDirDisabled".equals(args[0])) {
                // In this configuration, the directory properties should not be present
                if (Security.getProperty("testProp3") != null) {
                    System.exit(1);
                }
            }
            if ("propDirNoHidden".equals(args[0])) {
                // Properties listed in hidden files should not be registered
                if (Security.getProperty("testProp4") != null) {
                    System.exit(1);
                }
            }
        } else {
            Files.createDirectory(copyJdkDir);
            Files.createDirectory(secPropDir);
            Path jdkTestDir = Path.of(Optional.of(System.getProperty("test.jdk"))
                            .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
            );

            copyJDK(jdkTestDir, copyJdkDir);
            populateSecPropDir(secPropDir);
            String extraPropsFile = Path.of(System.getProperty("test.src"), "override.props").toString();

            // sanity test -XshowSettings:security option
            exerciseShowSettingsSecurity(copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-XshowSettings:security", "ConfigFileTest", "runner");

            // exercise some debug flags while we're here
            // regular JDK install - should expect success
            exerciseSecurity(0, "java",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all", "ConfigFileTest", "runner");

            // given an overriding security conf file that doesn't exist, we shouldn't
            // overwrite the properties from original/master security conf file
            exerciseSecurity(0, "SUN version",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile + "badFileName",
                    "ConfigFileTest", "runner");

            // test JDK launch with customized properties file
            exerciseSecurity(0, "NumProviders: 6",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile,
                    "ConfigFileTest", "runner");

            // test JDK launch with customized properties dir on the command line
            exerciseSecurity(0, "testProp3=cherry",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.propertiesDir=" + secPropDir.toString(),
                    "ConfigFileTest", "propDir");

            // enable properties directory in java.security
            Files.writeString(javaSecurity,
                              "security.propertiesDir=" + secPropDir.toAbsolutePath().toString(),
                              StandardOpenOption.APPEND);

            // test JDK launch with customized properties dir in java.security
            exerciseSecurity(0, "testProp3=cherry",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "ConfigFileTest", "propDir");

            // test JDK launch with customized properties dir in java.security disabled
            exerciseSecurity(0, "SUN version",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.propertiesDir=",
                    "ConfigFileTest", "propDirDisabled");

            // test JDK launch with customized properties file overriding properties dir
            exerciseSecurity(0, "NumProviders: 6",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile,
                    "ConfigFileTest", "propDirDisabled");

            // test JDK launch with customized properties file appended to properties dir
            exerciseSecurity(0, "testProp3=cherry",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties=file:///" + extraPropsFile,
                    "ConfigFileTest", "propDir");

            // test that hidden property files are ignored
            addHiddenFile(secPropDir);
            exerciseSecurity(0, "testProp3=cherry",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties=file:///" + extraPropsFile,
                    "ConfigFileTest", "propDirNoHidden");

            // test that property files that occur lexicographically later
            // override properties in earlier files
            addOverrideFile(secPropDir);
            exerciseSecurity(0, "testProp3=cabbage",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties=file:///" + extraPropsFile,
                    "ConfigFileTest", "propDir");

            // delete the master conf file
            Files.delete(javaSecurity);

            // launch JDK without java.security file being present or specified
            exerciseSecurity(1, "Error loading java.security file",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "ConfigFileTest", "runner");

            // test the override functionality also. Should not be allowed since
            // "security.overridePropertiesFile=true" Security property is missing.
            exerciseSecurity(1, "Error loading java.security file",
                    copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile, "ConfigFileTest", "runner");

            if (!overrideDetected) {
                throw new RuntimeException("Override scenario not seen");
            }
        }
    }

    private static void exerciseSecurity(int exitCode, String output, String... args) throws Exception {
        ProcessBuilder process = new ProcessBuilder(args);
        OutputAnalyzer oa = ProcessTools.executeProcess(process);
        oa.shouldHaveExitValue(exitCode)
                .shouldContain(output);

        // extra checks on debug output
        if (exitCode != 1) {
            if (oa.getStderr().contains("overriding other security properties files!")) {
                overrideDetected = true;
                // master file is not in use - only provider properties are set in custom file
                oa.shouldContain("security.provider.2=SunRsaSign")
                        .shouldNotContain(EXPECTED_DEBUG_OUTPUT)
                        .shouldNotContain(UNEXPECTED_DEBUG_OUTPUT);
            } else {
                oa.shouldContain(EXPECTED_DEBUG_OUTPUT)
                        .shouldNotContain(UNEXPECTED_DEBUG_OUTPUT);
            }
        }
    }

    // exercise the -XshowSettings:security launcher
    private static void exerciseShowSettingsSecurity(String... args) throws Exception {
        ProcessBuilder process = new ProcessBuilder(args);
        OutputAnalyzer oa = ProcessTools.executeProcess(process);
        oa.shouldHaveExitValue(0)
                .shouldContain("Security properties:")
                .shouldContain("Security provider static configuration:")
                .shouldContain("Security TLS configuration");
    }

    private static void copyJDK(Path src, Path dst) throws Exception {
        Files.walk(src)
            .skip(1)
            .forEach(file -> {
                try {
                    Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
    }

    private static void populateSecPropDir(Path dir) throws Exception {
        Files.writeString(dir.resolve("01-testFile"), "testProp1=apple");
        Files.writeString(dir.resolve("02-testFile"), "testProp2=banana");
        Path extraDir = dir.resolve("03-testDir");
        Files.createDirectory(extraDir);
        Files.writeString(extraDir.resolve("extra"), "testProp3=cherry");
    }

    private static void addHiddenFile(Path dir) throws Exception {
        Files.writeString(dir.resolve(".04-testFile"), "testProp4=diamond");
    }

    private static void addOverrideFile(Path dir) throws Exception {
        Files.writeString(dir.resolve("05-testFile"), "testProp3=cabbage");
    }
}
