/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6405536
 * @summary Verify that we can parse ECPrivateKeys from PKCS#12 and use them
 * @author Andreas Sterbenz
 * @library ..
 * @library ../../../../java/security/testlibrary
 */

import java.io.*;
import java.util.*;

import java.security.*;
import java.security.interfaces.*;
import java.security.cert.*;
import java.security.cert.Certificate;

import javax.security.auth.x500.X500Principal;

public class ReadPKCS12 extends PKCS11Test {

    private final static boolean COPY = false;

    public static void main(String[] args) throws Exception {
        main(new ReadPKCS12());
    }

    public void main(Provider p) throws Exception {
        if (p.getService("Signature", "SHA1withECDSA") == null) {
            System.out.println("Provider does not support ECDSA, skipping...");
            return;
        }

        /*
         * PKCS11Test.main will remove this provider if needed
         */
        Providers.setAt(p, 1);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try {
            // undocumented way to clear the Sun internal certificate cache
            factory.generateCertificate(null);
        } catch (CertificateException e) {
            // ignore
        }

        KeyStore ks2;
        if (COPY) {
            ks2 = KeyStore.getInstance("JKS");
            InputStream in = new FileInputStream("keystore.old");
            ks2.load(in, "passphrase".toCharArray());
            in.close();
        }

        File dir = new File(BASE, "pkcs12");
        File closedDir = new File(CLOSED_BASE, "pkcs12");

        Map<String,char[]> passwords = new HashMap<String,char[]>();
        BufferedReader reader = new BufferedReader(new FileReader((new File(BASE, "p12passwords.txt"))));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if ((line.length() == 0) || line.startsWith("#")) {
                continue;
            }
            String[] s = line.split(" ");
            passwords.put(s[0], s[1].toCharArray());
        }
        reader.close();

        for (File file : concat(dir.listFiles(), closedDir.listFiles())) {
            String name = file.getName();
            if (file.isFile() == false) {
                continue;
            }
            System.out.println();
            System.out.println("Reading " + name + "...");

            char[] password = passwords.get(name);
            if (password == null) {
                password = passwords.get("*");
            }

            InputStream in = new FileInputStream(file);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password);
            in.close();
            List<String> aliases = Collections.list(ks.aliases());
            System.out.println("Aliases: " + aliases);

            for (String alias : aliases) {
                PrivateKey privateKey = (PrivateKey)ks.getKey(alias, password);
                Certificate[] certs = ks.getCertificateChain(alias);
                PublicKey publicKey = certs[0].getPublicKey();
                System.out.println("Certificates: " + certs.length);
                System.out.println(privateKey);
                System.out.println(publicKey);
                if (COPY) {
                    ks2.setKeyEntry(alias, privateKey, "passphrase".toCharArray(), certs);
                }

                verifyCerts(certs);

                Random random = new Random();
                byte[] data = new byte[1024];
                random.nextBytes(data);

                Signature s = Signature.getInstance("SHA1withECDSA");
                s.initSign(privateKey);
                s.update(data);
                byte[] sig = s.sign();

                s.initVerify(publicKey);
                s.update(data);
                if (s.verify(sig) == false) {
                    throw new Exception("Signature does not verify");
                }
                System.out.println("Verified public/private key match");
            }
        }

        if (COPY) {
            OutputStream out = new FileOutputStream("keystore.new");
            ks2.store(out, "passphrase".toCharArray());
            out.close();
        }

        System.out.println("OK");
    }

    private static void verifyCerts(Certificate[] certs) throws Exception {
        int n = certs.length;
        for (int i = 0; i < n - 1; i++) {
            X509Certificate cert = (X509Certificate)certs[i];
            X509Certificate issuer = (X509Certificate)certs[i + 1];
            if (cert.getIssuerX500Principal().equals(issuer.getSubjectX500Principal()) == false) {
                throw new Exception("Certificates do not chain");
            }
            cert.verify(issuer.getPublicKey());
            System.out.println("Verified: " + cert.getSubjectX500Principal());
        }
        X509Certificate last = (X509Certificate)certs[n - 1];
        // if self-signed, verify the final cert
        if (last.getIssuerX500Principal().equals(last.getSubjectX500Principal())) {
            last.verify(last.getPublicKey());
            System.out.println("Verified: " + last.getSubjectX500Principal());
        }
    }

}
