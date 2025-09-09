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
 * @bug 8360563
 * @summary Testing getKeyPair
 * @enablePreview
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Arrays;

public class GetKeyPair {

    private static final String encEdECKey =
        """
        -----BEGIN ENCRYPTED PRIVATE KEY-----
        MIH0MF8GCSqGSIb3DQEFDTBSMDEGCSqGSIb3DQEFDDAkBBDhqUj1Oadj1GZXUMXT
        b3QEAgIIADAMBggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBAgQQitxCfcZcMtoNu+X+
        PQk+/wSBkFL1NddKkUL2tRv6pNf1TR7eI7qJReGRgJexU/6pDN+UQS5e5qSySa7E
        k1m2pUHgZlySUblXZj9nOzCsNFfq/jxlL15ZpAviAM2fRINnNEJcvoB+qZTS5cRb
        Xs3wC7wymHW3EdIZ9sxfSHq9t7j9SnC1jGHjno0v1rKcdIvJtYloxsRYjsG/Sxhz
        uNYnx8AMuQ==
        -----END ENCRYPTED PRIVATE KEY-----
        """;

    private static final String passwdText = "fish";
    private static final char[] password = passwdText.toCharArray();
    private static final SecretKey key = new SecretKeySpec(
        passwdText.getBytes(), "PBE");

    public static void main(String[] args) throws Exception {
        Provider p = Security.getProvider(
            System.getProperty("test.provider.name", "SunJCE"));

        EncryptedPrivateKeyInfo ekpi = PEMDecoder.of().decode(encEdECKey,
            EncryptedPrivateKeyInfo.class);
        PrivateKey priKey = PEMDecoder.of().withDecryption(password).
            decode(encEdECKey, PrivateKey.class);

        // Test getKey(password)
        System.out.println("Testing getKeyPair(char[]) ");
        KeyPair kp = ekpi.getKeyPair(password);
        if (!Arrays.equals(priKey.getEncoded(),
            kp.getPrivate().getEncoded())) {
            throw new AssertionError("didn't match with expected.");
        }
        System.out.println("Got KeyPair:  Pass");

        // Test getKey(key, provider) provider null
        System.out.println("Testing getKeyPair(key, provider)");
        kp = ekpi.getKeyPair(key, p);
        if (!Arrays.equals(priKey.getEncoded(),
            kp.getPrivate().getEncoded())) {
            throw new AssertionError("didn't match with expected.");
        }
        System.out.println("Got KeyPair:  Pass");
    }
}
