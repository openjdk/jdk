/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.System.out;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/*
 * @test
 * @bug 8048621
 * @summary Test the basic operations of KeyStore, provided by SunJCE (jceks),
 *  and SunPKCS11-Solaris(PKCS11KeyStore)
 * @author Yu-Ching Valerie PENG
 */

public class TestKeyStoreBasic {
    private static final char[] PASSWD2 = new char[] {
            'b', 'o', 'r', 'e', 'd'
    };
    private static final char[] PASSWDK = new String("cannot be null")
            .toCharArray();
    private static final String[] KS_Type = {
            "jks", "jceks", "pkcs12", "PKCS11KeyStore"
    };
    private static final String[] PRO_TYPE = {
            "SUN", "SunJCE", "SunJSSE", "SunPKCS11-Solaris"
    };
    private static final String ALIAS_HEAD = "test";

    public static void main(String args[]) throws Exception {
        TestKeyStoreBasic jstest = new TestKeyStoreBasic();
        jstest.run();
    }

    public void run() throws Exception {
        Provider[] providers = Security.getProviders();
        for (Provider p: providers) {
            String prvName = p.getName();
            if (prvName.startsWith("SunJCE")
                    || prvName.startsWith("SunPKCS11-Solaris")) {
                try {
                    runTest(p);
                    out.println("Test with provider " + p.getName() + ""
                            + " passed");
                } catch (java.security.KeyStoreException e) {
                    if (prvName.startsWith("SunPKCS11-Solaris")) {
                        out.println("KeyStoreException is expected "
                                + "PKCS11KeyStore is invalid keystore type.");
                        e.printStackTrace();
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    public void runTest(Provider p) throws Exception {
        SecretKey key = new SecretKeySpec(
                new String("No one knows").getBytes(), "PBE");
        int numEntries = 5;
        String proName = p.getName();
        String type = null;
        for (int i = 0; i < PRO_TYPE.length; i++) {
            if (proName.compareTo(PRO_TYPE[i]) == 0) {
                type = KS_Type[i];
                break;
            }
        }
        KeyStore ks = KeyStore.getInstance(type, p);
        KeyStore ks2 = KeyStore.getInstance(type, ks.getProvider().getName());

        // create an empty key store
        ks.load(null, null);

        // store the secret keys
        for (int j = 0; j < numEntries; j++) {
            ks.setKeyEntry(ALIAS_HEAD + j, key, PASSWDK, null);
        }

        // initialize the 2nd key store object with the 1st one
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, PASSWDK);
        byte[] bArr = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(bArr);
        ks2.load(bais, null);

        // check 2nd key store type
        checkType(ks2, type);
        // check the existing aliases for the 2nd key store
        checkAlias(ks2, numEntries);

        // compare the creation date of the 2 key stores for all aliases
        compareCreationDate(ks, ks2, numEntries);
        // remove the last entry from the 2nd key store
        numEntries--;
        ks2.deleteEntry(ALIAS_HEAD + numEntries);

        // re-initialize the 1st key store with the 2nd key store
        baos.reset();
        ks2.store(baos, PASSWD2);
        bais = new ByteArrayInputStream(baos.toByteArray());
        try {
            // expect an exception since the password is incorrect
            ks.load(bais, PASSWDK);
            throw new RuntimeException(
                    "ERROR: passed the loading with incorrect password");
        } catch (IOException ex) {
            bais.reset();
            ks.load(bais, PASSWD2);
            bais.reset();
            ks.load(bais, null);
        } finally {
            bais.close();
            baos.close();
        }

        // check key store type
        checkType(ks, type);

        // check the existing aliases
        checkAlias(ks, numEntries);

        // compare the creation date of the 2 key stores for all aliases
        compareCreationDate(ks, ks2, numEntries);

    }

    // check key store type
    private void checkType(KeyStore obj, String type) {
        if (!obj.getType().equals(type)) {
            throw new RuntimeException("ERROR: wrong key store type");

        }
    }

    // check the existing aliases
    private void checkAlias(KeyStore obj, int range) throws KeyStoreException {
        for (int k = 0; k < range; k++) {
            if (!obj.containsAlias(ALIAS_HEAD + k)) {
                throw new RuntimeException("ERROR: alias (" + k
                        + ") should exist");

            }
        }
    }

    // compare the creation dates - true if all the same
    private void compareCreationDate(KeyStore o1, KeyStore o2, int range)
            throws KeyStoreException {
        boolean result = true;
        String alias = null;
        for (int k = 0; k < range; k++) {
            alias = ALIAS_HEAD + k;
            if (!o1.getCreationDate(alias).equals(o2.getCreationDate(alias))) {
                throw new RuntimeException("ERROR: entry creation time (" + k
                        + ") differs");

            }
        }
    }

}
