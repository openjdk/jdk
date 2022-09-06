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
 * @bug 8255552 8286090
 * @summary Test keytool commands associated with secret key entries which use weak algorithms
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.nio.file.Files;
import java.nio.file.Paths;

public class WeakSecretKeyTest {

    private static final String JAVA_SECURITY_FILE = "java.security";

    public static void main(String[] args) throws Exception {
        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg DESede -alias des3key")
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses the DESede algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg DES -alias deskey")
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses the DES algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg AES -alias aeskey -keysize 256")
                .shouldNotContain("Warning")
                .shouldNotMatch("The generated secret key uses the AES algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg RC2 -alias rc2key -keysize 128")
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses the RC2 algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg RC4 -alias rc4key -keysize 1024")
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses the ARCFOUR algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-list -v")
                .shouldContain("Warning")
                .shouldMatch("<des3key> uses the DESede algorithm.*considered a security risk")
                .shouldMatch("<deskey> uses the DES algorithm.*considered a security risk")
                .shouldNotMatch("<aeskey> uses the AES algorithm.*considered a security risk")
                .shouldMatch("<rc2key> uses the RC2 algorithm.*considered a security risk")
                .shouldMatch("<rc4key> uses the ARCFOUR algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.setResponse("changeit", "changeit");
        SecurityTools.keytool("-importkeystore -srckeystore ks.p12 -destkeystore ks.new " +
                "-deststoretype pkcs12 -srcstorepass changeit ")
                .shouldContain("Warning")
                .shouldMatch("<des3key> uses the DESede algorithm.*considered a security risk")
                .shouldMatch("<deskey> uses the DES algorithm.*considered a security risk")
                .shouldMatch("<rc2key> uses the RC2 algorithm.*considered a security risk")
                .shouldMatch("<rc4key> uses the ARCFOUR algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks.new -storepass changeit " +
                "-list -v")
                .shouldContain("Warning")
                .shouldMatch("<des3key> uses the DESede algorithm.*considered a security risk")
                .shouldMatch("<deskey> uses the DES algorithm.*considered a security risk")
                .shouldMatch("<rc2key> uses the RC2 algorithm.*considered a security risk")
                .shouldMatch("<rc4key> uses the ARCFOUR algorithm.*considered a security risk")
                .shouldHaveExitValue(0);

        Files.writeString(Files.createFile(Paths.get(JAVA_SECURITY_FILE)),
                "jdk.security.legacyAlgorithms=AES keySize < 256\n");

        SecurityTools.keytool("-keystore ks.p12 -storepass changeit " +
                "-genseckey -keyalg AES -alias aeskey1 -keysize 128 " +
                "-J-Djava.security.properties=" +
                JAVA_SECURITY_FILE)
                .shouldContain("Warning")
                .shouldMatch("The generated secret key uses a 128-bit AES key.*considered a security risk")
                .shouldHaveExitValue(0);
    }
}
