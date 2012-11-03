/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6298106
 * @summary make sure we can add a trusted cert to the NSS KeyStore module
 * @author Andreas Sterbenz
 * @library ..
 * @run main/othervm AddTrustedCert
 */

import java.io.*;
import java.util.*;

import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;

public class AddTrustedCert extends SecmodTest {

    public static void main(String[] args) throws Exception {
        if (initSecmod() == false) {
            return;
        }

        InputStream in = new FileInputStream(BASE + SEP + "anchor.cer");
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate)factory.generateCertificate(in);
        in.close();
//      System.out.println(cert);

        String configName = BASE + SEP + "nss.cfg";
        Provider p = getSunPKCS11(configName);

        System.out.println(p);
        Security.addProvider(p);
        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, password);
        Collection<String> aliases = new TreeSet<String>(Collections.list(ks.aliases()));
        System.out.println("entries: " + aliases.size());
        System.out.println(aliases);
        int size1 = aliases.size();

        String alias = "anchor";
        ks.setCertificateEntry(alias, cert);
        ks.setCertificateEntry(alias, cert);

        aliases = new TreeSet<String>(Collections.list(ks.aliases()));
        System.out.println("entries: " + aliases.size());
        System.out.println(aliases);
        int size2 = aliases.size();

        if ((size2 != size1 + 1) || (aliases.contains(alias) == false)) {
            throw new Exception("Trusted cert not added");
        }
        X509Certificate cert2 = (X509Certificate)ks.getCertificate(alias);
        if (cert.equals(cert2) == false) {
            throw new Exception("KeyStore returned incorrect certificate");
        }

        System.out.println("OK");

    }

}
