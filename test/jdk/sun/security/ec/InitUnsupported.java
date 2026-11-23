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

import jdk.test.lib.Asserts;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PEMDecoder;
import java.security.Signature;

/*
 * @test
 * @bug 8396906
 * @library /test/lib
 * @summary Make sure non-supported curves cannot be used to initialize ECDSA
 */
public class InitUnsupported {
    public static void main() throws Exception {
        // brainpoolP384r1 EC key pair created for this test
        var kpb = PEMDecoder.of().decode("""
                -----BEGIN PRIVATE KEY-----
                MIHHAgEAMBQGByqGSM49AgEGCSskAwMCCAEBCwSBqzCBqAIBAQQweG2UWHjH16Zl
                36zgQ1/YOAZD+LlIHnNIF7QJZx1qyNoDMDkYAJ+ZHO4yh94cUIisoAsGCSskAwMC
                CAEBC6FkA2IABGiR+gkGVG/LnQclTL2X6Fj0GYQ4idPtNseF/9Ai/amOBB33mp5f
                khRA5875DtlkwDs48Fkjk3hkld4spnORoMmZPS0Sr4dQ0zuVSlvWvDXdJTQaKfjO
                sr0z7Dalr1xWsw==
                -----END PRIVATE KEY-----
                """, KeyPair.class);

        // secp384r1 ket pair
        var kpn = KeyPairGenerator.getInstance("EC").generateKeyPair();

        // We init with supported keys first to confirm that init failure
        // on unsupported keys does not corrupt the internal state.
        Signature s = Signature.getInstance("SHA256withECDSA");

        s.initSign(kpn.getPrivate());
        Asserts.assertThrows(InvalidKeyException.class,
                () -> s.initSign(kpb.getPrivate()));
        var sig = s.sign();

        s.initVerify(kpn.getPublic());
        Asserts.assertThrows(InvalidKeyException.class,
                () -> s.initVerify(kpb.getPublic()));
        Asserts.assertTrue(s.verify(sig));
    }
}
