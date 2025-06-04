/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293489
 * @summary Accept CAs with BasicConstraints without pathLenConstraint
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;

import java.io.File;
import java.security.*;
import java.security.cert.*;

import javax.net.ssl.*;

public class BasicConstraints12 {

    public static void main(String[] args) throws Exception {

        genkey("-dname CN=TrustAnchor -alias anchor");
        genkey("-dname CN=IntermediateCA -alias ca -ext bc:critical -signer anchor");
        genkey("-dname CN=Server -alias server -signer ca");

        KeyStore full = KeyStore.getInstance(new File("ks"), "changeit".toCharArray());
        X509Certificate anchor = (X509Certificate) full.getCertificate("anchor");
        X509Certificate ca = (X509Certificate) full.getCertificate("ca");
        X509Certificate server = (X509Certificate) full.getCertificate("server");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("anchor", anchor);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        X509TrustManager tm = (X509TrustManager)tmf.getTrustManagers()[0];

        X509Certificate[] chain = new X509Certificate[] {server, ca, anchor};

        System.out.println("Calling trustmanager...");

        tm.checkServerTrusted(chain, "RSA");
        System.out.println("Test ok");
    }

    static void genkey(String s) throws Exception {
        SecurityTools.keytool("-storepass changeit -keystore ks -genkeypair -keyalg RSA " + s)
                .shouldHaveExitValue(0);
    }
}
