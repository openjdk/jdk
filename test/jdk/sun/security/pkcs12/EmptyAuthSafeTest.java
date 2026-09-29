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
 * @run junit EmptyAuthSafeTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import sun.security.pkcs12.PKCS12KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmptyAuthSafeTest {

    private static final char[] PASSWORD = "1234".toCharArray();

    // No authSafe content but with MacData present
    private static final String ks1 = "MFsCAQMwCwYJKoZIhvcNAQcBMEkwMTANBglghk"
            + "gBZQMEAgEFAAQgX2iKyj065lT1hA8c7H+NREUaXuTdy/W2aHjeVJNT/N0EEAAR"
            + "IjNEVWZ3iJmqu8zd7v8CAggA";

    // No authSafe content and no MacData
    private static final String ks2 = "MBACAQMwCwYJKoZIhvcNAQcB";

    // No authSafe content and no MacData, with indefinite-length PFX encoding
    private static final String ks3 = "MIACAQMwCwYJKoZIhvcNAQcBAAA=";

    @Test
    void loadAndStore() throws Exception {
        var bytes = Base64.getMimeDecoder().decode(ks1);
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), PASSWORD);
        assertEquals(0, ks.size(), "Expected no entries");

        var baos = new ByteArrayOutputStream();
        ks.store(baos, PASSWORD);
        ks.load(new ByteArrayInputStream(baos.toByteArray()), PASSWORD);
        assertEquals(0, ks.size(), "Expected no entries after storing");
    }

    @ParameterizedTest
    @MethodSource("encodedKeyStores")
    void probe(String encoded) throws Exception {
        Path file = writeKeyStore(encoded);

        KeyStore ks = KeyStore.getInstance(file.toFile(), PASSWORD);
        assertTrue(ks.getType().equalsIgnoreCase("PKCS12"),
                "Expected a PKCS12 keystore");
        assertEquals(0, ks.size(), "Expected no entries");
    }

    @ParameterizedTest
    @MethodSource("passwordlessKeyStores")
    void isPasswordless(String encoded, boolean expected) throws Exception {
        Path file = writeKeyStore(encoded);

        assertEquals(expected, PKCS12KeyStore.isPasswordless(file.toFile()));
    }

    private static Path writeKeyStore(String encoded) throws Exception {
        Path file = Files.createTempFile(
                Path.of(System.getProperty("test.classes")),
                "empty-auth-safe-", ".p12");
        Files.write(file, Base64.getMimeDecoder().decode(encoded));
        return file;
    }

    private static Stream<String> encodedKeyStores() {
        return Stream.of(ks1, ks2, ks3);
    }

    private static Stream<Arguments> passwordlessKeyStores() {
        return Stream.of(
                Arguments.of(ks1, false),
                Arguments.of(ks2, true),
                Arguments.of(ks3, true));
    }
}

