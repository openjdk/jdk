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
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

import java.security.Security;
import java.util.Arrays;
import java.util.Optional;

/*
 * @test
 * @summary Throw error if default java.security file is missing
 * @bug 8155246
 * @library /test/lib
 * @run main ConfigFileTest
 */
public class ConfigFileTest {

    public static void main(String[] args) throws Exception {
        Path copyJdkDir = Path.of("./jdk-8155246-tmpdir");
        Path copiedJava = Optional.of(
                        Path.of(copyJdkDir.toString(), "bin", "java"))
                .orElseThrow(() -> new RuntimeException("Unable to locate new JDK")
                );

        if (args.length == 1) {
            // set up is complete. Run code to exercise loading of java.security
            System.out.println(Arrays.toString(Security.getProviders()));
        } else {
            Files.createDirectory(copyJdkDir);
            Path jdkTestDir = Path.of(Optional.of(System.getProperty("test.jdk"))
                            .orElseThrow(() -> new RuntimeException("Couldn't load JDK Test Dir"))
            );

            copyJDKMinusJavaSecurity(jdkTestDir, copyJdkDir);
            String extraPropsFile = Path.of(System.getProperty("test.src"), "override.props").toString();

            // exercise some debug flags while we're here
            // launch JDK without java.security file being present or specified
            exerciseSecurity(copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all", "ConfigFileTest", "runner");

            // test the override functionality also. Should not be allowed since
            // "security.overridePropertiesFile=true" Security property is missing.
            exerciseSecurity(copiedJava.toString(), "-cp", System.getProperty("test.classes"),
                    "-Djava.security.debug=all", "-Djavax.net.debug=all",
                    "-Djava.security.properties==file://" + extraPropsFile, "ConfigFileTest", "runner");
        }
    }

    private static void exerciseSecurity(String... args) throws Exception {
        ProcessBuilder process = new ProcessBuilder(args);
        OutputAnalyzer oa = ProcessTools.executeProcess(process);
        oa.shouldHaveExitValue(1).shouldContain("java.security file missing");
    }

    private static void copyJDKMinusJavaSecurity(Path src, Path dst) throws Exception {
        Files.walk(src)
            .skip(1)
            .filter(p -> !p.toString().endsWith("java.security"))
            .forEach(file -> {
                try {
                    Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
    }
}