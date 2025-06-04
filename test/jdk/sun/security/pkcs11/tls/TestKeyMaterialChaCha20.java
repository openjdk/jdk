/*
 * Copyright (c) 2022, Red Hat, Inc.
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
 * @bug 8288985
 * @summary Tests that P11TlsKeyMaterialGenerator works with ChaCha20-Poly1305
 * @library /test/lib ..
 * @modules java.base/sun.security.internal.spec
 *          jdk.crypto.cryptoki
 * @run main/othervm TestKeyMaterialChaCha20
 */

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.Provider;
import java.security.NoSuchAlgorithmException;
import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;
import sun.security.internal.spec.TlsMasterSecretParameterSpec;
import sun.security.internal.spec.TlsKeyMaterialParameterSpec;


public class TestKeyMaterialChaCha20 extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new TestKeyMaterialChaCha20(), args);
    }

    @Override
    public void main(Provider provider) throws Exception {
        KeyGenerator kg1, kg2, kg3;
        try {
            kg1 = KeyGenerator.getInstance("SunTlsRsaPremasterSecret", provider);
        } catch (Exception e) {
            System.out.println("Skipping, SunTlsRsaPremasterSecret KeyGenerator not supported");
            return;
        }
        try {
            kg2 = KeyGenerator.getInstance("SunTls12MasterSecret", provider);
        } catch (Exception e) {
            System.out.println("Skipping, SunTls12MasterSecret KeyGenerator not supported");
            return;
        }
        try {
            kg3 = KeyGenerator.getInstance("SunTls12KeyMaterial", provider);
        } catch (Exception e) {
            System.out.println("Skipping, SunTls12KeyMaterial KeyGenerator not supported");
            return;
        }

        kg1.init(new TlsRsaPremasterSecretParameterSpec(0x0303, 0x0303));
        SecretKey preMasterSecret = kg1.generateKey();

        TlsMasterSecretParameterSpec spec = new TlsMasterSecretParameterSpec(
            preMasterSecret,
            3, 3,
            new byte[32],
            new byte[32],
            "SHA-256", 32, 64);
        kg2.init(spec);
        SecretKey masterSecret = kg2.generateKey();

        TlsKeyMaterialParameterSpec params = new TlsKeyMaterialParameterSpec(
            masterSecret, 3, 3,
            new byte[32],
            new byte[32],
            "ChaCha20-Poly1305", 32, 32,
            12, 0,
            "SHA-256", 32, 64);
        kg3.init(params);
        kg3.generateKey();
    }

}
