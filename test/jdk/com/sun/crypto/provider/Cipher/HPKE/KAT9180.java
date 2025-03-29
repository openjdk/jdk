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

/*
 * @test
 * @bug 8325448
 * @summary KAT inside RFC 9180
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 */
import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;
import jdk.test.lib.json.JSONValue;
import jtreg.SkippedException;

import com.sun.crypto.provider.DHKEM;

import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

/// This test is baed on Appendix A (Test Vectors) of
/// [RFC 9180](https://datatracker.ietf.org/doc/html/rfc9180#name-test-vectors)
/// The test data is available as a JSON file at:
/// https://github.com/cfrg/draft-irtf-cfrg-hpke/blob/5f503c564da00b0687b3de75f1dfbdfc4079ad31/test-vectors.json.
///
/// The JSON file can either be hosted on an artifactory server or
/// provided via a local path with
/// ```
/// jtreg -Djdk.test.lib.artifacts.rfc9180-test-vectors=<local-json-file> KAT9180.java
/// ```
public class KAT9180 {

    @Artifact(
            organization = "jpg.tests.jdk.ietf",
            name = "rfc9180-test-vectors",
            revision = "5f503c5",
            extension = "json",
            unpack = false)
    private static class RFC_9180_KAT {
    }

    private static Path fetchACVPServerTests(Class<?> clazz) {
        try {
            return ArtifactResolver.resolve(clazz).entrySet().stream()
                    .findAny().get().getValue();
        } catch (ArtifactResolverException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new SkippedException("Cannot resolve artifact, "
                        + "please check if JIB jar is present in classpath.", e);
            }

            throw new SkippedException("Fetch artifact failed: " + clazz, e);
        }
    }

    static final HexFormat h = HexFormat.of();

    public static void main(String[] args) throws Exception {
        Path archivePath = fetchACVPServerTests(RFC_9180_KAT.class);
        System.out.println("Data path: " + archivePath);
        var c1 = Cipher.getInstance("HPKE");
        var c2 = Cipher.getInstance("HPKE");
        var ts = JSONValue.parse(new String(Files.readAllBytes(archivePath), StandardCharsets.UTF_8));
        for (var tg : ts.asArray()) {
            var mode = Integer.parseInt(tg.get("mode").asString());
            System.err.print('I');
            var kem_id = Integer.parseInt(tg.get("kem_id").asString());
            var kdf_id = Integer.parseInt(tg.get("kdf_id").asString());
            var aead_id = Integer.parseInt(tg.get("aead_id").asString());
            var ikmR = h.parseHex(tg.get("ikmR").asString());
            var ikmE = h.parseHex(tg.get("ikmE").asString());
            var info = h.parseHex(tg.get("info").asString());

            var kpR = new DHKEM.RFC9180DeriveKeyPairSR(ikmR).derive(kem_id);
            var spec = HPKEParameterSpec.of(kem_id, kdf_id, aead_id).info(info);
            var rand = new DHKEM.RFC9180DeriveKeyPairSR(ikmE);

            if (mode == 1 || mode == 3) {
                spec = spec.psk(
                        new SecretKeySpec(h.parseHex(tg.get("psk").asString()), "Generic"),
                        h.parseHex(tg.get("psk_id").asString()));
            }
            if (mode == 0 || mode == 1) {
                c1.init(Cipher.ENCRYPT_MODE, kpR.getPublic(), spec, rand);
                c2.init(Cipher.DECRYPT_MODE, kpR.getPrivate(),
                        spec.encapsulation(c1.getIV()));
            } else {
                var ikmS = h.parseHex(tg.get("ikmS").asString());
                var kpS = new DHKEM.RFC9180DeriveKeyPairSR(ikmS).derive(kem_id);
                c1.init(Cipher.ENCRYPT_MODE, kpR.getPublic(),
                        spec.authKey(kpS.getPrivate()), rand);
                c2.init(Cipher.DECRYPT_MODE, kpR.getPrivate(),
                        spec.encapsulation(c1.getIV()).authKey(kpS.getPublic()));
            }
            var enc = tg.get("encryptions");
            if (enc != null) {
                System.err.print('e');
                var count = 0;
                for (var p : enc.asArray()) {
                    var aad = h.parseHex(p.get("aad").asString());
                    var pt = h.parseHex(p.get("pt").asString());
                    var ct = h.parseHex(p.get("ct").asString());
                    c1.updateAAD(aad);
                    var ct1 = c1.doFinal(pt);
                    assertEQ(ct, ct1);
                    c2.updateAAD(aad);
                    var pt1 = c2.doFinal(ct);
                    assertEQ(pt, pt1);
                    count++;
                }
                System.err.print(count);
            }
        }
    }

    private static void assertEQ(byte[] s1, byte[] s2) {
        if (!Arrays.equals(s1, s2)) {
            System.out.println(h.formatHex(s1));
            System.out.println(h.formatHex(s2));
            throw new RuntimeException("not same");
        }
    }
}
