/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.net.ssl.*;

import jdk.internal.net.quic.QuicTLSEngine;
import sun.security.util.DisabledAlgorithmConstraints;
import static sun.security.util.DisabledAlgorithmConstraints.*;

/**
 * Algorithm constraints for disabled algorithms property
 * <p>
 * See the "jdk.certpath.disabledAlgorithms" specification in java.security
 * for the syntax of the disabled algorithm string.
 */
final class SSLAlgorithmConstraints implements AlgorithmConstraints {

    public enum SIGNATURE_CONSTRAINTS_MODE {
        PEER,  // Check against peer supported signatures
        LOCAL  // Check against local supported signatures
    }

    private static final DisabledAlgorithmConstraints tlsDisabledAlgConstraints =
            new DisabledAlgorithmConstraints(PROPERTY_TLS_DISABLED_ALGS,
                    new SSLAlgorithmDecomposer());

    private static final DisabledAlgorithmConstraints x509DisabledAlgConstraints =
            new DisabledAlgorithmConstraints(PROPERTY_CERTPATH_DISABLED_ALGS,
                    new SSLAlgorithmDecomposer(true));

    private final AlgorithmConstraints userSpecifiedConstraints;
    private final AlgorithmConstraints peerSpecifiedConstraints;

    private final boolean enabledX509DisabledAlgConstraints;

    // the default algorithm constraints
    static final SSLAlgorithmConstraints DEFAULT =
            new SSLAlgorithmConstraints(null, true);

    // the default SSL only algorithm constraints
    static final SSLAlgorithmConstraints DEFAULT_SSL_ONLY =
            new SSLAlgorithmConstraints(null, false);

    private SSLAlgorithmConstraints(
            AlgorithmConstraints userSpecifiedConstraints,
            boolean enabledX509DisabledAlgConstraints) {
        this(userSpecifiedConstraints, null, enabledX509DisabledAlgConstraints);
    }

    private SSLAlgorithmConstraints(
            AlgorithmConstraints userSpecifiedConstraints,
            SupportedSignatureAlgorithmConstraints peerSpecifiedConstraints,
            boolean withDefaultCertPathConstraints) {
        this.userSpecifiedConstraints = userSpecifiedConstraints;
        this.peerSpecifiedConstraints = peerSpecifiedConstraints;
        this.enabledX509DisabledAlgConstraints = withDefaultCertPathConstraints;
    }

    /**
     * Returns a SSLAlgorithmConstraints instance that checks the provided
     * {@code userSpecifiedConstraints} in addition to standard checks.
     * Returns a singleton instance if parameter is null or DEFAULT.
     *
     * @param userSpecifiedConstraints additional constraints to check
     * @return a SSLAlgorithmConstraints instance
     */
    static SSLAlgorithmConstraints wrap(
            AlgorithmConstraints userSpecifiedConstraints) {
        return wrap(userSpecifiedConstraints, true);
    }

    private static SSLAlgorithmConstraints wrap(
            AlgorithmConstraints userSpecifiedConstraints,
            boolean withDefaultCertPathConstraints) {
        if (nullIfDefault(userSpecifiedConstraints) == null) {
            return withDefaultCertPathConstraints ? DEFAULT : DEFAULT_SSL_ONLY;
        }
        return new SSLAlgorithmConstraints(userSpecifiedConstraints,
                withDefaultCertPathConstraints);
    }

    /**
     * Returns a SSLAlgorithmConstraints instance that checks the constraints
     * configured for the given {@code socket} in addition to standard checks.
     * Returns a singleton instance if the constraints are null or DEFAULT.
     *
     * @param socket socket with configured constraints
     * @param mode SIGNATURE_CONSTRAINTS_MODE
     * @return a SSLAlgorithmConstraints instance
     */
    static SSLAlgorithmConstraints forSocket(
            SSLSocket socket,
            SIGNATURE_CONSTRAINTS_MODE mode,
            boolean withDefaultCertPathConstraints) {

        if (socket == null) {
            return wrap(null, withDefaultCertPathConstraints);
        }

        return new SSLAlgorithmConstraints(
                nullIfDefault(getUserSpecifiedConstraints(socket)),
                new SupportedSignatureAlgorithmConstraints(
                        socket.getHandshakeSession(), mode),
                withDefaultCertPathConstraints);
    }

