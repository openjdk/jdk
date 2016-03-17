/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidParameterException;
import java.security.ProviderException;
import java.util.HashMap;
import java.util.Arrays;

/**
 * A Cryptographic Service Provider for the Microsoft Crypto API.
 *
 * @since 1.6
 */

public final class SunMSCAPI extends Provider {

    private static final long serialVersionUID = 8622598936488630849L; //TODO

    private static final String INFO = "Sun's Microsoft Crypto API provider";

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("sunmscapi");
                return null;
            }
        });
    }

    private static final class ProviderService extends Provider.Service {
        ProviderService(Provider p, String type, String algo, String cn) {
            super(p, type, algo, cn, null, null);
        }

        ProviderService(Provider p, String type, String algo, String cn,
            String[] aliases, HashMap<String, String> attrs) {
            super(p, type, algo, cn,
                  (aliases == null? null : Arrays.asList(aliases)), attrs);
        }

        @Override
        public Object newInstance(Object ctrParamObj)
            throws NoSuchAlgorithmException {
            String type = getType();
            if (ctrParamObj != null) {
                throw new InvalidParameterException
                    ("constructorParameter not used with " + type +
                     " engines");
            }
            String algo = getAlgorithm();
            try {
                if (type.equals("SecureRandom")) {
                    if (algo.equals("Windows-PRNG")) {
                        return new PRNG();
                    }
                } else if (type.equals("KeyStore")) {
                    if (algo.equals("Windows-MY")) {
                        return new KeyStore.MY();
                    } else if (algo.equals("Windows-ROOT")) {
                        return new KeyStore.ROOT();
                    }
                } else if (type.equals("Signature")) {
                    if (algo.equals("NONEwithRSA")) {
                        return new RSASignature.Raw();
                    } else if (algo.equals("SHA1withRSA")) {
                        return new RSASignature.SHA1();
                    } else if (algo.equals("SHA256withRSA")) {
                        return new RSASignature.SHA256();
                    } else if (algo.equals("SHA384withRSA")) {
                        return new RSASignature.SHA384();
                    } else if (algo.equals("SHA512withRSA")) {
                        return new RSASignature.SHA512();
                    } else if (algo.equals("MD5withRSA")) {
                        return new RSASignature.MD5();
                    } else if (algo.equals("MD2withRSA")) {
                        return new RSASignature.MD2();
                    }
                } else if (type.equals("KeyPairGenerator")) {
                    if (algo.equals("RSA")) {
                        return new RSAKeyPairGenerator();
                    }
                } else if (type.equals("Cipher")) {
                    if (algo.equals("RSA") ||
                        algo.equals("RSA/ECB/PKCS1Padding")) {
                        return new RSACipher();
                    }
                }
            } catch (Exception ex) {
                throw new NoSuchAlgorithmException
                    ("Error constructing " + type + " for " +
                    algo + " using SunMSCAPI", ex);
            }
            throw new ProviderException("No impl for " + algo +
                " " + type);
        }
    }

    public SunMSCAPI() {
        super("SunMSCAPI", 9.0d, INFO);

        final Provider p = this;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                /*
                 * Secure random
                 */
                putService(new ProviderService(p, "SecureRandom",
                           "Windows-PRNG", "sun.security.mscapi.PRNG"));

                /*
                 * Key store
                 */
                putService(new ProviderService(p, "KeyStore",
                           "Windows-MY", "sun.security.mscapi.KeyStore$MY"));
                putService(new ProviderService(p, "KeyStore",
                           "Windows-ROOT", "sun.security.mscapi.KeyStore$ROOT"));

                /*
                 * Signature engines
                 */
                HashMap<String, String> attrs = new HashMap<>(1);
                attrs.put("SupportedKeyClasses", "sun.security.mscapi.Key");

                // NONEwithRSA must be supplied with a pre-computed message digest.
                // Only the following digest algorithms are supported: MD5, SHA-1,
                // SHA-256, SHA-384, SHA-512 and a special-purpose digest
                // algorithm which is a concatenation of SHA-1 and MD5 digests.
                putService(new ProviderService(p, "Signature",
                           "NONEwithRSA", "sun.security.mscapi.RSASignature$Raw",
                           null, attrs));
                putService(new ProviderService(p, "Signature",
                           "SHA1withRSA", "sun.security.mscapi.RSASignature$SHA1",
                           null, attrs));
                putService(new ProviderService(p, "Signature",
                           "SHA256withRSA", "sun.security.mscapi.RSASignature$SHA256",
                           new String[] { "1.2.840.113549.1.1.11", "OID.1.2.840.113549.1.1.11" },
                           attrs));
                putService(new ProviderService(p, "Signature",
                           "SHA384withRSA", "sun.security.mscapi.RSASignature$SHA384",
                           new String[] { "1.2.840.113549.1.1.12", "OID.1.2.840.113549.1.1.12" },
                           attrs));
                putService(new ProviderService(p, "Signature",
                           "SHA512withRSA", "sun.security.mscapi.RSASignature$SHA512",
                           new String[] { "1.2.840.113549.1.1.13", "OID.1.2.840.113549.1.1.13" },
                           attrs));
                putService(new ProviderService(p, "Signature",
                           "MD5withRSA", "sun.security.mscapi.RSASignature$MD5",
                           null, attrs));
                putService(new ProviderService(p, "Signature",
                           "MD2withRSA", "sun.security.mscapi.RSASignature$MD2",
                           null, attrs));

                /*
                 * Key Pair Generator engines
                 */
                attrs.clear();
                attrs.put("KeySize", "16384");
                putService(new ProviderService(p, "KeyPairGenerator",
                           "RSA", "sun.security.mscapi.RSAKeyPairGenerator",
                           null, attrs));

                /*
                 * Cipher engines
                 */
                attrs.clear();
                attrs.put("SupportedModes", "ECB");
                attrs.put("SupportedPaddings", "PKCS1PADDING");
                attrs.put("SupportedKeyClasses", "sun.security.mscapi.Key");
                putService(new ProviderService(p, "Cipher",
                           "RSA", "sun.security.mscapi.RSACipher",
                           null, attrs));
                putService(new ProviderService(p, "Cipher",
                           "RSA/ECB/PKCS1Padding", "sun.security.mscapi.RSACipher",
                           null, attrs));
                return null;
            }
        });
    }
}
