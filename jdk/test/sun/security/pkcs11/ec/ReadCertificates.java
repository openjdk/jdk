/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6405536 6414980 8051972
 * @summary Make sure that we can parse certificates using various named curves
 *   and verify their signatures
 * @author Andreas Sterbenz
 * @library ..
 * @library ../../../../java/security/testlibrary
 */

import java.io.*;
import java.util.*;

import java.security.cert.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.ECParameterSpec;

import javax.security.auth.x500.X500Principal;

public class ReadCertificates extends PKCS11Test {

    private static CertificateFactory factory;

    private static SecureRandom random;

    private static Collection<X509Certificate> readCertificates(File file) throws Exception {
        System.out.println("Loading " + file.getName() + "...");
        InputStream in = new FileInputStream(file);
        Collection<X509Certificate> certs = (Collection<X509Certificate>)factory.generateCertificates(in);
        in.close();
        return certs;
    }

    public static void main(String[] args) throws Exception {
        main(new ReadCertificates());
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

        random = new SecureRandom();
        factory = CertificateFactory.getInstance("X.509");
        try {
            // clear certificate cache in from a previous run with a different
            // provider (undocumented hack for the Sun provider)
            factory.generateCertificate(null);
        } catch (CertificateException e) {
            // ignore
        }
        Map<X500Principal,X509Certificate> certs = new LinkedHashMap<X500Principal,X509Certificate>();

        File dir = new File(BASE, "certs");
        File closedDir = new File(CLOSED_BASE, "certs");
        File[] files = concat(dir.listFiles(), closedDir.listFiles());
        Arrays.sort(files);
        for (File file : files) {
            if (file.isFile() == false) {
                continue;
            }
            Collection<X509Certificate> certList = readCertificates(file);
            for (X509Certificate cert : certList) {
                X509Certificate old = certs.put(cert.getSubjectX500Principal(), cert);
                if (old != null) {
                    System.out.println("Duplicate subject:");
                    System.out.println("Old Certificate: " + old);
                    System.out.println("New Certificate: " + cert);
                    throw new Exception(file.getPath());
                }
            }
        }
        System.out.println("OK: " + certs.size() + " certificates.");

        // Get supported curves
        Vector<ECParameterSpec> supportedEC = getKnownCurves(p);

        System.out.println("Test Certs:\n");
        for (X509Certificate cert : certs.values()) {
            X509Certificate issuer = certs.get(cert.getIssuerX500Principal());
            System.out.print("Verifying " + cert.getSubjectX500Principal() +
                    "...  ");
            PublicKey key = issuer.getPublicKey();
            // Check if curve is supported
            if (issuer.getPublicKey() instanceof ECPublicKey) {
                if (!checkSupport(supportedEC,
                        ((ECPublicKey)key).getParams())) {
                    System.out.println("Curve not found. Skipped.");
                    continue;
                }
            }

           try {
               cert.verify(key, p.getName());
               System.out.println("Pass.");
           } catch (NoSuchAlgorithmException e) {
               System.out.println("Warning: " + e.getMessage() +
                   ". Trying another provider...");
               cert.verify(key);
           } catch (Exception e) {
               System.out.println(e.getMessage());
               if (key instanceof ECPublicKey) {
                   System.out.println("Failed.\n\tCurve: " +
                           ((ECPublicKey)key).getParams() +
                           "\n\tSignature Alg: " + cert.getSigAlgName());
               } else {
                   System.out.println("Key: "+key.toString());
               }

               System.err.println("Verifying " + cert.getSubjectX500Principal());
               e.printStackTrace();
           }
        }

        // try some random invalid signatures to make sure we get the correct
        // error
        System.out.println("Checking incorrect signatures...");
        List<X509Certificate> certList = new ArrayList<X509Certificate>(certs.values());
        for (int i = 0; i < 20; i++) {
            X509Certificate cert, signer;
            do {
                cert = getRandomCert(certList);
                signer = getRandomCert(certList);
            } while (cert.getIssuerX500Principal().equals(signer.getSubjectX500Principal()));
            try {
                PublicKey signerPublicKey = signer.getPublicKey();
                cert.verify(signerPublicKey);
                // Ignore false positives
                if (cert.getPublicKey().equals(signerPublicKey)) {
                    System.out.println("OK: self-signed certificate detected");
                } else {
                    throw new Exception("Verified invalid signature");
                }
            } catch (SignatureException e) {
                System.out.println("OK: " + e);
            } catch (InvalidKeyException e) {
                System.out.println("OK: " + e);
            }
        }

        System.out.println("OK");
    }

    private static X509Certificate getRandomCert(List<X509Certificate> certs) {
        int n = random.nextInt(certs.size());
        return certs.get(n);
    }

}
