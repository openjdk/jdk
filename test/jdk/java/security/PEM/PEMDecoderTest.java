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

import java.io.CharArrayReader;
import java.io.IOException;
import java.security.Key;
import java.security.PEMDecoder;
import java.security.PublicKey;
import java.security.SecurityObject;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HexFormat;

class PEMDecoderTest {

    static HexFormat hex = HexFormat.of();

    PEMDecoderTest() {
    }

    public static void main(String[] args) throws Exception{
        testSecurityObject();
        testClass();
        testTwoKeys();

    }

    static void testSecurityObject() throws IOException {
        SecurityObject so;

        so = new PEMDecoder().decode(PEMCerts.pubrsapem);
        if (!(so instanceof RSAPublicKey)) {
            throw new AssertionError("pubrsapem failed. Should be " +
                "RSAPublicKey: " +  so.getClass());
        }
    }

    static void testClass() throws IOException {
        var pk = new PEMDecoder().decode(PEMCerts.pubrsapem,
            RSAPublicKey.class);
        if (!(pk instanceof RSAPublicKey)) {
            throw new AssertionError("pubrsapem failed. Should be " +
                "RSAPublicKey: " + pk.getClass());
        }
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

    SecurityObject decodeKey(String pem) throws IOException {
        return new PEMDecoder().decode(pem);
    }
}