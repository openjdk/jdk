/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/*
 * @test
 * @summary Throw error if default java.security file is missing
 * @bug 8155246 8292297 8292177 8281658
 * @library /test/lib
 * @run main ConfigFileTest
 */
public class ConfigFileTest {

    private static final String EXPECTED_DEBUG_OUTPUT =
        "Initial security property: crypto.policy=unlimited";

    private static final String UNEXPECTED_DEBUG_OUTPUT =
            "Initial security property: postInitTest=shouldNotRecord";

    private static boolean overrideDetected = false;

    private static Path COPY_JDK_DIR = Path.of("./jdk-8155246-tmpdir");
    private static Path COPIED_JAVA = COPY_JDK_DIR.resolve("bin", "java");

    public static void main(String[] args) throws Exception {
        Path copyJdkDir = Path.of("./jdk-8155246-tmpdir");
        Path copiedJava = Optional.of(
                        Path.of(copyJdkDir.toString(), "bin", "java"))
                .orElseThrow(() -> new RuntimeException("Unable to locate new JDK")
                );

        if (args.length == 1) {
            // set up is complete. Run code to exercise loading of java.security
            Provider[] provs = Security.getProviders();
            Security.setProperty("postInitTest", "shouldNotRecord");
            System.out.println(Arrays.toString(provs) + "NumProviders: " + provs.length);
        } else {
            Files.createDirectory(copyJdkDir);
            Path jdkTestDir = Path.of(Optional.of(System.getProperty("test.jdk"))
                            .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
            );

            copyJDK(jdkTestDir, copyJdkDir);
            String extraPropsFile = Path.of(System.getProperty("test.src"), "override.props").toString();

            // sanity test -XshowSettings:security option
            exerciseShowSettingsSecurity(buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-XshowSettings:security", "ConfigFileTest", "runner"));

            // exercise some debug flags while we're here
            // regular JDK install - should expect success
            exerciseSecurity(0, "java",
                    buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all", "ConfigFileTest", "runner"));

            // given an overriding security conf file that doesn't exist, we shouldn't
            // overwrite the properties from original/master security conf file
            exerciseSecurity(0, "SUN version",
                    buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile + "badFileName",
                    "ConfigFileTest", "runner"));

            // test JDK launch with customized properties file
            exerciseSecurity(0, "NumProviders: 6",
                    buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile,
                    "ConfigFileTest", "runner"));

            // delete the master conf file
            Files.delete(Path.of(copyJdkDir.toString(), "conf",
                    "security","java.security"));

            // launch JDK without java.security file being present or specified
            exerciseSecurity(1, "Error loading java.security file",
                    buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "ConfigFileTest", "runner"));

            // test the override functionality also. Should not be allowed since
            // "security.overridePropertiesFile=true" Security property is missing.
            exerciseSecurity(1, "Error loading java.security file",
                    buildCommand("-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file:///" + extraPropsFile, "ConfigFileTest", "runner"));

            if (!overrideDetected) {
                throw new RuntimeException("Override scenario not seen");
            }
        }
    }

    private static ProcessBuilder buildCommand(String... command) {
        ArrayList<String> args = new ArrayList<>();
        args.add(COPIED_JAVA.toString());
        Collections.addAll(args, Utils.prependTestJavaOpts(command));
        return new ProcessBuilder(args);
    }

    private static void exerciseSecurity(int exitCode, String output, ProcessBuilder process) throws Exception {
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
    private static void exerciseShowSettingsSecurity(ProcessBuilder process) throws Exception {
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
}
