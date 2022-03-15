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
 * @bug 8282633
 * @summary jarsigner should display the named curve to better explain why
 *          an EC key is disabled or will be disabled.
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DisableCurveTest {
    private static final String JAVA_SECURITY_FILE = "java.security";

    public static void main(String[] args) throws Exception{
        SecurityTools.keytool("-keystore ks -storepass changeit " +
                "-genkeypair -keyalg EC -alias ca -dname CN=CA " +
                "-ext bc:c")
                .shouldHaveExitValue(0);

        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."), Path.of("ks"));

        Files.writeString(Files.createFile(Paths.get(JAVA_SECURITY_FILE)),
                "jdk.jar.disabledAlgorithms=secp256r1\n" +
                "jdk.certpath.disabledAlgorithms=secp256r1\n");

        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                "-signedjar signeda.jar -verbose " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " a.jar ca")
                .shouldContain(">>> Signer")
                .shouldContain("Signature algorithm: SHA256withECDSA, 256-bit EC (secp256r1) key (disabled)")
                .shouldContain("Warning:")
                .shouldContain("The EC (secp256r1) signing key has a keysize of 256 which is considered a security risk and is disabled")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-verify signeda.jar " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " -keystore ks -storepass changeit -verbose -debug")
                .shouldContain("- Signed by")
                .shouldContain("Signature algorithm: SHA256withECDSA, 256-bit EC (secp256r1) key (disabled)")
                .shouldContain("WARNING: The jar will be treated as unsigned")
                .shouldHaveExitValue(0);

        Files.deleteIfExists(Paths.get(JAVA_SECURITY_FILE));
        Files.writeString(Files.createFile(Paths.get(JAVA_SECURITY_FILE)),
                "jdk.security.legacyAlgorithms=secp256r1\n");

        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                "-signedjar signeda.jar -verbose " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " a.jar ca")
                .shouldContain(">>> Signer")
                .shouldContain("Signature algorithm: SHA256withECDSA, 256-bit EC (secp256r1) key (weak)")
                .shouldContain("Warning:")
                .shouldContain("The EC (secp256r1) signing key has a keysize of 256 which is considered a security risk. This key size will be disabled in a future update")
                .shouldHaveExitValue(0);

        SecurityTools.jarsigner("-verify signeda.jar " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE +
                " -keystore ks -storepass changeit -verbose -debug")
                .shouldContain("- Signed by")
                .shouldContain("Signature algorithm: SHA256withECDSA, 256-bit EC (secp256r1) key (weak)")
                .shouldContain("jar verified")
                .shouldContain("The EC (secp256r1) signing key has a keysize of 256 which is considered a security risk. This key size will be disabled in a future update")
                .shouldHaveExitValue(0);
    }
}
