/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326087
 * @summary Verify keystore loads when authSafe content is absent.
 * @modules java.base/sun.security.pkcs12
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

import sun.security.pkcs12.PKCS12KeyStore;

public class EmptyAuthSafeTest {

    private static final char[] PASSWORD = "1234".toCharArray();

    // No authSafe content but with MacData present
    private static final String ks1 = "MFsCAQMwCwYJKoZIhvcNAQcBMEkwMTANBglghk"
            + "gBZQMEAgEFAAQgX2iKyj065lT1hA8c7H+NREUaXuTdy/W2aHjeVJNT/N0EEAAR"
            + "IjNEVWZ3iJmqu8zd7v8CAggA";

    // No authSafe content and no MacData
    private static final String ks2 = "MBACAQMwCwYJKoZIhvcNAQcB";

    public static void main(String[] args) throws Exception {

        assertLoadAndStore(ks1);

        assertProbe(ks1);
        assertProbe(ks2);

        assertIsPasswordless(ks1, false);
        assertIsPasswordless(ks2, true);
    }

    static void assertLoadAndStore(String data) throws Exception {
        var bytes = Base64.getMimeDecoder().decode(data);
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), PASSWORD);
        if (ks.size() != 0) {
            throw new Exception("Expected no entries");
        }
        var baos = new ByteArrayOutputStream();
        ks.store(baos, PASSWORD);
        var newBytes = baos.toByteArray();
        var bais = new ByteArrayInputStream(newBytes);
        ks.load(bais, PASSWORD);
        if (ks.size() != 0) {
            throw new Exception("Expected no entries");
        }
    }

    private static void assertIsPasswordless(
            String encoded, boolean expected) throws Exception {
        Path keyStoreFile = Files.createTempFile(
                Path.of(System.getProperty("test.classes")),
                "empty-auth-safe-", ".p12");
        Files.write(keyStoreFile, Base64.getMimeDecoder().decode(encoded));

        boolean actual = PKCS12KeyStore.isPasswordless(keyStoreFile.toFile());
        if (actual != expected) {
            throw new Exception("Expected isPasswordless() to return "
                    + expected + ", got " + actual);
        }
    }

    private static void assertProbe(String encoded) throws Exception {
        Path file = Files.createTempFile(
                Path.of(System.getProperty("test.classes")),
                "empty-auth-safe-", ".p12");
        Files.write(file, Base64.getMimeDecoder().decode(encoded));

        KeyStore ks = KeyStore.getInstance(file.toFile(), PASSWORD);
        if (!ks.getType().equalsIgnoreCase("PKCS12") || ks.size() != 0) {
            throw new Exception("PKCS12 keystore was not correctly probed");
        }
    }
}
