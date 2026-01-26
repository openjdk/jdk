/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.ssl;

import java.security.Provider;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static sun.security.util.SecurityConstants.PROVIDER_VER;

// This is an internal provider used in the JSSE code for DH-as-KEM
// and Hybrid KEM support. It doesn't actually get installed in the
// system's list of security providers that is searched at runtime.
// JSSE loads this provider internally.
// It registers Hybrid KeyPairGenerator, KeyFactory, and KEM
// implementations for hybrid named groups as Provider services.

public class HybridProvider {

    public static final Provider PROVIDER = new ProviderImpl();

    private static final class ProviderImpl extends Provider {
        @java.io.Serial
        private static final long serialVersionUID = 0L;

        ProviderImpl() {
            super("HybridAndDHAsKEM", PROVIDER_VER,
                    "Hybrid and DHAsKEM provider");
            put("KEM.DH", DHasKEM.class.getName());

            // Hybrid KeyPairGenerator/KeyFactory/KEM

            // The order of shares in the concatenation for group name
            // X25519MLKEM768 has been reversed as per the current
            // draft RFC.
            var attrs = Map.of("name", "X25519MLKEM768", "left", "ML-KEM-768",
                    "right", "X25519");
            putService(new HybridService(this, "KeyPairGenerator",
                    "X25519MLKEM768",
                    "sun.security.ssl.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "X25519MLKEM768",
                    "sun.security.ssl.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "X25519MLKEM768",
                    "sun.security.ssl.Hybrid$KeyFactoryImpl",
                    null, attrs));

            attrs = Map.of("name", "SecP256r1MLKEM768", "left", "secp256r1",
                    "right", "ML-KEM-768");
            putService(new HybridService(this, "KeyPairGenerator",
                    "SecP256r1MLKEM768",
                    "sun.security.ssl.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "SecP256r1MLKEM768",
                    "sun.security.ssl.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "SecP256r1MLKEM768",
                    "sun.security.ssl.Hybrid$KeyFactoryImpl",
                    null, attrs));

            attrs = Map.of("name", "SecP384r1MLKEM1024", "left", "secp384r1",
                    "right", "ML-KEM-1024");
            putService(new HybridService(this, "KeyPairGenerator",
                    "SecP384r1MLKEM1024",
                    "sun.security.ssl.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "SecP384r1MLKEM1024",
                    "sun.security.ssl.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "SecP384r1MLKEM1024",
                    "sun.security.ssl.Hybrid$KeyFactoryImpl",
                    null, attrs));
        }
    }

    private static class HybridService extends Provider.Service {

        HybridService(Provider p, String type, String algo, String cn,
                      List<String> aliases, Map<String, String> attrs) {
            super(p, type, algo, cn, aliases, attrs);
        }

        @Override
        public Object newInstance(Object ctrParamObj)
                throws NoSuchAlgorithmException {
            String type = getType();
            return switch (type) {
                case "KeyPairGenerator" -> new Hybrid.KeyPairGeneratorImpl(
                        getAttribute("left"), getAttribute("right"));
                case "KeyFactory" -> new Hybrid.KeyFactoryImpl(
                        getAttribute("left"), getAttribute("right"));
                case "KEM" -> new Hybrid.KEMImpl(
                        getAttribute("left"), getAttribute("right"));
                default -> throw new NoSuchAlgorithmException(
                        "Unexpected value: " + type);
            };
        }
    }
}
