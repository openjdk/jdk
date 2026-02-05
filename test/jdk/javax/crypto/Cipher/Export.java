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
 * @bug 8325513
 * @library /test/lib /test/jdk/security/unsignedjce
 * @build java.base/javax.crypto.ProviderVerifier
 * @run main/othervm Export
 * @summary Try out the export method
 */

import jdk.test.lib.Asserts;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

public class Export {

    public static Provider PROVIDER = new Provider("X", "X", "X") {{
        put("Cipher.X", CipherImpl.class.getName());
        put("Cipher.NX", CipherImplNoEx.class.getName());
    }};

    public static void main(String[] args) throws Exception {

        // Not supported by AES cipher.
        Cipher c0 = Cipher.getInstance("AES");
        c0.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"));
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c0.exportKey("X", s2b("one"), 32));
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c0.exportData(s2b("one"), 32));

        SecretKey key = new SecretKeySpec(s2b("key"), "X");

        // X cipher defined in this class supports exporting.
        Cipher c1 = Cipher.getInstance("X", PROVIDER);

        // Cipher not initialized
        Asserts.assertThrows(IllegalStateException.class,
                () -> c1.exportKey("X", s2b("one"), 32));

        c1.init(Cipher.ENCRYPT_MODE, key);

        // Several error cases
        Asserts.assertThrows(NullPointerException.class,
                () -> c1.exportKey(null, s2b("one"), 32));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> c1.exportKey("X", null, 32));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> c1.exportData(null, 32));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> c1.exportKey("X", s2b("one"), 0));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> c1.exportData(s2b("one"), 0));

        // Normal usages
        SecretKey sk1 = c1.exportKey("X", s2b("one"), 32);
        SecretKey sk1p = c1.exportKey("X", s2b("two"), 32);
        byte[] d1 = c1.exportData(s2b("one"), 32);
        byte[] d1p = c1.exportData(s2b("two"), 32);

        // Different context strings return different exported data
        Asserts.assertNotEqualsByteArray(sk1.getEncoded(), sk1p.getEncoded());
        Asserts.assertNotEqualsByteArray(d1, d1p);

        Cipher c2 = Cipher.getInstance("X", PROVIDER);
        c2.init(Cipher.DECRYPT_MODE, key);
        SecretKey sk2 = c2.exportKey("X", s2b("one"), 32);
        byte[] d2 = c2.exportData(s2b("one"), 32);

        // Encryptor and decryptor export the same data
        Asserts.assertEqualsByteArray(sk1.getEncoded(), sk2.getEncoded());
        Asserts.assertEqualsByteArray(d1, d2);

        // Initialized with a different key
        Cipher c3 = Cipher.getInstance("X", PROVIDER);
        c3.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(s2b("another"), "X"));
        byte[] d3 = c3.exportData(s2b("one"), 32);
        Asserts.assertNotEqualsByteArray(d1, d3);

        // NX cipher
        Cipher c4 = Cipher.getInstance("NX", PROVIDER);
        c4.init(Cipher.ENCRYPT_MODE, key);
        c4.exportKey("X", s2b("one"), 32);

        // NX does not support exportData
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c4.exportData(s2b("one"), 32));
    }

    public static class CipherImpl extends CipherSpi {

        protected void engineSetMode(String mode) { }
        protected void engineSetPadding(String padding) { }
        protected int engineGetBlockSize() { return 0; }
        protected int engineGetOutputSize(int inputLen) { return 0; }
        protected byte[] engineGetIV() { return new byte[0]; }
        protected AlgorithmParameters engineGetParameters() { return null; }
        protected void engineInit(int o, Key k, AlgorithmParameterSpec p, SecureRandom r) { }
        protected void engineInit(int o, Key k, AlgorithmParameters p, SecureRandom r) { }
        protected byte[] engineUpdate(byte[] i, int o, int l) { return new byte[0]; }
        protected int engineUpdate(byte[] i, int o, int l, byte[] op, int opo) { return 0; }
        protected byte[] engineDoFinal(byte[] i, int o, int l) { return new byte[0]; }
        protected int engineDoFinal(byte[] i, int o, int l, byte[] op, int opo) { return 0; }

        byte[] keyBytes;
        protected void engineInit(int opmode, Key key, SecureRandom random) {
            keyBytes = key.getEncoded();
        }

        @Override
        protected SecretKey engineExportKey(String algorithm, byte[] context, int length) {
            return new SecretKeySpec(exportInternal(context, length), algorithm);
        }

        @Override
        protected byte[] engineExportData(byte[] context, int length) {
            return exportInternal(context, length);
        }

        private byte[] exportInternal(byte[] context, int length) {
            if (context == null) {
                throw new IllegalArgumentException();
            }
            byte[] output = new byte[length];
            for (int i = 0; i < length; i++) {
                output[i] = (byte) (context[i % context.length] ^ keyBytes[i % keyBytes.length]);
            }
            return output;
        }
    }

    public static class CipherImplNoEx extends CipherImpl {
        @Override
        protected byte[] engineExportData(byte[] context, int length) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    static byte[] s2b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
