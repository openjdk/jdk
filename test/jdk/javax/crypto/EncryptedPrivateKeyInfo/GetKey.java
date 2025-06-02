/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8298420
 * @summary Testing getKey
 * @enablePreview
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PEMDecoder;
import java.security.PrivateKey;
import java.util.Arrays;

public class GetKey {

    private static final String encEdECKey =
        """
        -----BEGIN ENCRYPTED PRIVATE KEY-----
        MIGqMGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDArBBRyYnoNyrcqvubzch00
        jyuAb5YizgICEAACARAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEAQIEEM8BgEgO
        vdMyi46+Dw7cOjwEQLtx5ME0NOOo7vlCGm3H/4j+Tf5UXrMb1UrkPjqc8OiLbC0n
        IycFtI70ciPjgwDSjtCcPxR8fSxJPrm2yOJsRVo=
        -----END ENCRYPTED PRIVATE KEY-----
        """;
    private static final String passwdText = "fish";
    private static final char[] password = passwdText.toCharArray();
    private static final SecretKey key = new SecretKeySpec(
        passwdText.getBytes(), "PBE");

    public static void main(String[] args) throws Exception {
        EncryptedPrivateKeyInfo ekpi = PEMDecoder.of().decode(encEdECKey,
            EncryptedPrivateKeyInfo.class);
        PrivateKey priKey = PEMDecoder.of().withDecryption(password).
            decode(encEdECKey, PrivateKey.class);

        // Test getKey(password)
        if (!Arrays.equals(priKey.getEncoded(),
            ekpi.getKey(password).getEncoded())) {
            throw new AssertionError("getKey(char[]) didn't "
                + "match with expected.");
        }

        // Test getKey(key, provider)
        if (!Arrays.equals(priKey.getEncoded(),
            ekpi.getKey(key, null).getEncoded())) {
            throw new AssertionError("getKey(key, provider) " +
                "didn't match with expected.");
        }
    }
}
