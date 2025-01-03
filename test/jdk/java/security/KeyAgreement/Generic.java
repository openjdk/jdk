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
 * @bug 8189441
 * @library /test/lib /test/jdk/sun/security/pkcs11
 * @summary make sure Generic is accepted by all KeyAgreement implementations
 */
import javax.crypto.KeyAgreement;
import java.security.KeyPairGenerator;
import java.security.Security;

public class Generic {

    public static void main(String[] args) throws Exception {
        Security.addProvider(PKCS11Test.getSunPKCS11(PKCS11Test.getNssConfig()));
        for (var p : Security.getProviders()) {
            for (var s : p.getServices()) {
                if (s.getType().equalsIgnoreCase("KeyAgreement")) {
                    try {
                        System.out.println(s.getProvider().getName() + "." + s.getAlgorithm());
                        var g = KeyPairGenerator.getInstance(ka2kpg(s.getAlgorithm()), p);
                        var kp1 = g.generateKeyPair();
                        var kp2 = g.generateKeyPair();
                        var ka = KeyAgreement.getInstance(s.getAlgorithm(), s.getProvider());
                        ka.init(kp1.getPrivate());
                        ka.doPhase(kp2.getPublic(), true);
                        ka.generateSecret("TlsPremasterSecret");
                        ka.init(kp1.getPrivate());
                        ka.doPhase(kp2.getPublic(), true);
                        ka.generateSecret("Generic");
                    } catch (Exception e) {
                        throw e;
                    }
                }
            }
        }
    }

    // Find key algorithm from KeyAgreement algorithm
    private static String ka2kpg(String ka) {
        return switch (ka) {
            case "ECDH" -> "EC";
            default -> ka;
        };
    }
}
