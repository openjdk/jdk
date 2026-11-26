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
 * @bug 8328184
 * @summary Certificate entries must be stored in the requested Windows store
 * @requires os.family == "windows"
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 *          jdk.crypto.mscapi/sun.security.mscapi:open
 * @run main/manual/native/othervm --enable-native-access=ALL-UNNAMED ${test.main.class}
 * @comment Run with administrative privileges because this test modifies
 *          the LOCALMACHINE store.
 */

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

public class LocalMachineStore {
    static {
        System.loadLibrary("LocalMachineStore");
    }

    private static native void copyCertWithKeyAssociationToLocalMachine(byte[] certBytes);

    public static void main(String[] args) throws Throwable {
        String alias = "jdk-8328184-" + UUID.randomUUID();

        testCertificates(alias);
        testPrivateKeys(alias);
        testCrossLocationKeyDeletion(alias);
    }

    private static void testCertificates(String alias) throws Exception {
        KeyStore localMachine = load("LOCALMACHINE");
        KeyStore currentUser;
        try {
            X509Certificate cert = generateCertificate();
            localMachine.setCertificateEntry(alias, cert);

            // Reload the stores to ensure the certificate is actually persisted
            localMachine = load("LOCALMACHINE");
            currentUser = load("CURRENTUSER");

            assertEquals(cert, localMachine.getCertificate(alias),
                    "Certificate should be in LOCALMACHINE store");
            assertFalse(currentUser.containsAlias(alias),
                    "Certificate should not be in CURRENTUSER store");

            // Add the same certificate to CURRENTUSER to verify that deleting it
            // from LOCALMACHINE leaves the CURRENTUSER copy intact.
            currentUser.setCertificateEntry(alias, cert);

            localMachine.deleteEntry(alias);

            localMachine = load("LOCALMACHINE");
            currentUser = load("CURRENTUSER");
            assertFalse(localMachine.containsAlias(alias),
                    "Certificate should be removed from LOCALMACHINE store");
            assertEquals(cert, currentUser.getCertificate(alias),
                    "Certificate should still be in CURRENTUSER store");
        } finally {
            // Clean up the certificate from both stores to avoid leaving test artifacts
            try {
                load("CURRENTUSER").deleteEntry(alias);
            } finally {
                load("LOCALMACHINE").deleteEntry(alias);
            }
        }
    }

    private static void testPrivateKeys(String alias) throws Throwable {
        KeyStore localMachine = load("LOCALMACHINE");
        KeyStore currentUser;
        try {
            CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
            keyGen.generate(2048);
            localMachine.setKeyEntry(alias, keyGen.getPrivateKey(), null,
                    new X509Certificate[]{keyGen.getSelfCertificate(
                            new X500Name("CN=" + alias),
                            24 * 60 * 60)});

            // Reload the stores to ensure the private key is actually persisted
            localMachine = load("LOCALMACHINE");
            currentUser = load("CURRENTUSER");

            assertTrue(localMachine.isKeyEntry(alias),
                    "Private key should be in LOCALMACHINE store");
            assertFalse(currentUser.containsAlias(alias),
                    "Private key should not be in CURRENTUSER store");

            // Use the private key reloaded from the store.
            PrivateKey storedKey = (PrivateKey) localMachine.getKey(alias, null);
            assertMachineKey(storedKey);

            Certificate cert = localMachine.getCertificate(alias);
            assertCanSign(cert, storedKey);

            // Verify deletion separately from clean-up.
            localMachine.deleteEntry(alias);
            localMachine = load("LOCALMACHINE");
            assertFalse(localMachine.containsAlias(alias),
                    "Private key entry should be removed from LOCALMACHINE store");
        } finally {
            // Clean up the private key from both stores to avoid leaving test artifacts
            try {
                load("CURRENTUSER").deleteEntry(alias);
            } finally {
                load("LOCALMACHINE").deleteEntry(alias);
            }
        }
    }

