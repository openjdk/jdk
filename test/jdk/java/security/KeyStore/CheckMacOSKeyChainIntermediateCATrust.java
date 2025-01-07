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

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jdk.test.lib.process.ProcessTools;

import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8347067
 * @library /test/lib
 * @requires os.family == "mac"
 * @summary Check whether loading of certificates from MacOS Keychain correctly
 *          loads intermediate CA certificates
 * @run junit CheckMacOSKeyChainIntermediateCATrust
 */
public class CheckMacOSKeyChainIntermediateCATrust {

    private static final String DIR = System.getProperty("test.src", ".");

    @Test
    public void test() throws Throwable {
        KeyStore ks = KeyStore.getInstance("KeychainStore");
        ks.load(null, null);

        Iterator<String> iterator = ks.aliases().asIterator();
        List<X509Certificate> certificates = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .sorted()
                .map(alias -> {
                    try {
                        return (X509Certificate) ks.getCertificate(alias);
                    } catch (KeyStoreException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        System.out.println("Verifying expected certificates are trusted");

        String rootCASubjectName = "CN=Example CA,O=Example,C=US";
        assertThat(containsSubjectName(certificates, rootCASubjectName), "Root CA not found " + rootCASubjectName, certificates);

        String intermediateCASubjectName = "CN=Example Intermediate CA,O=Example,C=US";
        assertThat(containsSubjectName(certificates, intermediateCASubjectName), "Intermediate CA not found " + intermediateCASubjectName, certificates);
    }

    @BeforeEach
    public void setup() {
        System.out.println("Adding certificates to key chain");
        addCertificatesToKeyChain();
    }

    @AfterEach
    public void cleanup() {
        System.out.println("Cleaning up");
        deleteCertificatesFromKeyChain();
    }

    private static void addCertificatesToKeyChain() {
        String loginKeyChain = getLoginKeyChain();

        Path caPath = Path.of("%s/%s".formatted(DIR, "test-ca.pem"));
        List<String> args = List.of(
                "/usr/bin/security",
                "add-trusted-cert",
                "-k", loginKeyChain,
                caPath.toString()
        );
        executeProcess(args);

        caPath = Path.of("%s/%s".formatted(DIR, "test-intermediate-ca.pem"));
        args = List.of(
                "/usr/bin/security",
                "add-certificates",
                "-k", loginKeyChain,
                caPath.toString()
        );
        executeProcess(args);

    }

    private static String getLoginKeyChain() {
        return Path.of(System.getProperty("user.home"), "Library/Keychains/login.keychain-db").toString();
    }

    private static void executeProcess(List<String> params) {
        System.out.println("Command line: " + params);
        try {
            int exitStatus = ProcessTools.executeProcess(params.toArray(new String[0])).getExitValue();
            if (exitStatus != 0) {
                fail("Process started with: " + params + " failed");
            }
        } catch (Throwable e) {
            fail(e.getMessage());
        }
    }

    private static void deleteCertificatesFromKeyChain() {
        executeProcess(
                List.of(
                        "/usr/bin/security",
                        "delete-certificate",
                        "-c", "Example CA",
                        "-t"
                )
        );

        executeProcess(
                List.of(
                        "/usr/bin/security",
                        "delete-certificate",
                        "-c", "Example Intermediate CA",
                        "-t"
                )
        );
    }

    private static boolean containsSubjectName(List<X509Certificate> certificates, String subjectName) {
        return certificates.stream()
                .map(cert -> cert.getSubjectX500Principal().getName())
                .anyMatch(name -> name.contains(subjectName));
    }

    private static List<String> getSubjects(List<X509Certificate> certificates) {
        return certificates.stream()
                .map(cert -> cert.getSubjectX500Principal().getName())
                .toList();
    }

    private static void assertThat(boolean expected, String message, List<X509Certificate> certificates) {
        if (!expected) {
            throw new AssertionError(message + ", subjects: " + getSubjects(certificates));
        }
    }
}
