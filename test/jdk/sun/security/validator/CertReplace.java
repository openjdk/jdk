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

/*
 * This test is called by certreplace.sh
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
import jdk.test.lib.security.KeyStoreUtils;
import sun.security.validator.Validator;

/*
 * @test id=certreplace
 * @bug 6948803
 * @summary CertPath validation regression caused by SHA1 replacement root and MD2 disable feature
 * @library /test/lib
 * @modules java.base/sun.security.validator
 *
 * @run main/othervm CertReplace certreplace.jks certreplace.certs
 */

/*
 * @test id=samedn
 * @bug 6958869
 * @summary Regression: PKIXValidator fails when multiple trust anchors have same dn
 * @library /test/lib
 * @modules java.base/sun.security.validator
 *
 * @run main/othervm CertReplace samedn.jks samedn1.certs
 * @run main/othervm CertReplace samedn.jks samedn2.certs
 */

public class CertReplace {

    private static final String SAMEDN_JKS = "samedn.jks";
    private static final String CERTREPLACE_JKS = "certreplace.jks";
    private static final String PASSWORD = "changeit";

    /**
     * This method creates certs for the Cert Replace test
     *
     * @throws Exception
     */
    private static void certReplace() throws Exception {

        final String intAliase = "int";
        final String userAlias = "user";
        final String caAlias = "ca";

        final String certplaceCerts = "certreplace.certs";

        final String ktBaseParameters = "-storepass " + PASSWORD + " " +
                                        "-keypass " + PASSWORD + " " +
                                        "-keystore " + CERTREPLACE_JKS + " " +
                                        "-keyalg rsa ";

        final Path keystoreFilePath = Paths.get(CERTREPLACE_JKS);
        Files.deleteIfExists(keystoreFilePath);

        // 1. Generate 3 aliases in a keystore: ca, int, user
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + caAlias + " -dname CN=CA -keyalg rsa -sigalg md2withrsa -ext bc");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + intAliase + " -dname CN=Int -keyalg rsa");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + userAlias + " -dname CN=User -keyalg rsa");

        final KeyStore keyStore = KeyStoreUtils.loadKeyStore(CERTREPLACE_JKS, PASSWORD);

        // 2. Signing: ca -> int -> user
        final String intReqFile = intAliase + ".req";
        final String intCertFileName = intAliase + ".cert";

        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias " + intAliase + " -file " + intReqFile);
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias " + caAlias + " -ext bc -infile " + intReqFile + " " +
                              "-outfile " + intCertFileName);

        //putting the certificate in the keystore
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
        final Certificate[] certs = new Certificate[]{
                certificateFactory.generateCertificate(
                        new FileInputStream(intCertFileName)
                )
        };
        final PrivateKey privateKey = (PrivateKey) keyStore.getKey(intAliase, PASSWORD.toCharArray());
        keyStore.setKeyEntry(intAliase, privateKey, PASSWORD.toCharArray(), certs);
        keyStore.store(new FileOutputStream(CERTREPLACE_JKS), PASSWORD.toCharArray());


        final String userReqFile = userAlias + ".req";
        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias " + userAlias + " -file " + userReqFile);
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias " + intAliase + " " +
                              "-infile " + userReqFile + " " +
                              "-outfile " + certplaceCerts); // this will create certreplace.certs which is later appended

        // 3. Create the certchain file
        final Path certPath = Paths.get(certplaceCerts);

        Files.write(certPath, Files.readAllBytes(Path.of(intCertFileName)), StandardOpenOption.APPEND);

        final String outputCa = SecurityTools.keytool(ktBaseParameters +
                                                      "-export -rfc -alias " + caAlias).getOutput();
        Files.write(certPath, outputCa.getBytes(), StandardOpenOption.APPEND);

        // 4. Upgrade ca from MD2withRSA to SHA256withRSA, remove other aliases and make this keystore the cacerts file
        SecurityTools.keytool(ktBaseParameters +
                              "-selfcert -alias " + caAlias);
        keyStore.deleteEntry(intAliase);
        keyStore.deleteEntry(userAlias);
    }

    /**
     * This method creates certs for the Same DN test
     *
     * @throws Exception
     */
    private static void sameDn() throws Exception {

        final String ca1Alias = "ca1";
        final String ca2Alias = "ca2";
        final String userAlias = "user";

        final String ktBaseParameters = "-storepass " + PASSWORD + " " +
                                        "-keypass " + PASSWORD + " " +
                                        "-keystore " + SAMEDN_JKS + " " +
                                        "-keyalg rsa ";

        final Path keystoreFilePath = Paths.get(SAMEDN_JKS);
        Files.deleteIfExists(keystoreFilePath);

        // 1. Generate 3 aliases in a keystore: ca1, ca2, user. The CAs' startdate
        // is set to one year ago so that they are expired now
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + ca1Alias + " -dname CN=CA -keyalg rsa " +
                              "-sigalg md5withrsa -ext bc -startdate -1y");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + ca2Alias + " -dname CN=CA -keyalg rsa " +
                              "-sigalg sha1withrsa -ext bc -startdate -1y");
        SecurityTools.keytool(ktBaseParameters +
                              "-genkeypair -alias " + userAlias + " -dname CN=User -keyalg rsa");

        final KeyStore keyStore = KeyStoreUtils.loadKeyStore(SAMEDN_JKS, PASSWORD);

        // 2. Signing: ca -> user. The startdate is set to 1 minute in the past to ensure the certificate
        // is valid at the time of validation and to prevent any issues with timing discrepancies
        // Automatically saves the certs to the certs files

        final String userReqFile = userAlias + ".req";
        SecurityTools.keytool(ktBaseParameters +
                              "-certreq -alias " + userAlias + " -file " + userReqFile);
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias " + ca1Alias + " " +
                              "-startdate -1M -infile " + userReqFile + " -outfile samedn1.certs");
        SecurityTools.keytool(ktBaseParameters +
                              "-gencert -rfc -alias " + ca2Alias + " " +
                              "-startdate -1M -infile " + userReqFile + " -outfile samedn2.certs");

        // 3. Remove user for cacerts
        keyStore.deleteEntry(userAlias);
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
        ks.load(new FileInputStream(args[0]), "changeit".toCharArray());
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
        for (Certificate c: cf.generateCertificates(
                new FileInputStream(chain))) {
            list.add((X509Certificate)c);
        }
        return (X509Certificate[]) list.toArray(new X509Certificate[0]);
    }
}
