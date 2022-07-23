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

/*
 * @test
 * @bug 8277474 8283665
 * @summary jarsigner -verify should check if the algorithm parameters of
 *          its signature algorithm use disabled or legacy algorithms
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CheckAlgParams {
    private static final String JAVA_SECURITY_FILE = "java.security";

    public static void main(String[] args) throws Exception{

        SecurityTools.keytool("-keystore ks -storepass changeit " +
                "-genkeypair -keyalg RSASSA-PSS -alias ca -dname CN=CA " +
                "-ext bc:c")
                .shouldHaveExitValue(0);

        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."), Path.of("ks"));

        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                "-signedjar signeda.jar " +
                "-verbose" +
                " a.jar ca")
                .shouldHaveExitValue(0);

        Files.writeString(Files.createFile(Paths.get(JAVA_SECURITY_FILE)),
                "jdk.jar.disabledAlgorithms=SHA384\n" +
                "jdk.security.legacyAlgorithms=\n");

        SecurityTools.jarsigner("-verify signeda.jar " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " -keystore ks -storepass changeit -verbose -debug")
                .shouldMatch("Digest algorithm: SHA-384.*(disabled)")
                .shouldMatch("Signature algorithm: RSASSA-PSS using PSSParameterSpec.*hashAlgorithm=SHA-384.*(disabled)")
                .shouldContain("The jar will be treated as unsigned")
                .shouldHaveExitValue(0);

        Files.deleteIfExists(Paths.get(JAVA_SECURITY_FILE));
        Files.writeString(Files.createFile(Paths.get(JAVA_SECURITY_FILE)),
                "jdk.jar.disabledAlgorithms=\n" +
                "jdk.security.legacyAlgorithms=SHA384\n");

        SecurityTools.jarsigner("-verify signeda.jar " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " -keystore ks -storepass changeit -verbose -debug")
                .shouldMatch("Digest algorithm: SHA-384.*(weak)")
                .shouldMatch("Signature algorithm: RSASSA-PSS using PSSParameterSpec.*hashAlgorithm=SHA-384.*(weak)")
                .shouldNotContain("The jar will be treated as unsigned")
                .shouldHaveExitValue(0);
    }
}
