/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4853305
 * @summary Test the new RSA provider can verify all the RSA certs in the cacerts file
 * @library /test/lib/
 */

// this test serves as our known answer test

import jtreg.SkippedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class TestCACerts {

    private final static String PROVIDER =
            System.getProperty("test.provider.name", "SunRsaSign");

    private final static char SEP = File.separatorChar;

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        String javaHome = System.getProperty("java.home");
        String caCerts = javaHome + SEP + "lib" + SEP + "security" + SEP + "cacerts";
        InputStream in = new FileInputStream(caCerts);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(in, null);
        in.close();

        final List<String> skippedCases = new ArrayList<>();

        for (Enumeration e = ks.aliases(); e.hasMoreElements(); ) {
            String alias = (String)e.nextElement();
            if (ks.isCertificateEntry(alias)) {
                System.out.println("* Testing " + alias + "...");
                X509Certificate cert = (X509Certificate)ks.getCertificate(alias);
                PublicKey key = cert.getPublicKey();
                String alg = key.getAlgorithm();
                if (alg.equals("RSA")) {
                    System.out.println("Signature algorithm: " + cert.getSigAlgName());
                    cert.verify(key, PROVIDER);
                } else {
                    System.out.println("Skipping cert with key: " + alg);
                }
            } else {
                skippedCases.add(String.format("[alias: %s]",
                        alias));
                System.out.println("Skipping alias " + alias);
            }
        }
        long stop = System.currentTimeMillis();

        if (!skippedCases.isEmpty()) {
            throw new SkippedException("Some tests were skipped " +
                                       skippedCases);
        }
        System.out.println("All tests passed (" + (stop - start) + " ms).");
    }

}
