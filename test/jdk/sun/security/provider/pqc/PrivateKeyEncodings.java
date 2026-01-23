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
 * @bug 8347938
 * @library /test/lib
 * @summary ensure ML-KEM and ML-DSA encodings consistent with
 *      draft-ietf-lamps-kyber-certificates-11 and RFC 9881
 * @modules java.base/com.sun.crypto.provider
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.provider
 * @run main/othervm PrivateKeyEncodings
 */
import com.sun.crypto.provider.ML_KEM_Impls;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.RepositoryFileReader;
import jdk.test.lib.security.FixedSecureRandom;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.ML_DSA_Impls;

import javax.crypto.KEM;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class PrivateKeyEncodings {

    public static void main(String[] args) throws Exception {
        // Example keys and certificates draft-ietf-lamps-kyber-certificates-11, Appendix B
        // (https://datatracker.ietf.org/doc/html/draft-ietf-lamps-kyber-certificates-11#autoid-17)
        // and RFC 9881, Appendix C.3
        // (https://datatracker.ietf.org/doc/html/rfc9881#name-example-certificates)
        //
        // These data can be retrieved from the following GitHub releases:
        //   https://github.com/lamps-wg/kyber-certificates/releases/tag/draft-ietf-lamps-kyber-certificates-11
        //   https://github.com/lamps-wg/dilithium-certificates/releases/tag/draft-ietf-lamps-dilithium-certificates-13
        //
        // Although the release tags include "draft", these values are the
        // same as those in the final RFC 9881.
        try (var kemReader = RepositoryFileReader.of(RepositoryFileReader.KYBER_CERTIFICATES.class,
                    "kyber-certificates-draft-ietf-lamps-kyber-certificates-11/");
             var dsaReader = RepositoryFileReader.of(RepositoryFileReader.DILITHIUM_CERTIFICATES.class,
                     "dilithium-certificates-draft-ietf-lamps-dilithium-certificates-13/")) {
            good(kemReader, dsaReader);
            badkem(kemReader);
            baddsa(dsaReader);
        }
    }

    static void badkem(RepositoryFileReader f) throws Exception {
        var kf = KeyFactory.getInstance("ML-KEM");

        // The first ML-KEM-512-PrivateKey example includes the both CHOICE,
        // i.e., both seed and expandedKey are included. The seed and expanded
        // values can be checked for inconsistencies.
        Asserts.assertThrows(InvalidKeySpecException.class, () ->
                kf.generatePrivate(new PKCS8EncodedKeySpec(
                        readData(f, "example/bad-ML-KEM-512-1.priv"))));

        // The second ML-KEM-512-PrivateKey example includes only expandedKey.
        // The expanded private key has a mutated s_0 and a valid public key hash,
        // but a pairwise consistency check would find that the public key
        // fails to match private.
        var k2 = kf.generatePrivate(new PKCS8EncodedKeySpec(
                readData(f, "example/bad-ML-KEM-512-2.priv")));
        var pk2 = ML_KEM_Impls.privKeyToPubKey((NamedPKCS8Key) k2);
        var enc = KEM.getInstance("ML-KEM").newEncapsulator(pk2).encapsulate();
        var dk = KEM.getInstance("ML-KEM").newDecapsulator(k2).decapsulate(enc.encapsulation());
        Asserts.assertNotEqualsByteArray(enc.key().getEncoded(), dk.getEncoded());

        // The third ML-KEM-512-PrivateKey example includes only expandedKey.
        // The expanded private key has a mutated H(ek); both a public key
        // digest check and a pairwise consistency check should fail.
        var k3 = kf.generatePrivate(new PKCS8EncodedKeySpec(
                readData(f, "example/bad-ML-KEM-512-3.priv")));
        Asserts.assertThrows(InvalidKeyException.class, () ->
                KEM.getInstance("ML-KEM").newDecapsulator(k3));

        // The fourth ML-KEM-512-PrivateKey example includes the both CHOICE,
        // i.e., both seed and expandedKey are included. There is mismatch
        // of the seed and expanded private key in only the z implicit rejection
        // secret; here the private and public vectors match and the pairwise
        // consistency check passes, but z is different.
        Asserts.assertThrows(InvalidKeySpecException.class, () ->
                kf.generatePrivate(new PKCS8EncodedKeySpec(
                        readData(f, "example/bad-ML-KEM-512-4.priv"))));
    }

    static void baddsa(RepositoryFileReader f) throws Exception {
        var kf = KeyFactory.getInstance("ML-DSA");

        // The first ML-DSA-PrivateKey example includes the both CHOICE, i.e.,
        // both seed and expandedKey are included. The seed and expanded values
        // can be checked for inconsistencies.
        Asserts.assertThrows(InvalidKeySpecException.class, () ->
                kf.generatePrivate(new PKCS8EncodedKeySpec(
                        readData(f, "examples/bad-ML-DSA-44-1.priv"))));

        // The second ML-DSA-PrivateKey example includes only expandedKey.
        // The public key fails to match the tr hash value in the private key.
        var k2 = kf.generatePrivate(new PKCS8EncodedKeySpec(
                readData(f, "examples/bad-ML-DSA-44-2.priv")));
        Asserts.assertThrows(IllegalArgumentException.class, () ->
                ML_DSA_Impls.privKeyToPubKey((NamedPKCS8Key) k2));

        // The third ML-DSA-PrivateKey example also includes only expandedKey.
        // The private s_1 and s_2 vectors imply a t vector whose private low
        // bits do not match the t_0 vector portion of the private key
        // (its high bits t_1 are the primary content of the public key).
        var k3 = kf.generatePrivate(new PKCS8EncodedKeySpec(
                readData(f, "examples/bad-ML-DSA-44-3.priv")));
        Asserts.assertThrows(IllegalArgumentException.class, () ->
                ML_DSA_Impls.privKeyToPubKey((NamedPKCS8Key) k3));
    }

    static void good(RepositoryFileReader kemReader, RepositoryFileReader dsaReader)
            throws Exception {

        var seed = new byte[64];
        for (var i = 0; i < seed.length; i++) {
            seed[i] = (byte) i;
        }
        var cf = CertificateFactory.getInstance("X.509");
        var allPublicKeys = new HashMap<String, PublicKey>();

        for (var pname: List.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87", // DSA first, will sign KEM
                "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024")) {

            var isKem = pname.startsWith("ML-KEM");
            KeyPairGenerator g = KeyPairGenerator.getInstance(isKem ? "ML-KEM" : "ML-DSA");
            var prop = isKem ? "mlkem" : "mldsa";
            var f = isKem ? kemReader : dsaReader;
            var example = isKem ? "example/" : "examples/";

            g.initialize(new NamedParameterSpec(pname), new FixedSecureRandom(seed));
            var pk = g.generateKeyPair().getPublic();
            allPublicKeys.put(pname, pk);
            Asserts.assertEqualsByteArray(readData(f, example + pname + ".pub"), pk.getEncoded());

            var in = new ByteArrayInputStream(readData(f, example + pname + ".crt"));
            var c = cf.generateCertificate(in);
            var signer = switch (pname) {
                case "ML-KEM-512" -> allPublicKeys.get("ML-DSA-44");
                case "ML-KEM-768" -> allPublicKeys.get("ML-DSA-65");
                case "ML-KEM-1024" -> allPublicKeys.get("ML-DSA-87");
                default -> c.getPublicKey();
            };
            c.verify(signer);
            Asserts.assertEquals(c.getPublicKey(), pk);

            for (var type : List.of("seed", "expandedkey", "both")) {
                System.err.println(pname + " " + type);
                System.setProperty("jdk." + prop + ".pkcs8.encoding", type);
                g.initialize(new NamedParameterSpec(pname), new FixedSecureRandom(seed));
                var sk = g.generateKeyPair().getPrivate();
                if (type.equals("expandedkey")) type = "expanded";
                Asserts.assertEqualsByteArray(
                        readData(f, example + pname + "-" + type + ".priv"), sk.getEncoded());
                checkInterop(pk, sk);
            }
        }
    }

    // Ensures pk and sk interop with each other
    static void checkInterop(PublicKey pk, PrivateKey sk) throws Exception {
        if (pk.getAlgorithm().startsWith("ML-KEM")) {
            var kem = KEM.getInstance("ML-KEM");
            var enc = kem.newEncapsulator(pk).encapsulate();
            var k = kem.newDecapsulator(sk).decapsulate(enc.encapsulation());
            Asserts.assertEqualsByteArray(k.getEncoded(), enc.key().getEncoded());
        } else {
            var msg = "hello".getBytes(StandardCharsets.UTF_8);
            var s = Signature.getInstance("ML-DSA");
            s.initSign(sk);
            s.update(msg);
            var sig = s.sign();
            s.initVerify(pk);
            s.update(msg);
            Asserts.assertTrue(s.verify(sig));
        }
    }

    static byte[] readData(RepositoryFileReader f, String entry) throws Exception {
        byte[] data = f.read(entry);
        var pem = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))
                .lines()
                .filter(s -> !s.contains("-----"))
                .collect(Collectors.joining());
        return Base64.getMimeDecoder().decode(pem);
    }
}
