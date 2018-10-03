/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8011402
 * @summary Move blacklisting certificate logic from hard code to data
 * @modules java.base/sun.security.util
 */

import sun.security.util.UntrustedCertificates;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

public class CheckBlacklistedCerts {
    public static void main(String[] args) throws Exception {

        String home = System.getProperty("java.home");
        boolean failed = false;

        // Root CAs should always be trusted
        File file = new File(home, "lib/security/cacerts");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(file)) {
            ks.load(fis, null);
        }
        System.out.println("Check for cacerts: " + ks.size());
        for (String alias: Collections.list(ks.aliases())) {
            X509Certificate cert = (X509Certificate)ks.getCertificate(alias);
            if (UntrustedCertificates.isUntrusted(cert)) {
                System.out.print(alias + " is untrusted");
                failed = true;
            }
        }

        // All certs in the pem files
        Set<Certificate> blacklisted = new HashSet<>();

        // Assumes the full src is available
        File[] blacklists = {
            new File(System.getProperty("test.src"),
                "../../../make/data/blacklistedcertsconverter/blacklisted.certs.pem"),
            new File(System.getProperty("test.src"),
                "../../../make/closed/data/blacklistedcertsconverter/blacklisted.certs.pem")
        };

        // Is this an OPENJDK build?
        String prop = System.getProperty("java.runtime.name");
        if (prop != null && prop.startsWith("OpenJDK")) {
            System.out.println("This is a OpenJDK build.");
            blacklists = Arrays.copyOf(blacklists, 1);
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (File blacklist: blacklists) {
            System.out.print("Check for " + blacklist + ": ");
            if (!blacklist.exists()) {
                System.out.println("does not exist");
            } else {
                try (FileInputStream fis = new FileInputStream(blacklist)) {
                    Collection<? extends Certificate> certs
                            = cf.generateCertificates(fis);
                    System.out.println(certs.size());
                    for (Certificate c: certs) {
                        blacklisted.add(c);
                        X509Certificate cert = ((X509Certificate)c);
                        if (!UntrustedCertificates.isUntrusted(cert)) {
                            System.out.println(cert.getSubjectDN() + " is trusted");
                            failed = true;
                        }
                    }
                }
            }
        }

        // Check the blacklisted.certs file itself
        file = new File(home, "lib/security/blacklisted.certs");
        System.out.print("Check for " + file + ": ");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)))) {
            int acount = 0;
            int ccount = 0;
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("Algorithm")) {
                    acount++;
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    ccount++;
                }
            }
            System.out.println(acount + " algs, " + ccount + " certs" );
            if (acount != 1) {
                System.out.println("There are " + acount + " algorithms");
                failed = true;
            }
            if (ccount != blacklisted.size()
                    && !blacklisted.isEmpty()) {
                System.out.println("Wrong blacklisted.certs size: "
                        + ccount + " fingerprints, "
                        + blacklisted.size() + " certs");
                failed = true;
            }
        }

        if (failed) {
            throw new Exception("Failed");
        }
    }
}
