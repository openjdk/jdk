/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8265765
 * @summary Test DomainKeyStore with a collection of keystores that has an empty one in between
 *          based on the test in the bug report
 */

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DomainLoadStoreParameter;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.KeyGenerator;

public class DksWithEmptyKeystore {
    private static void write(Path p, KeyStore keystore) throws Exception {
        try (OutputStream outputStream = Files.newOutputStream(p)) {
            keystore.store(outputStream, new char[] { 'x' });
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);

        // Create a keystore with one key
        KeyStore nonEmptyKeystore = KeyStore.getInstance("PKCS12");
        nonEmptyKeystore.load(null, null);

        Path nonEmptyPath = Path.of("non_empty.p12");
        nonEmptyKeystore.setKeyEntry("aeskey", kg.generateKey(), new char[] { 'a' }, null);
        write(nonEmptyPath, nonEmptyKeystore);

        // Create an empty keystore
        KeyStore emptyKeystore = KeyStore.getInstance("PKCS12");
        emptyKeystore.load(null, null);

        Path emptyPath = Path.of("empty.p12");
        write(emptyPath, emptyKeystore);

        // Create a domain keystore with two non-empty keystores
        Path dksWithTwoPartsPath = Path.of("two-parts.dks");
        var twoPartsConfiguration = """
                domain Combo {
                    keystore a keystoreURI="%s";
                    keystore b keystoreURI="%s";
                };
                """;
        Files.writeString(dksWithTwoPartsPath, String.format(twoPartsConfiguration,
                nonEmptyPath.toUri(), nonEmptyPath.toUri()));
        Map<String,KeyStore.ProtectionParameter> protectionParameters = new LinkedHashMap<>();

        KeyStore dksKeystore = KeyStore.getInstance("DKS");
        dksKeystore.load(new DomainLoadStoreParameter(dksWithTwoPartsPath.toUri(), protectionParameters));
        System.out.printf("%s size: %d%n", dksWithTwoPartsPath, dksKeystore.size());

        int index = 0;
        for (Enumeration<String> enumeration = dksKeystore.aliases(); enumeration.hasMoreElements(); ) {
            System.out.printf("%d: %s%n", index, enumeration.nextElement());
            index++;
        }

        System.out.printf("enumerated aliases from %s: %d%n", dksWithTwoPartsPath, index);
        if (index != dksKeystore.size()) {
            throw new Exception("Failed to get the number of aliases in the domain keystore " +
                    "that has two keystores.");
        }

        // Create a domain keystore with two non-empty keystores and an empty one in between
        Path dksWithThreePartsPath = Path.of("three-parts.dks");
        var threePartsConfiguration = """
                domain Combo {
                    keystore a keystoreURI="%s";
                    keystore b keystoreURI="%s";
                    keystore c keystoreURI="%s";
                };
                """;
        Files.writeString(dksWithThreePartsPath, String.format(threePartsConfiguration,
                nonEmptyPath.toUri(), emptyPath.toUri(), nonEmptyPath.toUri()));

        KeyStore dksKeystore1 = KeyStore.getInstance("DKS");
        dksKeystore1.load(new DomainLoadStoreParameter(dksWithThreePartsPath.toUri(), protectionParameters));
        System.out.printf("%s size: %d%n", dksWithThreePartsPath, dksKeystore1.size());

        index = 0;
        for (Enumeration<String> enumeration = dksKeystore1.aliases(); enumeration.hasMoreElements(); ) {
            System.out.printf("%d: %s%n", index, enumeration.nextElement());
            index++;
        }

        System.out.printf("enumerated aliases from %s: %d%n", dksWithThreePartsPath, index);
        if (index != dksKeystore1.size()) {
            throw new Exception("Failed to get the number of aliases in the domain keystore " +
                    "that has three keystores with an empty one in between.");
        } else {
            System.out.printf("Test completed successfully");
        }
    }
}
