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
 * @key intermittent
 * @summary New default -sigalg for keytool
 * @modules java.base/sun.security.tools.keytool
 * @modules jdk.crypto.ec
 * @run main/othervm DefaultSignatureAlgorithm RSA 1024 SHA256withRSA
 * @run main/othervm DefaultSignatureAlgorithm RSA 3072 SHA256withRSA
 * @run main/othervm DefaultSignatureAlgorithm RSA 3073 SHA384withRSA
 * @run main/othervm DefaultSignatureAlgorithm DSA 1024 SHA256withDSA
 * @run main/othervm/timeout=700 DefaultSignatureAlgorithm DSA 3072
 *      SHA256withDSA
 * @run main/othervm DefaultSignatureAlgorithm EC 192 SHA256withECDSA
 * @run main/othervm DefaultSignatureAlgorithm EC 384 SHA384withECDSA
 * @run main/othervm DefaultSignatureAlgorithm EC 571 SHA512withECDSA
 * @run main/othervm DefaultSignatureAlgorithm EC 571 SHA256withECDSA
 *      SHA256withECDSA
 */

import sun.security.tools.keytool.Main;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class DefaultSignatureAlgorithm {

    public static void main(String[] args) throws Exception {
        if(args == null || args.length < 3) {
            throw new RuntimeException("Invalid arguments provided.");
        }
        String sigAlg = (args.length == 4) ? args[3] : null;
        run(args[0], Integer.valueOf(args[1]), args[2], sigAlg);
    }

    private static void run(String keyAlg, int keySize,
                    String expectedSigAlg, String sigAlg) throws Exception {
        String alias = keyAlg + keySize + System.currentTimeMillis();
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
