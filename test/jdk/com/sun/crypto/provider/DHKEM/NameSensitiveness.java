/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318328
 * @summary DHKEM should check XDH name in case-insensitive mode
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 */
import javax.crypto.KEM;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.XECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

public class NameSensitiveness {
    public static void main(String[] args) throws Exception {
        var g = KeyPairGenerator.getInstance("XDH");
        g.initialize(NamedParameterSpec.X25519);
        var pk1 = (XECPublicKey) g.generateKeyPair().getPublic();
        var pk2 = new XECPublicKey() {
            public BigInteger getU() {
                return pk1.getU();
            }
            public AlgorithmParameterSpec getParams() {
                return new NamedParameterSpec("x25519"); // lowercase!!!
            }
            public String getAlgorithm() {
                return pk1.getAlgorithm();
            }
            public String getFormat() {
                return pk1.getFormat();
            }
            public byte[] getEncoded() {
                return pk1.getEncoded();
            }
        };
        var kem = KEM.getInstance("DHKEM");
        kem.newEncapsulator(pk2);
    }
}
