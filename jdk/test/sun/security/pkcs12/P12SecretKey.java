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
 * @bug 8149411
 * @summary Get AES key from keystore (uses SecretKeySpec not SecretKeyFactory)
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class P12SecretKey {

    private static final String ALIAS = "alias";

    public static void main(String[] args) throws Exception {
        P12SecretKey testp12 = new P12SecretKey();
        String keystoreType = "pkcs12";
        if (args != null && args.length > 0) {
            keystoreType = args[0];
        }
        testp12.run(keystoreType);
    }

    private void run(String keystoreType) throws Exception {
        char[] pw = "password".toCharArray();
        KeyStore ks = KeyStore.getInstance(keystoreType);
        ks.load(null, pw);

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey key = kg.generateKey();

        KeyStore.SecretKeyEntry ske = new KeyStore.SecretKeyEntry(key);
        KeyStore.ProtectionParameter kspp = new KeyStore.PasswordProtection(pw);
        ks.setEntry(ALIAS, ske, kspp);

        File ksFile = File.createTempFile("test", ".test");
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, pw);
            fos.flush();
        }

        // now see if we can get it back
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            KeyStore ks2 = KeyStore.getInstance(keystoreType);
            ks2.load(fis, pw);
            KeyStore.Entry entry = ks2.getEntry(ALIAS, kspp);
            SecretKey keyIn = ((KeyStore.SecretKeyEntry)entry).getSecretKey();
            if (Arrays.equals(key.getEncoded(), keyIn.getEncoded())) {
                System.err.println("OK: worked just fine with " + keystoreType +
                                   " keystore");
            } else {
                System.err.println("ERROR: keys are NOT equal after storing in "
                                   + keystoreType + " keystore");
            }
        }
    }
}
