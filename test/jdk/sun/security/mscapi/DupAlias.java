/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/**
 * @test
 * @bug 8187634
 * @requires os.family == "windows"
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @summary getCertificateAlias should return correct alias
 */
public class DupAlias {
    public static void main(String[] args) throws Exception {

        String nn = "8187634";
        String na = nn + "a";
        String nb = nn + "b";
        String n1 = nn + " (1)";

        CertAndKeyGen g = new CertAndKeyGen("EC", "SHA256withECDSA");
        g.generate(-1);
        X509Certificate a = g.getSelfCertificate(new X500Name("CN=" + na), 1000);
        g.generate(-1);
        X509Certificate b = g.getSelfCertificate(new X500Name("CN=" + nb), 1000);

        KeyStore ks = KeyStore.getInstance("Windows-MY-CURRENTUSER");
        try {
            ks.load(null, null);
            ks.deleteEntry(na);
            ks.deleteEntry(nb);
            ks.deleteEntry(nn);
            ks.deleteEntry(n1);
            ks.setCertificateEntry(na, a);
            ks.setCertificateEntry(nb, b);

            ps(String.format("""
                    $cert = Get-Item Cert:/CurrentUser/My/%s;
                    $cert.FriendlyName = %s;
                    $cert = Get-Item Cert:/CurrentUser/My/%s;
                    $cert.FriendlyName = %s;
                    """, thumbprint(a), nn, thumbprint(b), nn));

            ks.load(null, null);
            Asserts.assertFalse(ks.containsAlias(na));
            Asserts.assertFalse(ks.containsAlias(nb));
            Asserts.assertEquals(ks.getCertificateAlias(ks.getCertificate(nn)), nn);
            Asserts.assertEquals(ks.getCertificateAlias(ks.getCertificate(n1)), n1);
        } finally {
            ks.deleteEntry(na);
            ks.deleteEntry(nb);
            ks.deleteEntry(nn);
            ks.deleteEntry(n1);
        }
    }

    static void ps(String f) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", f);
        pb.inheritIO();
        if (pb.start().waitFor() != 0) {
            throw new RuntimeException("Failed");
        }
    }

    static String thumbprint(X509Certificate c) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(c.getEncoded()));
    }
}
