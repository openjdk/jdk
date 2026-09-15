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
 * @bug 8377980
 * @library /test/lib
 * @summary check that the default implementations of
 *          X509CRL.getThisUpdateInstant()/getNextUpdateInstant() and
 *          X509CRLEntry.getRevocationDateInstant() are consistent with
 *          their Date-returning counterparts on custom subclasses.
 * @run junit X509CRLTest
 */

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Date;
import java.util.Set;

import jdk.test.lib.security.CertUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class X509CRLTest {
    private static final String TEST_CRL =
            """
            -----BEGIN X509 CRL-----
            MIIBGzCBhQIBATANBgkqhkiG9w0BAQQFADAfMQswCQYDVQQGEwJVUzEQMA4GA1UE
            ChMHRXhhbXBsZRcNMDkwNDI3MDIzODA0WhcNMjgwNjI2MDIzODA0WjAiMCACAQUX
            DTA5MDQyNzAyMzgwMFowDDAKBgNVHRUEAwoBBKAOMAwwCgYDVR0UBAMCAQIwDQYJ
            KoZIhvcNAQEEBQADgYEAoarfzXEtw3ZDi4f9U8eSvRIipHSyxOrJC7HR/hM5VhmY
            CErChny6x9lBVg9s57tfD/P9PSzBLusCcHwHMAbMOEcTltVVKUWZnnbumpywlYyg
            oKLrE9+yCOkYUOpiRlz43/3vkEL5hjIKMcDSZnPKBZi1h16Yj2hPe9GMibNip54=
            -----END X509 CRL-----""";

    private static class TestX509CRL extends X509CRL {
        private final X509CRL crl;

        TestX509CRL(X509CRL crl) {
            this.crl = crl;
        }

        public Set<String> getCriticalExtensionOIDs() {
            return crl.getCriticalExtensionOIDs();
        }

        public byte[] getExtensionValue(String oid) {
            return crl.getExtensionValue(oid);
        }

        public Set<String> getNonCriticalExtensionOIDs() {
            return crl.getNonCriticalExtensionOIDs();
        }

        public boolean hasUnsupportedCriticalExtension() {
            return crl.hasUnsupportedCriticalExtension();
        }

        public Set<? extends X509CRLEntry> getRevokedCertificates() {
            return crl.getRevokedCertificates();
        }

        public X509CRLEntry getRevokedCertificate(BigInteger serialNumber) {
            return crl.getRevokedCertificate(serialNumber);
        }

        public boolean isRevoked(Certificate cert) {
            return crl.isRevoked(cert);
        }

        public Date getNextUpdate() {
            return crl.getNextUpdate();
        }

        public Date getThisUpdate() {
            return crl.getThisUpdate();
        }

        public int getVersion() {
            return crl.getVersion();
        }

        public Principal getIssuerDN() {
            return crl.getIssuerDN();
        }

        public byte[] getTBSCertList() throws CRLException {
            return crl.getTBSCertList();
        }

        public byte[] getSignature() {
            return crl.getSignature();
        }

        public String getSigAlgName() {
            return crl.getSigAlgName();
        }

        public String getSigAlgOID() {
            return crl.getSigAlgOID();
        }

        public byte[] getSigAlgParams() {
            return crl.getSigAlgParams();
        }

        public byte[] getEncoded() throws CRLException {
            return crl.getEncoded();
        }

        public void verify(PublicKey key) throws CRLException,
                InvalidKeyException, NoSuchAlgorithmException,
                NoSuchProviderException, SignatureException {
            crl.verify(key);
        }

        public void verify(PublicKey key, String sigProvider) throws
                CRLException, InvalidKeyException, NoSuchAlgorithmException,
                NoSuchProviderException, SignatureException {
            crl.verify(key, sigProvider);
        }

        public String toString() {
            return crl.toString();
        }
    }

    private static class TestX509CRLEntry extends X509CRLEntry {
        private final X509CRLEntry entry;

        TestX509CRLEntry(X509CRLEntry entry) {
            this.entry = entry;
        }

        public byte[] getEncoded() throws CRLException {
            return entry.getEncoded();
        }

        public BigInteger getSerialNumber() {
            return entry.getSerialNumber();
        }

        public Date getRevocationDate() {
            return entry.getRevocationDate();
        }

        public boolean hasExtensions() {
            return entry.hasExtensions();
        }

        public String toString() {
            return entry.toString();
        }

        public Set<String> getCriticalExtensionOIDs() {
            return entry.getCriticalExtensionOIDs();
        }

        public Set<String> getNonCriticalExtensionOIDs() {
            return entry.getNonCriticalExtensionOIDs();
        }

        public byte[] getExtensionValue(String oid) {
            return entry.getExtensionValue(oid);
        }

        public boolean hasUnsupportedCriticalExtension() {
            return entry.hasUnsupportedCriticalExtension();
        }
    }

    private static TestX509CRL getTestCrl() throws Exception {
        return new TestX509CRL(CertUtils.getCRLFromString(TEST_CRL));
    }

    @Test
    public void thisUpdateInstantMatchesThisUpdate() throws Exception {
        final TestX509CRL crl = getTestCrl();
        assertEquals(crl.getThisUpdate().toInstant(),
                crl.getThisUpdateInstant());
    }

    @Test
    public void nextUpdateInstantMatchesNextUpdate() throws Exception {
        final TestX509CRL crl = getTestCrl();
        assertEquals(crl.getNextUpdate().toInstant(),
                crl.getNextUpdateInstant());
    }

    @Test
    public void revocationInstantMatchesRevocationDate() throws Exception {
        final X509CRL crl = CertUtils.getCRLFromString(TEST_CRL);
        final Set<? extends X509CRLEntry> entries =
                crl.getRevokedCertificates();
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        final TestX509CRLEntry entry =
                new TestX509CRLEntry(entries.iterator().next());
        assertEquals(entry.getRevocationDate().toInstant(),
                entry.getRevocationInstant());
    }
}
