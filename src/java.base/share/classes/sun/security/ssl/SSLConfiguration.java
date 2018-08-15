/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AlgorithmConstraints;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import sun.security.ssl.SSLExtension.ClientExtensions;
import sun.security.ssl.SSLExtension.ServerExtensions;

/**
 * SSL/(D)TLS configuration.
 */
final class SSLConfiguration implements Cloneable {
    // configurations with SSLParameters
    AlgorithmConstraints        algorithmConstraints;
    List<ProtocolVersion>       enabledProtocols;
    List<CipherSuite>           enabledCipherSuites;
    ClientAuthType              clientAuthType;
    String                      identificationProtocol;
    List<SNIServerName>         serverNames;
    Collection<SNIMatcher>      sniMatchers;
    String[]                    applicationProtocols;
    boolean                     preferLocalCipherSuites;
    boolean                     enableRetransmissions;
    int                         maximumPacketSize;

    // the maximum protocol version of enabled protocols
    ProtocolVersion             maximumProtocolVersion;

    // Configurations per SSLSocket or SSLEngine instance.
    boolean                     isClientMode;
    boolean                     enableSessionCreation;

    // the application layer protocol negotiation configuration
    BiFunction<SSLSocket, List<String>, String> socketAPSelector;
    BiFunction<SSLEngine, List<String>, String> engineAPSelector;

    HashMap<HandshakeCompletedListener, AccessControlContext>
                                handshakeListeners;

    boolean                     noSniExtension;
    boolean                     noSniMatcher;

    // To switch off the extended_master_secret extension.
    static final boolean useExtendedMasterSecret;

    // Allow session resumption without Extended Master Secret extension.
    static final boolean allowLegacyResumption =
        Utilities.getBooleanProperty("jdk.tls.allowLegacyResumption", true);

    // Allow full handshake without Extended Master Secret extension.
    static final boolean allowLegacyMasterSecret =
        Utilities.getBooleanProperty("jdk.tls.allowLegacyMasterSecret", true);

    // Allow full handshake without Extended Master Secret extension.
    static final boolean useCompatibilityMode = Utilities.getBooleanProperty(
            "jdk.tls.client.useCompatibilityMode", true);

    // Respond a close_notify alert if receiving close_notify alert.
    static final boolean acknowledgeCloseNotify  = Utilities.getBooleanProperty(
            "jdk.tls.acknowledgeCloseNotify", false);

    // Is the extended_master_secret extension supported?
    static {
        boolean supportExtendedMasterSecret = Utilities.getBooleanProperty(
                    "jdk.tls.useExtendedMasterSecret", true);
        if (supportExtendedMasterSecret) {
            try {
                JsseJce.getKeyGenerator("SunTlsExtendedMasterSecret");
            } catch (NoSuchAlgorithmException nae) {
                supportExtendedMasterSecret = false;
            }
        }
        useExtendedMasterSecret = supportExtendedMasterSecret;
    }

    SSLConfiguration(SSLContextImpl sslContext, boolean isClientMode) {

        // Configurations with SSLParameters, default values.
        this.algorithmConstraints = SSLAlgorithmConstraints.DEFAULT;
        this.enabledProtocols =
                sslContext.getDefaultProtocolVersions(!isClientMode);
        this.enabledCipherSuites =
                sslContext.getDefaultCipherSuites(!isClientMode);
        this.clientAuthType = ClientAuthType.CLIENT_AUTH_NONE;

        this.identificationProtocol = null;
        this.serverNames = Collections.<SNIServerName>emptyList();
        this.sniMatchers = Collections.<SNIMatcher>emptyList();
        this.preferLocalCipherSuites = false;

        this.applicationProtocols = new String[0];
        this.enableRetransmissions = sslContext.isDTLS();
        this.maximumPacketSize = 0;         // please reset it explicitly later

        this.maximumProtocolVersion = ProtocolVersion.NONE;
        for (ProtocolVersion pv : enabledProtocols) {
            if (pv.compareTo(maximumProtocolVersion) > 0) {
                this.maximumProtocolVersion = pv;
            }
        }

        // Configurations per SSLSocket or SSLEngine instance.
        this.isClientMode = isClientMode;
        this.enableSessionCreation = true;
        this.socketAPSelector = null;
        this.engineAPSelector = null;

        this.handshakeListeners = null;
        this.noSniExtension = false;
        this.noSniMatcher = false;
    }