    /**
     * Returns a SSLAlgorithmConstraints instance that checks the constraints
     * configured for the given {@code engine} in addition to standard checks.
     * Returns a singleton instance if the constraints are null or DEFAULT.
     *
     * @param engine engine with configured constraints
     * @param mode SIGNATURE_CONSTRAINTS_MODE
     * @return a SSLAlgorithmConstraints instance
     */
    static SSLAlgorithmConstraints forEngine(
            SSLEngine engine,
            SIGNATURE_CONSTRAINTS_MODE mode,
            boolean withDefaultCertPathConstraints) {

        if (engine == null) {
            return wrap(null, withDefaultCertPathConstraints);
        }

        return new SSLAlgorithmConstraints(
                nullIfDefault(getUserSpecifiedConstraints(engine)),
                new SupportedSignatureAlgorithmConstraints(
                        engine.getHandshakeSession(), mode),
                withDefaultCertPathConstraints);
    }

    /**
     * Returns an {@link AlgorithmConstraints} instance that uses the
     * constraints configured for the given {@code engine} in addition
     * to the platform configured constraints.
     * <p>
     * If the given {@code allowedAlgorithms} is non-null then the returned
     * {@code AlgorithmConstraints} will only permit those allowed algorithms.
     *
     * @param engine QuicTLSEngine used to determine the constraints
     * @param mode SIGNATURE_CONSTRAINTS_MODE
     * @param withDefaultCertPathConstraints whether or not to apply the default certpath
     *                                       algorithm constraints too
     * @return a AlgorithmConstraints instance
     */
    static AlgorithmConstraints forQUIC(QuicTLSEngine engine,
                                        SIGNATURE_CONSTRAINTS_MODE mode,
                                        boolean withDefaultCertPathConstraints) {
        if (engine == null) {
            return wrap(null, withDefaultCertPathConstraints);
        }

        return new SSLAlgorithmConstraints(
                nullIfDefault(getUserSpecifiedConstraints(engine)),
                new SupportedSignatureAlgorithmConstraints(engine.getHandshakeSession(), mode),
                withDefaultCertPathConstraints);
    }

    private static AlgorithmConstraints nullIfDefault(
            AlgorithmConstraints constraints) {
        return constraints == DEFAULT ? null : constraints;
    }

    private static AlgorithmConstraints getUserSpecifiedConstraints(
            SSLEngine engine) {
        if (engine != null) {
            // Note that the KeyManager or TrustManager implementation may be
            // not implemented in the same provider as SSLSocket/SSLEngine.
            // Please check the instance before casting to use SSLEngineImpl.
            if (engine instanceof SSLEngineImpl) {
                HandshakeContext hc =
                        ((SSLEngineImpl) engine).conContext.handshakeContext;
                if (hc != null) {
                    return hc.sslConfig.userSpecifiedAlgorithmConstraints;
                }
            }

            return engine.getSSLParameters().getAlgorithmConstraints();
        }

        return null;
    }

    private static AlgorithmConstraints getUserSpecifiedConstraints(
            SSLSocket socket) {
        if (socket != null) {
            // Note that the KeyManager or TrustManager implementation may be
            // not implemented in the same provider as SSLSocket/SSLEngine.
            // Please check the instance before casting to use SSLSocketImpl.
            if (socket instanceof SSLSocketImpl) {
                HandshakeContext hc =
                        ((SSLSocketImpl) socket).conContext.handshakeContext;
                if (hc != null) {
                    return hc.sslConfig.userSpecifiedAlgorithmConstraints;
                }
            }

            return socket.getSSLParameters().getAlgorithmConstraints();
        }

        return null;
    }

    private static AlgorithmConstraints getUserSpecifiedConstraints(
            QuicTLSEngine quicEngine) {
        if (quicEngine != null) {
            if (quicEngine instanceof QuicTLSEngineImpl engineImpl) {
                return engineImpl.getAlgorithmConstraints();
            }
            return quicEngine.getSSLParameters().getAlgorithmConstraints();
        }
        return null;
    }

    @Override
    public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

        boolean permitted = true;

        if (peerSpecifiedConstraints != null) {
            permitted = peerSpecifiedConstraints.permits(
                    primitives, algorithm, parameters);
        }

