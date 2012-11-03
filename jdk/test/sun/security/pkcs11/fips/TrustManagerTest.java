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

/*
 * @test
 * @bug 6323647
 * @summary Verify that the SunJSSE trustmanager works correctly in FIPS mode
 * @author Andreas Sterbenz
 * @library ..
 * @run main/othervm TrustManagerTest
 */

import java.io.*;
import java.util.*;

import java.security.*;
import java.security.cert.*;

import javax.net.ssl.*;

// This test belongs more in JSSE than here, but the JSSE workspace does not
// have the NSS test infrastructure. It will live here for the time being.

public class TrustManagerTest extends SecmodTest {

    public static void main(String[] args) throws Exception {
        if (initSecmod() == false) {
            return;
        }

        if ("sparc".equals(System.getProperty("os.arch")) == false) {
            // we have not updated other platforms with the proper NSS libraries yet
            System.out.println("Test currently works only on solaris-sparc, skipping");
            return;
        }

        String configName = BASE + SEP + "fips.cfg";
        Provider p = getSunPKCS11(configName);

        System.out.println(p);
        Security.addProvider(p);

        Security.removeProvider("SunJSSE");
        Provider jsse = new com.sun.net.ssl.internal.ssl.Provider(p);
        Security.addProvider(jsse);
        System.out.println(jsse.getInfo());

        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, "test12".toCharArray());

        X509Certificate server = loadCertificate("certs/server.cer");
        X509Certificate ca = loadCertificate("certs/ca.cer");
        X509Certificate anchor = loadCertificate("certs/anchor.cer");

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("anchor", anchor);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        X509TrustManager tm = (X509TrustManager)tmf.getTrustManagers()[0];

        X509Certificate[] chain = {server, ca, anchor};

        tm.checkServerTrusted(chain, "RSA");

        System.out.println("OK");
    }

    private static X509Certificate loadCertificate(String name) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream in = new FileInputStream(BASE + SEP + name);
        X509Certificate cert = (X509Certificate)cf.generateCertificate(in);
        in.close();
        return cert;
    }

}
