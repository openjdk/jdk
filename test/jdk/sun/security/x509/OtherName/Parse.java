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
 * @bug 8277976
 * @summary Break up SEQUENCE in X509Certificate::getSubjectAlternativeNames
 *          and X509Certificate::getIssuerAlternativeNames in otherName
 * @modules java.base/sun.security.util
 *          java.base/sun.security.x509
 *          java.base/sun.security.tools.keytool
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.OIDMap;
import sun.security.x509.OtherName;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.X500Name;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;

public class Parse {

    public static class MyDNSName extends DNSName {
        public MyDNSName(byte[] in) throws IOException {
            super(new String(Arrays.copyOfRange(in, 2, in.length),
                    StandardCharsets.US_ASCII));
        }
    }

    public static void main(String[] args) throws Exception {
        OIDMap.addAttribute("n1", "1.2.3.6", MyDNSName.class);

        CertificateExtensions exts = new CertificateExtensions();
        GeneralNames names = new GeneralNames();

        byte[] d1 = new byte[] {
                DerValue.tag_OctetString, 5, 'a', '.', 'c', 'o', 'm' };
        names.add(new GeneralName(
                new OtherName(ObjectIdentifier.of("1.2.3.5"), d1)));

        byte[] d2 = new byte[] {
                DerValue.tag_UTF8String, 5, 'a', '.', 'c', 'o', 'm' };
        names.add(new GeneralName(
                new OtherName(ObjectIdentifier.of("1.2.3.6"), d2)));

        exts.set("x", new SubjectAlternativeNameExtension(names));
        CertAndKeyGen g = new CertAndKeyGen("Ed25519", "Ed25519");
        g.generate(-1);
        X509Certificate x = g.getSelfCertificate(new X500Name("CN=ME"),
                new Date(),
                100000,
                exts);

        int found = 0;
        for (var san : x.getSubjectAlternativeNames()) {
            if (san.size() >= 4 && san.get(0).equals(0)) {
                if (san.get(2).equals("1.2.3.5")) {
                    Asserts.assertTrue(Arrays.equals((byte[]) san.get(3), d1));
                    found++;
                } else if (san.get(2).equals("1.2.3.6")) {
                    Asserts.assertEQ(san.get(3), "a.com");
                    found++;
                }
            }
        }
        Asserts.assertEQ(found, 2);
    }
}

