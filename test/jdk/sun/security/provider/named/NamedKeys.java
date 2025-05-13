/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8347938 8347941
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.x509
 * @library /test/lib
 * @summary check the Named***Key behavior
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.util.Arrays;

public class NamedKeys {
    public static void main(String[] args) throws Exception {

        var r = SeededSecureRandom.one();
        var raw = r.nBytes(32);

        Asserts.assertThrows(NullPointerException.class,
                () -> new NamedPKCS8Key("ML-DSA", "ML-DSA-44", raw, null));

        // Create a key using raw bytes
        var sk = new NamedPKCS8Key("ML-DSA", "ML-DSA-44", raw, raw);
        var enc = sk.getEncoded().clone();

        // The raw bytes array is re-used
        Asserts.assertTrue(sk.getRawBytes() == sk.getRawBytes());
        // but the encoding is different
        Asserts.assertTrue(sk.getEncoded() != sk.getEncoded());

        // When source change
        Arrays.fill(raw, (byte)0);
        // Internal raw bytes also changes
        Asserts.assertEqualsByteArray(sk.getRawBytes(), new byte[32]);
        // No guarantee on getEncoded() output, could be cached

        // Create a key using encoding
        Asserts.assertThrows(NullPointerException.class,
                () -> new NamedPKCS8Key("ML-DSA", enc, null));
        var sk1 = new NamedPKCS8Key("ML-DSA", enc, (_, n) -> n);
        var sk2 = new NamedPKCS8Key("ML-DSA", enc, (_, n) -> n);
        var raw1 = sk1.getRawBytes();
        Asserts.assertTrue(raw1 != sk2.getRawBytes());
        Asserts.assertTrue(sk1.getEncoded() != sk2.getEncoded());

        var encCopy = enc.clone(); // store a copy
        Arrays.fill(enc, (byte)0); // clean the source and the key unchanged
        Asserts.assertEqualsByteArray(encCopy, sk1.getEncoded());

        // Same with public key
        // Create a key using raw bytes
        var pk = new NamedX509Key("ML-DSA", "ML-DSA-44", raw);
        var enc2 = pk.getEncoded().clone();

        // The raw bytes array is re-used
        Asserts.assertTrue(pk.getRawBytes() == pk.getRawBytes());
        // but the encoding is different
        Asserts.assertTrue(pk.getEncoded() != pk.getEncoded());

        // When source change
        Arrays.fill(raw, (byte)0);
        // Internal raw bytes also changes
        Asserts.assertEqualsByteArray(pk.getRawBytes(), new byte[32]);
        // No guarantee on getEncoded() output, could be cached

        // Create a key using encoding
        var pk1 = new NamedX509Key("ML-DSA", enc2);
        var pk2 = new NamedX509Key("ML-DSA", enc2);
        raw1 = pk1.getRawBytes();
        Asserts.assertTrue(raw1 != pk2.getRawBytes());
        Asserts.assertTrue(pk1.getEncoded() != pk2.getEncoded());

        encCopy = enc2.clone(); // store a copy
        Arrays.fill(enc2, (byte)0); // clean the source and the key unchanged
        Asserts.assertEqualsByteArray(encCopy, pk1.getEncoded());
    }
}
