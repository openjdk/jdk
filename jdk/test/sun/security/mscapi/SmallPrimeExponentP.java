/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;

/*
 * @test
 * @bug 8023546
 * @key intermittent
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.tools.keytool
 * @summary sun/security/mscapi/ShortRSAKey1024.sh fails intermittently
 * @requires os.family == "windows"
 */
public class SmallPrimeExponentP {

    public static void main(String argv[]) throws Exception {

        String osName = System.getProperty("os.name");
        if (!osName.startsWith("Windows")) {
            System.out.println("Not windows");
            return;
        }
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);
        CertAndKeyGen ckg = new CertAndKeyGen("RSA", "SHA1withRSA");
        ckg.setRandom(new SecureRandom());
        boolean see63 = false, see65 = false;
        while (!see63 || !see65) {
            ckg.generate(1024);
            RSAPrivateCrtKey k = (RSAPrivateCrtKey) ckg.getPrivateKey();
            int len = k.getPrimeExponentP().toByteArray().length;
            if (len == 63 || len == 65) {
                if (len == 63) {
                    if (see63) continue;
                    else see63 = true;
                }
                if (len == 65) {
                    if (see65) continue;
                    else see65 = true;
                }
                System.err.print(len);
                ks.setKeyEntry("anything", k, null, new X509Certificate[]{
                        ckg.getSelfCertificate(new X500Name("CN=Me"), 1000)
                });
            }
            System.err.print('.');
        }
        ks.store(null, null);
    }
}
