/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.AlgorithmParameters;

import javax.net.ssl.*;

import java.security.Key;

import java.util.Set;
import java.util.HashSet;

import sun.security.util.DisabledAlgorithmConstraints;
import sun.security.ssl.CipherSuite.*;

/**
 * Algorithm constraints for disabled algorithms property
 *
 * See the "jdk.certpath.disabledAlgorithms" specification in java.security
 * for the syntax of the disabled algorithm string.
 */
final class SSLAlgorithmConstraints implements AlgorithmConstraints {
    private final static AlgorithmConstraints tlsDisabledAlgConstraints =
            new TLSDisabledAlgConstraints();
    private final static AlgorithmConstraints x509DisabledAlgConstraints =
            new X509DisabledAlgConstraints();
    private AlgorithmConstraints userAlgConstraints = null;
    private AlgorithmConstraints peerAlgConstraints = null;

    private boolean enabledX509DisabledAlgConstraints = true;

    SSLAlgorithmConstraints(AlgorithmConstraints algorithmConstraints) {
        userAlgConstraints = algorithmConstraints;
    }

    SSLAlgorithmConstraints(SSLSocket socket,
            boolean withDefaultCertPathConstraints) {
        if (socket != null) {
            userAlgConstraints =
                socket.getSSLParameters().getAlgorithmConstraints();
        }

        if (!withDefaultCertPathConstraints) {
            enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLEngine engine,
            boolean withDefaultCertPathConstraints) {
        if (engine != null) {
            userAlgConstraints =
                engine.getSSLParameters().getAlgorithmConstraints();
        }

        if (!withDefaultCertPathConstraints) {
            enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLSocket socket, String[] supportedAlgorithms,
            boolean withDefaultCertPathConstraints) {
        if (socket != null) {
            userAlgConstraints =
                socket.getSSLParameters().getAlgorithmConstraints();
            peerAlgConstraints =
                new SupportedSignatureAlgorithmConstraints(supportedAlgorithms);
        }

        if (!withDefaultCertPathConstraints) {
            enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLEngine engine, String[] supportedAlgorithms,
            boolean withDefaultCertPathConstraints) {
        if (engine != null) {
            userAlgConstraints =
                engine.getSSLParameters().getAlgorithmConstraints();
            peerAlgConstraints =
                new SupportedSignatureAlgorithmConstraints(supportedAlgorithms);
        }

        if (!withDefaultCertPathConstraints) {
            enabledX509DisabledAlgConstraints = false;
        }
    }

    public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

        boolean permitted = true;

        if (peerAlgConstraints != null) {
            permitted = peerAlgConstraints.permits(
                                    primitives, algorithm, parameters);
        }

        if (permitted && userAlgConstraints != null) {
            permitted = userAlgConstraints.permits(
                                    primitives, algorithm, parameters);
        }

        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(
                                    primitives, algorithm, parameters);
        }

        if (permitted && enabledX509DisabledAlgConstraints) {
            permitted = x509DisabledAlgConstraints.permits(
                                    primitives, algorithm, parameters);
        }

        return permitted;
    }

    public boolean permits(Set<CryptoPrimitive> primitives, Key key) {

        boolean permitted = true;

        if (peerAlgConstraints != null) {
            permitted = peerAlgConstraints.permits(primitives, key);
        }

        if (permitted && userAlgConstraints != null) {
            permitted = userAlgConstraints.permits(primitives, key);
        }

        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(primitives, key);
        }

        if (permitted && enabledX509DisabledAlgConstraints) {
            permitted = x509DisabledAlgConstraints.permits(primitives, key);
        }

        return permitted;
    }

    public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        boolean permitted = true;

        if (peerAlgConstraints != null) {
            permitted = peerAlgConstraints.permits(
                                    primitives, algorithm, key, parameters);
        }

