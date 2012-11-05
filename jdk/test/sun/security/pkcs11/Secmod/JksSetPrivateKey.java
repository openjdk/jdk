/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6269847
 * @summary store a NSS PKCS11 PrivateKeyEntry to JKS KeyStore throws confusing NPE
 * @author Wang Weijun
 * @library ..
 * @run main/othervm JksSetPrivateKey
 */

import java.util.*;

import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;

public class JksSetPrivateKey extends SecmodTest {

    public static void main(String[] args) throws Exception {
        if (initSecmod() == false) {
            return;
        }

        String configName = BASE + SEP + "nss.cfg";
        Provider p = getSunPKCS11(configName);

        System.out.println(p);
        Security.addProvider(p);
        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, password);
        Collection<String> aliases = new TreeSet<String>(Collections.list(ks.aliases()));
        System.out.println("entries: " + aliases.size());
        System.out.println(aliases);

        PrivateKey privateKey = (PrivateKey)ks.getKey(keyAlias, password);
        System.out.println(privateKey);

        X509Certificate[] chain = (X509Certificate[])ks.getCertificateChain(keyAlias);

        KeyStore jks = KeyStore.getInstance("JKS");
        jks.load(null, null);

        try {
            jks.setKeyEntry("k1", privateKey, "changeit".toCharArray(), chain);
            throw new Exception("No, an NSS PrivateKey shouldn't be extractable and put inside a JKS keystore");
        } catch (KeyStoreException e) {
            System.err.println(e);; // This is OK
        }

        try {
            jks.setKeyEntry("k2", new DummyPrivateKey(), "changeit".toCharArray(), chain);
            throw new Exception("No, non-PKCS#8 key shouldn't be put inside a KeyStore");
        } catch (KeyStoreException e) {
            System.err.println(e);; // This is OK
        }
        System.out.println("OK");

        try {
            jks.setKeyEntry("k3", new DummyPrivateKey2(), "changeit".toCharArray(), chain);
            throw new Exception("No, not-extractble key shouldn't be put inside a KeyStore");
        } catch (KeyStoreException e) {
            System.err.println(e);; // This is OK
        }
        System.out.println("OK");
    }
}

class DummyPrivateKey implements PrivateKey {
    public String getAlgorithm() {
        return "DUMMY";
    }

    public String getFormat() {
        return "DUMMY";
    }

    public byte[] getEncoded() {
        return "DUMMY".getBytes();
    }
}

class DummyPrivateKey2 implements PrivateKey {
    public String getAlgorithm() {
        return "DUMMY";
    }

    public String getFormat() {
        return "PKCS#8";
    }

    public byte[] getEncoded() {
        return null;
    }
}
