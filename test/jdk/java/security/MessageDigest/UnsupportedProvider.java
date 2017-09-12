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

import java.security.Provider;
import java.security.Security;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * @test
 * @bug 8156059
 * @summary Test expected NoSuchAlgorithmException is thrown
 *          if using SHA-3 with unsupported providers
 */

public class UnsupportedProvider {

    public static void main(String args[]) {
        String[] algorithms = { "SHA3-224", "SHA3-256", "SHA3-384",
                "SHA3-512" };

        for (Provider prov : Security.getProviders()) {
            for (String algo : algorithms) {
                try {
                    String provName = prov.getName();
                    MessageDigest md = MessageDigest.getInstance(algo, prov);

                    if (!isSHA3Supported(provName)) {
                        throw new RuntimeException("SHA-3 is not supported by "
                                + provName + " provider, but expected "
                                + "NoSuchAlgorithmException is not thrown");
                    }
                } catch (NoSuchAlgorithmException ex) {
                    if (isSHA3Supported(prov.getName())) {
                        throw new RuntimeException("SHA-3 should be supported "
                                + "by " + prov.getName() + " provider, got"
                                + " unexpected NoSuchAlgorithmException");
                    }
                    continue;
                }
            }
        }
    }

    // Check if specific provider supports SHA-3 hash algorithms
    static boolean isSHA3Supported(String provName) {
        if ("SUN".equals(provName)) {
            return true;
        }
        if ("OracleUcrypto".equals(provName)
                && "SunOS".equals(System.getProperty("os.name"))
                && System.getProperty("os.version").compareTo("5.12") >= 0) {
            return true;
        }
        return false;
    }
}
