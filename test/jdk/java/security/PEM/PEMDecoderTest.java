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

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.PEMDecoder;
import java.security.PublicKey;
import java.security.SecurityObject;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.Arrays;

public class PEMDecoderTest {

    static HexFormat hex = HexFormat.of();

    PEMDecoderTest() {
    }

    public static void main(String[] args) throws IOException {
        PEMCerts.entryList.stream().forEach(entry -> test(entry));
        PEMCerts.entryList.stream().forEach(entry -> testSecurityObject(entry));
        testTwoKeys();
        testFailure(PEMCerts.getEntry("ecprivpem"), ECPublicKey.class, false);
        testFailure(PEMCerts.getEntry(PEMCerts.failureEntryList, "rsaOpenSSL"), RSAPublicKey.class, false);
    }

    static void testFailure(PEMCerts.Entry entry, Class c, boolean expected) {
        try {
            test(entry.pem(), c, expected);
            throw new AssertionError("Failure with " +
                entry.name() + ":  Not supposed to succeed.");
        } catch (Exception e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getMessage());
        }
    }

    static void test(String pem) {
        try {
            new PEMDecoder().decode(pem);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static void test(PEMCerts.Entry entry) {
        try {
            test(entry.pem(), entry.type(), true);
            System.out.println("PASS (" + entry.name() + ")");
        } catch (Exception|AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    static void test(String pem, Class c) throws IOException {
            test(pem, c, true);
    }

    static void testSecurityObject(PEMCerts.Entry entry) {
        try {
            test(entry.pem(), SecurityObject.class, true);
        } catch (Exception|AssertionError e) {
            throw new RuntimeException("Error with " + entry.name() + ":  " +
                e.getMessage(), e);
        }
    }


    static List getInterfaceList(Class<?> so) {
        Class<?>[] clist = so.getInterfaces();
        ArrayList<Class> list = new ArrayList<>();
        list.addAll(Arrays.asList(clist));
        if (clist.length > 0) {
            for (Class cc : clist) {
                list.addAll(getInterfaceList(cc));
            }
        }
        return list;
    }

    static void test(String pem, Class c, boolean expected) throws IOException {
        var pk = new PEMDecoder().decode(pem);
        List<Class> list = getInterfaceList(pk.getClass());
        for(Class cc : list) {
            if (cc.equals(c)) {
                return;
            }
        }
        throw new RuntimeException("Entry did not contain expected: " + c);
    }

    // Run the same key twice through the same decoder and make sure the
    // result is the same
    static void testTwoKeys() throws IOException {
        PublicKey p1, p2;
        PEMDecoder pd = new PEMDecoder();
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
}