    SSLParameters getSSLParameters() {
        SSLParameters params = new SSLParameters();

        params.setAlgorithmConstraints(this.algorithmConstraints);
        params.setProtocols(ProtocolVersion.toStringArray(enabledProtocols));
        params.setCipherSuites(CipherSuite.namesOf(enabledCipherSuites));
        switch (this.clientAuthType) {
            case CLIENT_AUTH_REQUIRED:
                params.setNeedClientAuth(true);
                break;
            case CLIENT_AUTH_REQUESTED:
                params.setWantClientAuth(true);
                break;
            default:
                params.setWantClientAuth(false);
        }
        params.setEndpointIdentificationAlgorithm(this.identificationProtocol);

        if (serverNames.isEmpty() && !noSniExtension) {
            // 'null' indicates none has been set
            params.setServerNames(null);
        } else {
            params.setServerNames(this.serverNames);
        }

        if (sniMatchers.isEmpty() && !noSniMatcher) {
            // 'null' indicates none has been set
            params.setSNIMatchers(null);
        } else {
            params.setSNIMatchers(this.sniMatchers);
        }

        params.setApplicationProtocols(this.applicationProtocols);
        params.setUseCipherSuitesOrder(this.preferLocalCipherSuites);
        params.setEnableRetransmissions(this.enableRetransmissions);
        params.setMaximumPacketSize(this.maximumPacketSize);

        return params;
    }

    void setSSLParameters(SSLParameters params) {
        AlgorithmConstraints ac = params.getAlgorithmConstraints();
        if (ac != null) {
            this.algorithmConstraints = ac;
        }   // otherwise, use the default value

        String[] sa = params.getCipherSuites();
        if (sa != null) {
            this.enabledCipherSuites = CipherSuite.validValuesOf(sa);
        }   // otherwise, use the default values

        sa = params.getProtocols();
        if (sa != null) {
            this.enabledProtocols = ProtocolVersion.namesOf(sa);

            this.maximumProtocolVersion = ProtocolVersion.NONE;
            for (ProtocolVersion pv : enabledProtocols) {
                if (pv.compareTo(maximumProtocolVersion) > 0) {
                    this.maximumProtocolVersion = pv;
                }
            }
        }   // otherwise, use the default values

        if (params.getNeedClientAuth()) {
            this.clientAuthType = ClientAuthType.CLIENT_AUTH_REQUIRED;
        } else if (params.getWantClientAuth()) {
            this.clientAuthType = ClientAuthType.CLIENT_AUTH_REQUESTED;
        } else {
            this.clientAuthType = ClientAuthType.CLIENT_AUTH_NONE;
        }

        String s = params.getEndpointIdentificationAlgorithm();
        if (s != null) {
            this.identificationProtocol = s;
        }   // otherwise, use the default value

        List<SNIServerName> sniNames = params.getServerNames();
        if (sniNames != null) {
            this.noSniExtension = sniNames.isEmpty();
            this.serverNames = sniNames;
        }   // null if none has been set

        Collection<SNIMatcher> matchers = params.getSNIMatchers();
        if (matchers != null) {
            this.noSniMatcher = matchers.isEmpty();
            this.sniMatchers = matchers;
        }   // null if none has been set

        sa = params.getApplicationProtocols();
        if (sa != null) {
            this.applicationProtocols = sa;
        }   // otherwise, use the default values

        this.preferLocalCipherSuites = params.getUseCipherSuitesOrder();
        this.enableRetransmissions = params.getEnableRetransmissions();
        this.maximumPacketSize = params.getMaximumPacketSize();
    }

    // SSLSocket only
    void addHandshakeCompletedListener(
            HandshakeCompletedListener listener) {

        if (handshakeListeners == null) {
            handshakeListeners = new HashMap<>(4);
        }

        handshakeListeners.put(listener, AccessController.getContext());
    }