        if (permitted && userSpecifiedConstraints != null) {
            permitted = userSpecifiedConstraints.permits(
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

    @Override
    public boolean permits(Set<CryptoPrimitive> primitives, Key key) {

        boolean permitted = true;

        if (peerSpecifiedConstraints != null) {
            permitted = peerSpecifiedConstraints.permits(primitives, key);
        }

        if (permitted && userSpecifiedConstraints != null) {
            permitted = userSpecifiedConstraints.permits(primitives, key);
        }

        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(primitives, key);
        }

        if (permitted && enabledX509DisabledAlgConstraints) {
            permitted = x509DisabledAlgConstraints.permits(primitives, key);
        }

        return permitted;
    }

    @Override
    public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        boolean permitted = true;

        if (peerSpecifiedConstraints != null) {
            permitted = peerSpecifiedConstraints.permits(
                    primitives, algorithm, key, parameters);
        }

        if (permitted && userSpecifiedConstraints != null) {
            permitted = userSpecifiedConstraints.permits(
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

    // Checks if algorithm is disabled for the given TLS scopes.
    boolean permits(String algorithm, Set<SSLScope> scopes) {
        return tlsDisabledAlgConstraints.permits(algorithm, scopes);
    }

    private static class SupportedSignatureAlgorithmConstraints
            implements AlgorithmConstraints {

        // Supported signature algorithms
        private Set<String> supportedAlgorithms;
        // Supported signature schemes
        private List<SignatureScheme> supportedSignatureSchemes;
        private boolean checksDisabled;

        SupportedSignatureAlgorithmConstraints(
                SSLSession session, SIGNATURE_CONSTRAINTS_MODE mode) {

            if (mode == null
                    || !(session instanceof ExtendedSSLSession extSession
                    // "signature_algorithms_cert" TLS extension is only
                    // available starting with TLSv1.2.
                    && ProtocolVersion.useTLS12PlusSpec(
                    extSession.getProtocol()))) {

                checksDisabled = true;
                return;
            }

            supportedAlgorithms = new TreeSet<>(
                    String.CASE_INSENSITIVE_ORDER);

            switch (mode) {
                case SIGNATURE_CONSTRAINTS_MODE.PEER:
                    supportedAlgorithms.addAll(Arrays.asList(extSession
                            .getPeerSupportedSignatureAlgorithms()));
                    break;
                case SIGNATURE_CONSTRAINTS_MODE.LOCAL:
                    supportedAlgorithms.addAll(Arrays.asList(extSession
                            .getLocalSupportedSignatureAlgorithms()));
            }

            // Do additional SignatureSchemes checks for in-house
            // ExtendedSSLSession implementation.
            if (extSession instanceof SSLSessionImpl sslSessionImpl) {
                switch (mode) {
                    case SIGNATURE_CONSTRAINTS_MODE.PEER:
                        supportedSignatureSchemes = new ArrayList<>(
                                sslSessionImpl
                                        .getPeerSupportedSignatureSchemes());
                        break;
                    case SIGNATURE_CONSTRAINTS_MODE.LOCAL:
                        supportedSignatureSchemes = new ArrayList<>(
                                sslSessionImpl
                                        .getLocalSupportedSignatureSchemes());
                }
            }
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives,
                String algorithm, AlgorithmParameters parameters) {

            if (checksDisabled) {
                return true;
            }

            if (algorithm == null || algorithm.isEmpty()) {
                throw new IllegalArgumentException(
                        "No algorithm name specified");
            }

            if (primitives == null || primitives.isEmpty()) {
                throw new IllegalArgumentException(
                        "No cryptographic primitive specified");
            }

            if (supportedAlgorithms == null || supportedAlgorithms.isEmpty()) {
                return false;
            }

            return supportedAlgorithms.contains(algorithm);
        }

        @Override
        public final boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        @Override
        public final boolean permits(Set<CryptoPrimitive> primitives,
                String algorithm, Key key, AlgorithmParameters parameters) {

            if (algorithm == null || algorithm.isEmpty()) {
                throw new IllegalArgumentException(
                        "No algorithm name specified");
            }

            return permits(primitives, algorithm, parameters)
                    && checkRsaSsaPssParams(algorithm, key, parameters);
        }

        // Additional check for RSASSA-PSS signature algorithm parameters.
        private boolean checkRsaSsaPssParams(
                String algorithm, Key key, AlgorithmParameters parameters) {

            if (supportedSignatureSchemes == null
                    || key == null
                    || parameters == null
                    || !"RSASSA-PSS".equalsIgnoreCase(algorithm)) {
                return true;
            }

            try {
                String keyAlg = key.getAlgorithm();
                String paramDigestAlg = parameters.getParameterSpec(
                        PSSParameterSpec.class).getDigestAlgorithm();

                return supportedSignatureSchemes.stream().anyMatch(ss ->
                        ss.algorithm.equalsIgnoreCase(algorithm)
                                && ss.keyAlgorithm.equalsIgnoreCase(keyAlg)
                                && ((PSSParameterSpec) ss.signAlgParams.parameterSpec)
                                .getDigestAlgorithm()
                                .equalsIgnoreCase(paramDigestAlg));

            } catch (InvalidParameterSpecException e) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("Invalid AlgorithmParameters: "
                            + parameters + "; Error: " + e.getMessage());
                }

                return true;
            }
        }
    }
}
