/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;

class X509Authentications implements SSLAuthentication {

    private final String[] keyTypes;

    public X509Authentications(String[] keyTypes) {
        this.keyTypes = keyTypes;
    }

    @Override
    public SSLPossession createPossession(HandshakeContext context) {
        if (context.sslConfig.isClientMode) {
            SSLPossession poss = createClientPossession(
                    (ClientHandshakeContext)context);
            if (poss != null) {
                return poss;
            }
        } else {
            SSLPossession poss = createServerPossession(
                    (ServerHandshakeContext)context);
            if (poss != null) {
                return poss;
            }
        }

        return null;
    }

    public SSLPossession createClientPossession(HandshakeContext handshakeContext) {
        ClientHandshakeContext chc = (ClientHandshakeContext)handshakeContext;
        X509ExtendedKeyManager km = chc.sslContext.getX509KeyManager();
        String clientAlias = null;
        if (chc.conContext.transport instanceof SSLSocketImpl) {
            clientAlias = km.chooseClientAlias(
                    keyTypes,
                    chc.peerSupportedAuthorities == null ? null :
                            chc.peerSupportedAuthorities.clone(),
                    (SSLSocket)chc.conContext.transport);
        } else if (chc.conContext.transport instanceof SSLEngineImpl) {
            clientAlias = km.chooseEngineClientAlias(
                    keyTypes,
                    chc.peerSupportedAuthorities == null ? null :
                            chc.peerSupportedAuthorities.clone(),
                    (SSLEngine)chc.conContext.transport);
        }

        if (clientAlias == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest("No X.509 cert selected for ");
            }
            return null;
        }

        PrivateKey clientPrivateKey = km.getPrivateKey(clientAlias);
        if (clientPrivateKey == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest(
                        clientAlias + " is not a private key entry");
            }
            return null;
        }

        X509Certificate[] clientCerts = km.getCertificateChain(clientAlias);
        if ((clientCerts == null) || (clientCerts.length == 0)) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.finest(clientAlias +
                        " is a private key entry with no cert chain stored");
            }
            return null;
        }

        PublicKey clientPublicKey = clientCerts[0].getPublicKey();
        if (!clientPrivateKey.getAlgorithm().equals(clientPublicKey.getAlgorithm())) {
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.fine(
                        clientAlias + " private or public key is not of " +
                                "keyType" + " algorithm");
            }
            return null;
        }

        return new X509Authentication.X509Possession(clientPrivateKey, clientCerts);
    }

    private SSLPossession createServerPossession(ServerHandshakeContext shc) {
        X509ExtendedKeyManager km = shc.sslContext.getX509KeyManager();
        String serverAlias = null;
        for (String keyType : keyTypes) {
            if (shc.conContext.transport instanceof SSLSocketImpl) {
                serverAlias = km.chooseServerAlias(keyType,
                        shc.peerSupportedAuthorities == null ? null :
                                shc.peerSupportedAuthorities.clone(),
                        (SSLSocket) shc.conContext.transport);
            } else if (shc.conContext.transport instanceof SSLEngineImpl) {
                serverAlias = km.chooseEngineServerAlias(keyType,
                        shc.peerSupportedAuthorities == null ? null :
                                shc.peerSupportedAuthorities.clone(),
                        (SSLEngine) shc.conContext.transport);
            }

            if (serverAlias == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.finest("No X.509 cert selected for " + keyType);
                }
                continue;
            }

            PrivateKey serverPrivateKey = km.getPrivateKey(serverAlias);
            if (serverPrivateKey == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.finest(
                            serverAlias + " is not a private key entry");
                }
                continue;
            }

            X509Certificate[] serverCerts = km.getCertificateChain(serverAlias);
            if ((serverCerts == null) || (serverCerts.length == 0)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.finest(
                            serverAlias + " is not a certificate entry");
                }
                continue;
            }

            PublicKey serverPublicKey = serverCerts[0].getPublicKey();
            if ((!serverPrivateKey.getAlgorithm().equals(keyType))
                    || (!serverPublicKey.getAlgorithm().equals(keyType))) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.fine(
                            serverAlias + " private or public key is not of " +
                                    keyType + " algorithm");
                }
                continue;
            }

            // For TLS 1.2 and prior versions, the public key of a EC cert
            // MUST use a curve and point format supported by the client.
            // But for TLS 1.3, signature algorithms are negotiated
            // independently via the "signature_algorithms" extension.
            if (!shc.negotiatedProtocol.useTLS13PlusSpec() &&
                    keyType.equals("EC")) {
                if (!(serverPublicKey instanceof ECPublicKey)) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                        SSLLogger.warning(serverAlias +
                                " public key is not an instance of ECPublicKey");
                    }
                    continue;
                }

                // For ECC certs, check whether we support the EC domain
                // parameters.  If the client sent a supported_groups
                // ClientHello extension, check against that too for
                // TLS 1.2 and prior versions.
                ECParameterSpec params =
                        ((ECPublicKey) serverPublicKey).getParams();
                NamedGroup namedGroup = NamedGroup.valueOf(params);
                if ((namedGroup == null) ||
                        (!SupportedGroupsExtension.SupportedGroups.isSupported(namedGroup)) ||
                        ((shc.clientRequestedNamedGroups != null) &&
                                !shc.clientRequestedNamedGroups.contains(namedGroup))) {

                    if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                        SSLLogger.warning(
                                "Unsupported named group (" + namedGroup +
                                        ") used in the " + serverAlias + " certificate");
                    }

                    continue;
                }
            }

            return new X509Authentication.X509Possession(serverPrivateKey, serverCerts);
        }
        return null;
    }
}
