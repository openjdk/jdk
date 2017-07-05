/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8138766
 * @summary New default -sigalg for keytool
 * @modules java.base/sun.security.tools.keytool
 */

import sun.security.tools.keytool.Main;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class DefaultSignatureAlgorithm {

    private static int counter = 0;

    public static void main(String[] args) throws Exception {

        // Calculating large RSA keys are too slow.
        run("RSA", 1024, null, "SHA256withRSA");
        run("RSA", 3072, null, "SHA256withRSA");
        run("RSA", 3073, null, "SHA384withRSA");

        run("DSA", 1024, null, "SHA256withDSA");
        run("DSA", 3072, null, "SHA256withDSA");

        run("EC", 192, null, "SHA256withECDSA");
        run("EC", 384, null, "SHA384withECDSA");
        run("EC", 571, null, "SHA512withECDSA");

        // If you specify one, it will be used.
        run("EC", 571, "SHA256withECDSA", "SHA256withECDSA");
    }

    private static void run(String keyAlg, int keySize,
                    String sigAlg, String expectedSigAlg) throws Exception {
        String alias = keyAlg + keySize + (counter++);
        String cmd = "-keystore ks -storepass changeit" +
                " -keypass changeit -alias " + alias +
                " -keyalg " + keyAlg + " -keysize " + keySize +
                " -genkeypair -dname CN=" + alias + " -debug";
        if (sigAlg != null) {
            cmd += " -sigalg " + sigAlg;
        }
        Main.main(cmd.split(" "));

        KeyStore ks = KeyStore.getInstance(
                new File("ks"), "changeit".toCharArray());
        X509Certificate cert = (X509Certificate)ks.getCertificate(alias);
        String actualSigAlg = cert.getSigAlgName();
        if (!actualSigAlg.equals(expectedSigAlg)) {
            throw new Exception("Failure at " + alias + ": expected "
                    + expectedSigAlg + ", actually " + actualSigAlg);
        }
    }
}
