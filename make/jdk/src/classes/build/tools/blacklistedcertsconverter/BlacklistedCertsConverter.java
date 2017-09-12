/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.blacklistedcertsconverter;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;


/**
 * Converts blacklisted.certs.pem from System.in to blacklisted.certs in
 * System.out. The input must start with a #! line including the fingerprint
 * algorithm. The output is sorted and unique.
 */
public class BlacklistedCertsConverter {

    public static void main(String[] args) throws Exception {

        byte[] pattern = "#! java BlacklistedCertsConverter ".getBytes();
        String mdAlg = "";

        for (int i=0; ; i++) {
            int n = System.in.read();
            if (n < 0) {
                throw new Exception("Unexpected EOF");
            }
            if (i < pattern.length) {
                if (n != pattern[i]) {
                    throw new Exception("The first line must start with \""
                            + new String(pattern) + "\"");
                }
            } else if (i < pattern.length + 100) {
                if (n < 32) {
                    break;
                } else {
                    mdAlg = mdAlg + String.format("%c", n);
                }
            }
        }

        mdAlg = mdAlg.trim();
        System.out.println("Algorithm=" + mdAlg);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs
                = cf.generateCertificates(System.in);

        // Output sorted so that it's easy to locate an entry.
        Set<String> fingerprints = new TreeSet<>();
        for (Certificate cert: certs) {
            fingerprints.add(
                    getCertificateFingerPrint(mdAlg, (X509Certificate)cert));
        }

        for (String s: fingerprints) {
            System.out.println(s);
        }
    }

    /**
     * Converts a byte to hex digit and writes to the supplied buffer
     */
    private static void byte2hex(byte b, StringBuffer buf) {
        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]);
        buf.append(hexChars[low]);
    }

    /**
     * Gets the requested finger print of the certificate.
     */
    private static String getCertificateFingerPrint(
            String mdAlg, X509Certificate cert) throws Exception {
        byte[] encCertInfo = cert.getEncoded();
        MessageDigest md = MessageDigest.getInstance(mdAlg);
        byte[] digest = md.digest(encCertInfo);
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            byte2hex(digest[i], buf);
        }
        return buf.toString();
    }
}
