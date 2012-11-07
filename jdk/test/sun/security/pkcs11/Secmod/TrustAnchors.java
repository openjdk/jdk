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
 * @bug 6298106 6275523 6420252
 * @summary make sure we can access the NSS trust anchor module
 * @author Andreas Sterbenz
 * @library ..
 * @run main/othervm TrustAnchors
 */

import java.util.*;

import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;

public class TrustAnchors extends SecmodTest {

    public static void main(String[] args) throws Exception {
        if (initSecmod() == false) {
            return;
        }

        if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            // our secmod.db file says nssckbi.*so*, so NSS does not find the
            // *DLL* on windows.
            System.out.println("Test currently does not work on Windows, skipping");
            return;
        }

        String configName = BASE + SEP + "nsstrust.cfg";
        Provider p = getSunPKCS11(configName);

        System.out.println(p);
        Security.addProvider(p);
        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, null);
        Collection<String> aliases = new TreeSet<String>(Collections.list(ks.aliases()));
        System.out.println("entries: " + aliases.size());
        System.out.println(aliases);

        for (String alias : aliases) {
            if (ks.isCertificateEntry(alias) == false) {
                throw new Exception("not trusted: " + alias);
            }
            X509Certificate cert = (X509Certificate)ks.getCertificate(alias);
            // verify self-signed certs
            if (cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
            System.out.print(".");
                cert.verify(cert.getPublicKey());
            } else {
                System.out.print("-");
            }
        }

        System.out.println();
        System.out.println("OK");
    }

}
