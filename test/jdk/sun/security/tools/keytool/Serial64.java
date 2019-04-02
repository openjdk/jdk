/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8221257
 * @summary Improve serial number generation mechanism for keytool -gencert
 * @library /test/lib
 * @key randomness
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class Serial64 {

    static List<BigInteger> numbers = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        // 10 Self-signed certs and issued certs
        genkeypair("ca");
        genkeypair("user");
        for (int i = 0; i < 8; i++) {
            gencert("ca", "user");
        }

        numbers.forEach(b -> System.out.println(b.toString(16)));

        // Must be positive, therefore never zero.
        Asserts.assertTrue(numbers.stream()
                .allMatch(b -> b.signum() == 1));

        // At least one should be 64 bit. There is a chance of
        // 2^-10 this would fail.
        Asserts.assertTrue(numbers.stream()
                .anyMatch(b -> b.bitLength() == 64));
    }

    static OutputAnalyzer keytool(String s) throws Exception {
        return SecurityTools.keytool(
                "-storepass changeit -keypass changeit "
                        + "-keystore ks -keyalg rsa " + s);
    }

    static void genkeypair(String a) throws Exception {
        keytool("-genkeypair -alias " + a + " -dname CN=" + a)
                .shouldHaveExitValue(0);
        numbers.add(((X509Certificate)KeyStore.getInstance(
                new File("ks"), "changeit".toCharArray())
                    .getCertificate(a)).getSerialNumber());
    }

    static void gencert(String signer, String owner)
            throws Exception {
        keytool("-certreq -alias " + owner + " -file req")
                .shouldHaveExitValue(0);
        keytool("-gencert -alias " + signer + " -infile req -outfile cert")
                .shouldHaveExitValue(0);
        try (FileInputStream fis = new FileInputStream("cert")) {
            numbers.add(((X509Certificate)CertificateFactory.getInstance("X.509")
                    .generateCertificate(fis)).getSerialNumber());
        }
    }
}
