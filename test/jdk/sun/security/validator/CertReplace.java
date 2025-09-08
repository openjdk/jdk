/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.SecurityTools;
import jdk.test.lib.security.CertUtils;
import jdk.test.lib.security.KeyStoreUtils;
import sun.security.validator.Validator;

/*
 * @test id=certreplace
 * @bug 6948803
 * @summary CertPath validation regression caused by SHA1 replacement root and MD2 disable feature
 * @library /test/lib
 * @modules java.base/sun.security.validator
 *
 * @run main CertReplace certreplace.jks certreplace.certs
 */

/*
 * @test id=samedn
 * @bug 6958869
 * @summary Regression: PKIXValidator fails when multiple trust anchors have same dn
 * @library /test/lib
 * @modules java.base/sun.security.validator
 *
 * @run main CertReplace samedn.jks samedn1.certs
 * @run main CertReplace samedn.jks samedn2.certs
 */

public class CertReplace {

    private static final String SAMEDN_JKS = "samedn.jks";
    private static final String CERTREPLACE_JKS = "certreplace.jks";
    private static final String PASSWORD = "changeit";
    private static final char[] PASSWORD_CHAR_ARR = PASSWORD.toCharArray();

    /**
     * This method creates certs for the Cert Replace test
     *
     * @throws Exception
     */
    private static void certReplace() throws Exception {

        final String ktBaseParameters = "-storepass " + PASSWORD + " " +
                                        "-keypass " + PASSWORD + " " +
                                        "-keystore " + CERTREPLACE_JKS + " " +
                                        "-keyalg rsa ";

        final Path keystoreFilePath = Paths.get(CERTREPLACE_JKS);
        Files.deleteIfExists(keystoreFilePath);

        // 1. Generate 3 aliases in a keystore: ca, int, user
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias ca -dname CN=CA -keyalg rsa -sigalg md2withrsa -ext bc");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias int -dname CN=Int -keyalg rsa");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias user -dname CN=User -keyalg rsa");

        final KeyStore keyStore = KeyStoreUtils.loadKeyStore(CERTREPLACE_JKS, PASSWORD);

        // 2. Signing: ca -> int -> user

        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias int -file int.req");
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias ca -ext bc -infile int.req " +
                              "-outfile int.cert");

        //putting the certificate in the keystore
        try (final FileInputStream certInputStream = new FileInputStream("int.cert")) {
            final Certificate[] certs = new Certificate[]{
                    CertUtils.getCertFromStream(
                            certInputStream
                    )
            };

            final PrivateKey privateKey = (PrivateKey) keyStore.getKey("int", PASSWORD_CHAR_ARR);
            keyStore.setKeyEntry("int", privateKey, PASSWORD_CHAR_ARR, certs);
            keyStore.store(new FileOutputStream(CERTREPLACE_JKS), PASSWORD_CHAR_ARR);
        }

        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias user -file user.req");
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias int " +
                              "-infile user.req " +
                              "-outfile certreplace.certs"); // this will create certreplace.certs which is later appended

        // 3. Create the certchain file
        final Path certPath = Paths.get("certreplace.certs");

        Files.write(certPath, Files.readAllBytes(Path.of("int.cert")), StandardOpenOption.APPEND);

        final String outputCa = SecurityTools.keytool(ktBaseParameters +
                                                      "-export -rfc -alias ca").getOutput();
        Files.write(certPath, outputCa.getBytes(), StandardOpenOption.APPEND);

        // 4. Upgrade ca from MD2withRSA to SHA256withRSA, remove other aliases and make this keystore the cacerts file
        keyStore.deleteEntry("int");
        keyStore.deleteEntry("user");
        keyStore.store(new FileOutputStream(CERTREPLACE_JKS), PASSWORD_CHAR_ARR);

        SecurityTools.keytool(ktBaseParameters +
                              "-selfcert -alias ca");
    }

    /**
     * This method creates certs for the Same DN test
     *
     * @throws Exception
     */
    private static void sameDn() throws Exception {

        final String ktBaseParameters = "-storepass " + PASSWORD + " " +
                                        "-keypass " + PASSWORD + " " +
                                        "-keystore " + SAMEDN_JKS + " " +
                                        "-keyalg rsa ";

        final Path keystoreFilePath = Paths.get(SAMEDN_JKS);
        Files.deleteIfExists(keystoreFilePath);

        // 1. Generate 3 aliases in a keystore: ca1, ca2, user. The CAs' startdate
        // is set to one year ago so that they are expired now
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias ca1 -dname CN=CA -keyalg rsa " +
                              "-sigalg md5withrsa -ext bc -startdate -1y");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias ca2 -dname CN=CA -keyalg rsa " +
                              "-sigalg sha1withrsa -ext bc -startdate -1y");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias user -dname CN=User -keyalg rsa");

        // 2. Signing: ca -> user. The startdate is set to 1 minute in the past to ensure the certificate
        // is valid at the time of validation and to prevent any issues with timing discrepancies
        // Automatically saves the certs to the certs files

        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias user -file user.req");
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias ca1 " +
                              "-startdate -1M -infile user.req -outfile samedn1.certs");
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias ca2 " +
                              "-startdate -1M -infile user.req -outfile samedn2.certs");

        // 3. Remove user for cacerts
        final KeyStore keyStore = KeyStoreUtils.loadKeyStore(SAMEDN_JKS, PASSWORD);
        keyStore.deleteEntry("user");
        keyStore.store(new FileOutputStream(CERTREPLACE_JKS), PASSWORD_CHAR_ARR);
    }

    /**
     * @param args {cacerts keystore, cert chain}
     */
    public static void main(String[] args) throws Exception {

        if (args[0].equals(CERTREPLACE_JKS)) {
            certReplace();
        } else if (args[0].equals(SAMEDN_JKS)) {
            sameDn();
        } else {
            throw new RuntimeException("Not recognised test " + args[0]);
        }

        KeyStore ks = KeyStore.getInstance("JKS");
        try (final FileInputStream certInputStream = new FileInputStream(args[0])) {
            ks.load(certInputStream, PASSWORD_CHAR_ARR);
        }
        Validator v = Validator.getInstance
            (Validator.TYPE_PKIX, Validator.VAR_GENERIC, ks);
        X509Certificate[] chain = createPath(args[1]);
        System.out.println("Chain: ");
        for (X509Certificate c: v.validate(chain)) {
            System.out.println("   " + c.getSubjectX500Principal() +
                    " issued by " + c.getIssuerX500Principal());
        }
    }

    public static X509Certificate[] createPath(String chain) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List list = new ArrayList();
        try (final FileInputStream certInputStream = new FileInputStream(chain)) {
            for (Certificate c : cf.generateCertificates(certInputStream)) {
                list.add((X509Certificate) c);
            }
        }
        return (X509Certificate[]) list.toArray(new X509Certificate[0]);
    }
}