        if (permitted && userAlgConstraints != null) {
            permitted = userAlgConstraints.permits(
                                    primitives, algorithm, key, parameters);
        }

        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(
                                    primitives, algorithm, key, parameters);
        }

        if (permitted && enabledX509DisabledAlgConstraints) {
            permitted = x509DisabledAlgConstraints.permits(
                                    primitives, algorithm, key, parameters);
        }

        return permitted;
    }


    static private class SupportedSignatureAlgorithmConstraints
                                    implements AlgorithmConstraints {
        // supported signature algorithms
        private String[] supportedAlgorithms;

        SupportedSignatureAlgorithmConstraints(String[] supportedAlgorithms) {
            if (supportedAlgorithms != null) {
                this.supportedAlgorithms = supportedAlgorithms.clone();
            } else {
                this.supportedAlgorithms = null;
            }
        }

        public boolean permits(Set<CryptoPrimitive> primitives,
                String algorithm, AlgorithmParameters parameters) {

            if (algorithm == null || algorithm.length() == 0) {
                throw new IllegalArgumentException(
                        "No algorithm name specified");
            }

            if (primitives == null || primitives.isEmpty()) {
                throw new IllegalArgumentException(
                        "No cryptographic primitive specified");
            }

            if (supportedAlgorithms == null ||
                        supportedAlgorithms.length == 0) {
                return false;
            }

            // trim the MGF part: <digest>with<encryption>and<mgf>
            int position = algorithm.indexOf("and");
            if (position > 0) {
                algorithm = algorithm.substring(0, position);
            }

            for (String supportedAlgorithm : supportedAlgorithms) {
                if (algorithm.equalsIgnoreCase(supportedAlgorithm)) {
                    return true;
                }
            }

            return false;
        }

        final public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        final public boolean permits(Set<CryptoPrimitive> primitives,
                String algorithm, Key key, AlgorithmParameters parameters) {

            if (algorithm == null || algorithm.length() == 0) {
                throw new IllegalArgumentException(
                        "No algorithm name specified");
            }

            return permits(primitives, algorithm, parameters);
        }
    }

    static private class BasicDisabledAlgConstraints
            extends DisabledAlgorithmConstraints {
        BasicDisabledAlgConstraints(String propertyName) {
            super(propertyName);
        }

        protected Set<String> decomposes(KeyExchange keyExchange,
                        boolean forCertPathOnly) {
            Set<String> components = new HashSet<String>();
            switch (keyExchange) {
                case K_NULL:
                    if (!forCertPathOnly) {
                        components.add("NULL");
                    }
                    break;
                case K_RSA:
                    components.add("RSA");
                    break;
                case K_RSA_EXPORT:
                    components.add("RSA");
                    components.add("RSA_EXPORT");
                    break;
                case K_DH_RSA:
                    components.add("RSA");
                    components.add("DH");
                    components.add("DiffieHellman");
                    components.add("DH_RSA");
                    break;
                case K_DH_DSS:
                    components.add("DSA");
                    components.add("DSS");
                    components.add("DH");
                    components.add("DiffieHellman");
                    components.add("DH_DSS");
                    break;
                case K_DHE_DSS:
                    components.add("DSA");
                    components.add("DSS");
                    components.add("DH");
                    components.add("DHE");
                    components.add("DiffieHellman");
                    components.add("DHE_DSS");
                    break;
                case K_DHE_RSA:
                    components.add("RSA");
                    components.add("DH");
                    components.add("DHE");
                    components.add("DiffieHellman");
                    components.add("DHE_RSA");
                    break;
                case K_DH_ANON:
                    if (!forCertPathOnly) {
                        components.add("ANON");
                        components.add("DH");
                        components.add("DiffieHellman");
                        components.add("DH_ANON");
                    }
                    break;
                case K_ECDH_ECDSA:
                    components.add("ECDH");
                    components.add("ECDSA");
                    components.add("ECDH_ECDSA");
                    break;
                case K_ECDH_RSA:
                    components.add("ECDH");
                    components.add("RSA");
                    components.add("ECDH_RSA");
                    break;
                case K_ECDHE_ECDSA:
                    components.add("ECDHE");
                    components.add("ECDSA");
                    components.add("ECDHE_ECDSA");
                    break;
                case K_ECDHE_RSA:
                    components.add("ECDHE");
                    components.add("RSA");
                    components.add("ECDHE_RSA");
                    break;
                case K_ECDH_ANON:
                    if (!forCertPathOnly) {
                        components.add("ECDH");
                        components.add("ANON");
                        components.add("ECDH_ANON");
                    }
                    break;
                case K_KRB5:
                    if (!forCertPathOnly) {
                        components.add("KRB5");
                    }
                    break;
                case K_KRB5_EXPORT:
                    if (!forCertPathOnly) {
                        components.add("KRB5_EXPORT");
                    }
                    break;
                default:
                    // ignore
            }

            return components;
        }

        protected Set<String> decomposes(BulkCipher bulkCipher) {
            Set<String> components = new HashSet<String>();

            if (bulkCipher.transformation != null) {
                components.addAll(super.decomposes(bulkCipher.transformation));
            }

            return components;
        }

        protected Set<String> decomposes(MacAlg macAlg) {
            Set<String> components = new HashSet<String>();

            if (macAlg == CipherSuite.M_MD5) {
                components.add("MD5");
                components.add("HmacMD5");
            } else if (macAlg == CipherSuite.M_SHA) {
                components.add("SHA1");
                components.add("SHA-1");
                components.add("HmacSHA1");
            } else if (macAlg == CipherSuite.M_SHA256) {
                components.add("SHA256");
                components.add("SHA-256");
                components.add("HmacSHA256");
            } else if (macAlg == CipherSuite.M_SHA384) {
                components.add("SHA384");
                components.add("SHA-384");
                components.add("HmacSHA384");
            }

            return components;
        }
    }

    static private class TLSDisabledAlgConstraints
            extends BasicDisabledAlgConstraints {

        TLSDisabledAlgConstraints() {
            super(DisabledAlgorithmConstraints.PROPERTY_TLS_DISABLED_ALGS);
        }

        @Override
        protected Set<String> decomposes(String algorithm) {
            if (algorithm.startsWith("SSL_") || algorithm.startsWith("TLS_")) {
                CipherSuite cipherSuite = null;
                try {
                    cipherSuite = CipherSuite.valueOf(algorithm);
                } catch (IllegalArgumentException iae) {
                    // ignore: unknown or unsupported ciphersuite
                }

                if (cipherSuite != null) {
                    Set<String> components = new HashSet<String>();

                    if(cipherSuite.keyExchange != null) {
                        components.addAll(
                            decomposes(cipherSuite.keyExchange, false));
                    }

                    if (cipherSuite.cipher != null) {
                        components.addAll(decomposes(cipherSuite.cipher));
                    }

                    if (cipherSuite.macAlg != null) {
                        components.addAll(decomposes(cipherSuite.macAlg));
                    }

                    return components;
                }
            }

            return super.decomposes(algorithm);
        }
    }

    static private class X509DisabledAlgConstraints
            extends BasicDisabledAlgConstraints {

        X509DisabledAlgConstraints() {
            super(DisabledAlgorithmConstraints.PROPERTY_CERTPATH_DISABLED_ALGS);
        }

        @Override
        protected Set<String> decomposes(String algorithm) {
            if (algorithm.startsWith("SSL_") || algorithm.startsWith("TLS_")) {
                CipherSuite cipherSuite = null;
                try {
                    cipherSuite = CipherSuite.valueOf(algorithm);
                } catch (IllegalArgumentException iae) {
                    // ignore: unknown or unsupported ciphersuite
                }

                if (cipherSuite != null) {
                    Set<String> components = new HashSet<String>();

                    if(cipherSuite.keyExchange != null) {
                        components.addAll(
                            decomposes(cipherSuite.keyExchange, true));
                    }

                    // Certification path algorithm constraints do not apply
                    // to cipherSuite.cipher and cipherSuite.macAlg.

                    return components;
                }
            }

            return super.decomposes(algorithm);
        }
    }
}