    // SSLSocket only
    void removeHandshakeCompletedListener(
            HandshakeCompletedListener listener) {

        if (handshakeListeners == null) {
            throw new IllegalArgumentException("no listeners");
        }

        if (handshakeListeners.remove(listener) == null) {
            throw new IllegalArgumentException("listener not registered");
        }

        if (handshakeListeners.isEmpty()) {
            handshakeListeners = null;
        }
    }

    /**
     * Return true if the extension is available.
     */
    boolean isAvailable(SSLExtension extension) {
        for (ProtocolVersion protocolVersion : enabledProtocols) {
            if (extension.isAvailable(protocolVersion)) {
                if (isClientMode ?
                        ClientExtensions.defaults.contains(extension) :
                        ServerExtensions.defaults.contains(extension)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Return true if the extension is available for the specific protocol.
     */
    boolean isAvailable(SSLExtension extension,
            ProtocolVersion protocolVersion) {
        return extension.isAvailable(protocolVersion) &&
                (isClientMode ? ClientExtensions.defaults.contains(extension) :
                                ServerExtensions.defaults.contains(extension));
    }

    /**
     * Get the enabled extensions for the specific handshake message.
     *
     * Used to consume handshake extensions.
     */
    SSLExtension[] getEnabledExtensions(SSLHandshake handshakeType) {
        List<SSLExtension> extensions = new ArrayList<>();
        for (SSLExtension extension : SSLExtension.values()) {
            if (extension.handshakeType == handshakeType) {
                if (isAvailable(extension)) {
                    extensions.add(extension);
                }
            }
        }

        return extensions.toArray(new SSLExtension[0]);
    }

    /**
     * Get the enabled extensions for the specific handshake message, excluding
     * the specified extensions.
     *
     * Used to consume handshake extensions.
     */
    SSLExtension[] getExclusiveExtensions(SSLHandshake handshakeType,
            List<SSLExtension> excluded) {
        List<SSLExtension> extensions = new ArrayList<>();
        for (SSLExtension extension : SSLExtension.values()) {
            if (extension.handshakeType == handshakeType) {
                if (isAvailable(extension) && !excluded.contains(extension)) {
                    extensions.add(extension);
                }
            }
        }

        return extensions.toArray(new SSLExtension[0]);
    }

    /**
     * Get the enabled extensions for the specific handshake message
     * and the specific protocol version.
     *
     * Used to produce handshake extensions after handshake protocol
     * version negotiation.
     */
    SSLExtension[] getEnabledExtensions(
            SSLHandshake handshakeType, ProtocolVersion protocolVersion) {
        return getEnabledExtensions(
            handshakeType, Arrays.asList(protocolVersion));
    }

    /**
     * Get the enabled extensions for the specific handshake message
     * and the specific protocol versions.
     *
     * Used to produce ClientHello extensions before handshake protocol
     * version negotiation.
     */
    SSLExtension[] getEnabledExtensions(
            SSLHandshake handshakeType, List<ProtocolVersion> activeProtocols) {
        List<SSLExtension> extensions = new ArrayList<>();
        for (SSLExtension extension : SSLExtension.values()) {
            if (extension.handshakeType == handshakeType) {
                if (!isAvailable(extension)) {
                    continue;
                }

                for (ProtocolVersion protocolVersion : activeProtocols) {
                    if (extension.isAvailable(protocolVersion)) {
                        extensions.add(extension);
                        break;
                    }
                }
            }
        }

        return extensions.toArray(new SSLExtension[0]);
    }

    @Override
    @SuppressWarnings({"unchecked", "CloneDeclaresCloneNotSupported"})
    public Object clone() {
        // Note that only references to the configurations are copied.
        try {
            SSLConfiguration config = (SSLConfiguration)super.clone();
            if (handshakeListeners != null) {
                config.handshakeListeners =
                    (HashMap<HandshakeCompletedListener, AccessControlContext>)
                            handshakeListeners.clone();
            }

            return config;
        } catch (CloneNotSupportedException cnse) {
            // unlikely
        }

        return null;    // unlikely
    }
}
