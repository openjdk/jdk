/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320597
 * @summary Verify RSA signature with omitted digest params (should be encoded as NULL)
 * for backward compatibility
 */
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class WithoutNULL {
    public static void main(String[] args) throws Exception {

        // A 1024-bit RSA public key
        byte[] key = Base64.getMimeDecoder().decode("""
                MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrfTrEm4KvdFSpGAM7InrFEzALTKdphT9fK6Gu
                eVjHtKsuCSEaULCdjhJvPpFK40ONr1JEC1Ywp1UYrfBBdKunnbDZqNZL1cFv+IzF4Yj6JO6pOeHi
                1Zpur1GaQRRlYTvzmyWY/AATQDh8JfKObNnDVwXeezFODUG8h5+XL1ZXZQIDAQAB""");

        // A SHA1withRSA signature on an empty input where the digestAlgorithm
        // inside DigestInfo does not have a parameters field.
        byte[] sig = Base64.getMimeDecoder().decode("""
                D1FpiT44WEXlDfYK880bdorLO+e9qJVXZWiBgqs9dfK7lYQwyEt9dL23mbUAKm5TVEj2ZxtHkEvk
                b8oaWkxk069jDTM1RhllPJZkAjeQRbw4gkg4N6wKZz9B/jdSRMNJg/b9QdRYZOHOBxsEHMbUREPV
                DoCOLaxB8eIXX0EWkiE=""");

        Signature s = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        s.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        if (!s.verify(sig)) {
            throw new RuntimeException("Does not verify");
        }
    }
}
