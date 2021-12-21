/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8279066
 * @library /test/lib
 * @summary Still see private key entries without certificates in a PKCS12 keystore
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;

import java.io.File;
import java.security.KeyStore;

public class ZeroCertificates {
    public static void main(String[] args) throws Exception {

        SecurityTools.keytool("-keystore tmp -storepass changeit -genkeypair " +
                        "-alias certone -dname CN=A -keyalg EC")
                .shouldHaveExitValue(0);
        SecurityTools.keytool("-keystore tmp -storepass changeit -exportcert " +
                        "-alias certone -file cert")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit -genkeypair " +
                        "-alias privateone -dname CN=A -keyalg EC")
                .shouldHaveExitValue(0);
        SecurityTools.keytool("-keystore ks -storepass changeit -genseckey " +
                        "-alias secretone -keyalg AES -keysize 128")
                .shouldHaveExitValue(0);
        SecurityTools.keytool("-keystore ks -storepass changeit -importcert " +
                        "-alias certone -file cert -noprompt")
                .shouldHaveExitValue(0);

        SecurityTools.keytool("-keystore ks -storepass changeit -list")
                .shouldHaveExitValue(0)
                .shouldContain("Your keystore contains 3 entries")
                .shouldContain("certone")
                .shouldContain("privateone")
                .shouldContain("secretone");

        // PrivateKeyEntry and TrustedCertificateEntry will not show.
        // SecretKeyEntry is still there.
        SecurityTools.keytool("-keystore ks -list")
                .shouldHaveExitValue(0)
                .shouldContain("Your keystore contains 1 entry")
                .shouldNotContain("certone")
                .shouldNotContain("privateone")
                .shouldContain("secretone");

        KeyStore ks;

        ks = KeyStore.getInstance(new File("ks"), "changeit".toCharArray());
        Asserts.assertEQ(ks.size(), 3);
        Asserts.assertTrue(ks.containsAlias("secretone"));
        Asserts.assertTrue(ks.containsAlias("privateone"));
        Asserts.assertTrue(ks.containsAlias("certone"));

        ks = KeyStore.getInstance(new File("ks"), (char[])null);
        Asserts.assertEQ(ks.size(), 1);
        Asserts.assertTrue(ks.containsAlias("secretone"));
        Asserts.assertFalse(ks.containsAlias("privateone"));
        Asserts.assertFalse(ks.containsAlias("certone"));
    }
}
