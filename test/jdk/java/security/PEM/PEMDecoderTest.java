/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Testing PEM decodings
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.Arrays;

public class PEMDecoderTest {

    static HexFormat hex = HexFormat.of();

    PEMDecoderTest() {
    }

    public static void main(String[] args) throws IOException {
        PEMCerts.entryList.stream().forEach(entry -> test(entry));
        PEMCerts.entryList.stream().forEach(entry -> test(entry, SecurityObject.class));
        PEMCerts.encryptedList.stream().forEach(entry -> testEncrypted(entry));
        testTwoKeys();
        test(PEMCerts.getEntry("privpem"), RSAKey.class);
        PEMCerts.failureEntryList.stream().forEach(entry -> testFailure(entry));
        testFailure(PEMCerts.getEntry("ecprivpem"), ECPublicKey.class);
        testFailure(PEMCerts.getEntry(PEMCerts.privList, "rsaOpenSSL"), RSAPublicKey.class);
        testClass(PEMCerts.getEntry("privpem"), PKCS8EncodedKeySpec.class);
        testClass(PEMCerts.getEntry("privpem"), RSAPrivateCrtKeySpec.class);
        testClass(PEMCerts.getEntry("privpem"), RSAPrivateKeySpec.class);
        testClass(PEMCerts.getEntry("privpem"), X509EncodedKeySpec.class, false);
        testClass(PEMCerts.getEntry("rsaCert"), X509EncodedKeySpec.class, false);
        testClass(PEMCerts.getEntry("oasrfc8410"), PrivateKey.class, true);
        testClass(PEMCerts.getEntry("oasrfc8410"), PublicKey.class, true);

    }

    static void testFailure(PEMCerts.Entry entry) {
        testFailure(entry, entry.clazz());
    }

    static void testFailure(PEMCerts.Entry entry, Class c) {
        try {
            test(entry.pem(), c, PEMDecoder.of());
            throw new AssertionError("Failure with " +
                entry.name() + ":  Not supposed to succeed.");
        } catch (NullPointerException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        }
    }

    static void testEncrypted(PEMCerts.Entry entry) {
        PEMDecoder decoder = PEMDecoder.of();
        if (entry.clazz() != EncryptedPrivateKeyInfo.class) {
            decoder = decoder.withDecryption(entry.password());
        }

        try {
            test(entry.pem(), entry.clazz(), decoder);
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    // Change the Entry to use the given class as the expected class returned
    static void test(PEMCerts.Entry entry, Class c) {
        test(entry.newEntry(entry, c));
    }

    // Run test with a given Entry
    static void test(PEMCerts.Entry entry) {
        try {
            test(entry.pem(), entry.clazz(), PEMDecoder.of());
            System.out.println("PASS (" + entry.name() + ")");
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    static List getInterfaceList(Class ccc) {
        Class<?>[] interfaces = ccc.getInterfaces();
        List<Class> list = new ArrayList<>(Arrays.asList(interfaces));
        var x = ccc.getSuperclass();
        if (x != null) {
            list.add(x);
        }
        List<Class> results = new ArrayList<>(list);
        if (list.size() > 0) {
            for (Class cname : list) {
                try {
                    if (cname != null &&
                        cname.getName().startsWith("java.security.")) {
                        results.addAll(getInterfaceList(cname));
                    }
                } catch (Exception e) {
                    System.err.println("Exception with " + cname);
                }
            }
        }
        return results;
    }

    /**
     * Perform the decoding test with the given decoder, on the given pem, and
     * expect the clazz to be returned.
     */
    static void test(String pem, Class clazz, PEMDecoder decoder) throws IOException {
        var pk = decoder.decode(pem);

        // Check that clazz matches what pk returned.
        if (pk.getClass().equals(clazz)) {
            return;
        }

        // Search interfaces and inheritance to find a match with clazz
        List<Class> list = getInterfaceList(pk.getClass());
        for (Class cc : list) {
            if (cc != null && cc.equals(clazz)) {
                return;
            }
        }

        throw new RuntimeException("Entry did not contain expected: " +
            clazz.getName());
    }

    // Run the same key twice through the same decoder and make sure the
    // result is the same
    static void testTwoKeys() throws IOException {
        PublicKey p1, p2;
        PEMDecoder pd = PEMDecoder.of();
        p1 = pd.decode(PEMCerts.pubrsapem, RSAPublicKey.class);
        p2 = pd.decode(PEMCerts.pubrsapem, RSAPublicKey.class);
        if (!Arrays.equals(p1.getEncoded(), p2.getEncoded())) {
            System.err.println("These two should have matched:");
            System.err.println(hex.parseHex(new String(p1.getEncoded())));
            System.err.println(hex.parseHex(new String(p2.getEncoded())));
            throw new AssertionError("Two decoding of the same key failed to" +
                " match: ");
        }
    }

    static void testClass(PEMCerts.Entry entry, Class clazz) throws IOException {
        var pk = PEMDecoder.of().decode(entry.pem(), clazz);
    }

    static void testClass(PEMCerts.Entry entry, Class clazz, boolean pass) throws RuntimeException {
        try {
            testClass(entry, clazz);
        } catch (IOException e) {
            if (pass) {
                throw new RuntimeException(e);
            }
        }
    }
}