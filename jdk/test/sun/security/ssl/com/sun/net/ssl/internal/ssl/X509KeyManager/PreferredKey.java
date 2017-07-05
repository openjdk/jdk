/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6302644
 * @summary X509KeyManager implementation for NewSunX509 doesn't return most
 *          preferable key
 */
import java.io.*;
import java.net.*;
import java.security.*;
import javax.net.ssl.*;

public class PreferredKey {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../../../../etc";
    static String keyStoreFile = "keystore";
    static String passwd = "passphrase";


    public static void main(String[] args) throws Exception {
        KeyStore ks;
        KeyManagerFactory kmf;
        X509KeyManager km;

        String keyFilename =
            System.getProperty("test.src", ".") + "/" + pathToStores +
                "/" + keyStoreFile;
        char [] password = passwd.toCharArray();

        ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keyFilename), password);
        kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, password);
        km = (X509KeyManager) kmf.getKeyManagers()[0];

        /*
         * There should be both an rsa and a dsa entry in the
         * keystore, otherwise the test will no work.
         */
        String[] aliases = km.getClientAliases("RSA", null);
        String alias = km.chooseClientAlias(new String[] {"RSA", "DSA"},
                                     null, null);

        // there're should both be null or nonnull
        if (aliases != null || alias != null) {
            String algorithm = km.getPrivateKey(alias).getAlgorithm();
            if (!algorithm.equals("RSA") || !algorithm.equals(
                        km.getPrivateKey(aliases[0]).getAlgorithm())) {
                throw new Exception("Failed to get the preferable key aliases");
            }
        }

        aliases = km.getClientAliases("DSA", null);
        alias = km.chooseClientAlias(new String[] {"DSA", "RSA"},
                                     null, null);

        // there're should both be null or nonnull
        if (aliases != null || alias != null) {
            String algorithm = km.getPrivateKey(alias).getAlgorithm();
            if (!algorithm.equals("DSA") || !algorithm.equals(
                        km.getPrivateKey(aliases[0]).getAlgorithm())) {
                throw new Exception("Failed to get the preferable key aliases");
            }
        }
    }
}
