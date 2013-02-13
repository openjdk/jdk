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

/*
 * @test
 * @bug 8006591
 * @summary Protect keystore entries using stronger PBE algorithms
 */

import java.io.*;
import java.security.*;
import javax.crypto.spec.*;

// Retrieve a keystore entry, protected by the default encryption algorithm.
// Set the keystore entry, protected by a stronger encryption algorithm.

public class PBETest {
    private final static String DIR = System.getProperty("test.src", ".");
    //private static final String PBE_ALGO = "PBEWithHmacSHA1AndAES_128";
    private static final String PBE_ALGO = "PBEWithSHA1AndDESede";
    private static final char[] PASSWORD = "passphrase".toCharArray();
    private static final String KEYSTORE_TYPE = "JKS";
    private static final String KEYSTORE = DIR + "/keystore.jks";
    private static final String NEW_KEYSTORE_TYPE = "PKCS12";
    private static final String NEW_KEYSTORE = PBE_ALGO + ".p12";
    private static final String ALIAS = "vajra";

    private static final byte[] IV = {
        0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,
        0x19,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x20
    };
    private static final byte[] SALT = {
        0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08
    };
    private static final int ITERATION_COUNT = 1024;

    public static void main(String[] args) throws Exception {

        new File(NEW_KEYSTORE).delete();

        KeyStore keystore = load(KEYSTORE_TYPE, KEYSTORE, PASSWORD);
        KeyStore.Entry entry =
            keystore.getEntry(ALIAS,
                new KeyStore.PasswordProtection(PASSWORD));
        System.out.println("Retrieved entry named '" + ALIAS + "'");

        // Set entry
        KeyStore keystore2 = load(NEW_KEYSTORE_TYPE, null, null);
        keystore2.setEntry(ALIAS, entry,
            new KeyStore.PasswordProtection(PASSWORD, PBE_ALGO,
                new PBEParameterSpec(SALT, ITERATION_COUNT,
                    new IvParameterSpec(IV))));
        System.out.println("Encrypted entry using: " + PBE_ALGO);

        try (FileOutputStream outStream = new FileOutputStream(NEW_KEYSTORE)) {
            System.out.println("Storing keystore to: " + NEW_KEYSTORE);
            keystore2.store(outStream, PASSWORD);
        }

        keystore2 = load(NEW_KEYSTORE_TYPE, NEW_KEYSTORE, PASSWORD);
        entry = keystore2.getEntry(ALIAS,
            new KeyStore.PasswordProtection(PASSWORD));
        System.out.println("Retrieved entry named '" + ALIAS + "'");
    }

    private static KeyStore load(String type, String path, char[] password)
        throws Exception {
        KeyStore keystore = KeyStore.getInstance(type);

        if (path != null) {

            try (FileInputStream inStream = new FileInputStream(path)) {
                System.out.println("Loading keystore from: " + path);
                keystore.load(inStream, password);
                System.out.println("Loaded keystore with " + keystore.size() +
                    " entries");
            }
        } else {
            keystore.load(null, null);
        }

        return keystore;
    }
}