    private static void testCrossLocationKeyDeletion(String alias) throws Throwable {
        KeyStore currentUser = load("CURRENTUSER");
        try {
            CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
            keyGen.generate(2048);
            X509Certificate currentUserCert = keyGen.getSelfCertificate(
                    new X500Name("CN=" + alias),
                    24 * 60 * 60);
            currentUser.setKeyEntry(alias, keyGen.getPrivateKey(), null,
                    new X509Certificate[]{currentUserCert});

            // Reload the CURRENTUSER store to ensure the private key is actually persisted
            currentUser = load("CURRENTUSER");
            assertTrue(currentUser.isKeyEntry(alias),
                    "Private key should be in CURRENTUSER store");
            PrivateKey priv = (PrivateKey) currentUser.getKey(alias, null);
            assertCanSign(currentUserCert, priv);

            // Copy the certificate with its CURRENTUSER key association to LOCALMACHINE.
            copyCertWithKeyAssociationToLocalMachine(currentUserCert.getEncoded());

            KeyStore localMachine = load("LOCALMACHINE");
            assertTrue(localMachine.isKeyEntry(alias),
                       "LOCALMACHINE entry should reference the CURRENTUSER private key");
            PrivateKey localMachinePriv = (PrivateKey) localMachine.getKey(alias, null);
            assertCanSign(localMachine.getCertificate(alias), localMachinePriv);

            // Delete the entry from LOCALMACHINE and verify that the CURRENTUSER copy is still intact.
            localMachine.deleteEntry(alias);
            localMachine = load("LOCALMACHINE");
            assertFalse(localMachine.containsAlias(alias),
                    "Private key entry should be removed from LOCALMACHINE store");
            currentUser = load("CURRENTUSER");
            assertTrue(currentUser.isKeyEntry(alias),
                    "Private key should still be in CURRENTUSER store");

            PrivateKey currentUserPriv = (PrivateKey) currentUser.getKey(alias, null);
            assertCanSign(currentUser.getCertificate(alias), currentUserPriv);
        } finally {
            try {
                load("LOCALMACHINE").deleteEntry(alias);
            } finally {
                load("CURRENTUSER").deleteEntry(alias);
            }
        }
    }


    private static void assertCanSign(Certificate cert, PrivateKey privKey) throws Exception {
        byte[] data = {1, 2, 3, 4};

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privKey);
        signer.update(data);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert);
        verifier.update(data);
        assertTrue(verifier.verify(signature),
                "Signature made with the stored private key should verify");
    }

    private static void assertMachineKey(PrivateKey key) throws Throwable {
        final int PP_KEYSET_TYPE = 27;
        final int CRYPT_MACHINE_KEYSET = 0x20;

        Class<?> cCKey = Class.forName("sun.security.mscapi.CKey");
        Method mGetKey = cCKey.getDeclaredMethod("getHCryptKey");
        Method mGetProvider = cCKey.getDeclaredMethod("getHCryptProvider");
        mGetKey.setAccessible(true);
        mGetProvider.setAccessible(true);

        assertTrue((long) mGetKey.invoke(key) != 0, "Expected a CAPI key");

        try (Arena arena = Arena.ofConfined()) {
            long provider = (long) mGetProvider.invoke(key);

            SymbolLookup advapi32 = SymbolLookup.libraryLookup("Advapi32.dll", arena);

            // BOOL CryptGetProvParam(HCRYPTPROV hProv, DWORD dwParam, BYTE *pbData,
            //                        DWORD *pdwDataLen, DWORD dwFlags);
            MethodHandle mCryptgetProvParam = Linker.nativeLinker().downcallHandle(
                    advapi32.find("CryptGetProvParam").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT,
                            JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT),
                    Linker.Option.captureCallState("GetLastError"));

            StructLayout stateLayout = Linker.Option.captureStateLayout();
            MemorySegment state = arena.allocate(stateLayout);
            MemorySegment keysetType = arena.allocate(JAVA_INT);
            MemorySegment size = arena.allocate(JAVA_INT);
            size.set(JAVA_INT, 0, Integer.BYTES);

            int result = (int) mCryptgetProvParam.invokeExact(
                    state, provider, PP_KEYSET_TYPE, keysetType, size, 0);

            if (result == 0) {
                int lastError = state.get(
                        JAVA_INT, stateLayout.byteOffset(groupElement("GetLastError")));
                throw new AssertionError("CryptGetProvParam failed with error code: 0x"
                        + Integer.toHexString(lastError));
            }

            assertTrue((keysetType.get(JAVA_INT, 0) & CRYPT_MACHINE_KEYSET) != 0,
                    "Expected a machine key");
        } finally {
            // Keep the key alive until after the native call
            Reference.reachabilityFence(key);
        }
    }

    private static KeyStore load(String type) throws Exception {
        KeyStore ks = KeyStore.getInstance("Windows-MY-" + type);
        ks.load(null, null);
        return ks;
    }

    private static X509Certificate generateCertificate() throws Exception {
        CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
        keyGen.generate(2048);
        return keyGen.getSelfCertificate(
                new X500Name("CN=JDK-8328184-" + UUID.randomUUID()),
                24 * 60 * 60
        );
    }
}
