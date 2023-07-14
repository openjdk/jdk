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
package jdk.httpclient.test.lib.common;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.StandardConstants;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;

/**
 * A (server side) SNI host name matcher. Implementation is based on the expectations set in
 * section 3 of RFC-6066.
 * A server can be configured with an instance of this class.
 * <p>
 * The RFC states:
 * {@code
 * Currently, the only server names supported are DNS hostnames; however, this does not imply
 * any dependency of TLS on DNS,
 * ....
 * TLS MAY treat provided server names as opaque data and pass the names and types to the application.
 * }
 * <p>
 * The implementation in this class doesn't mandate the configured/recognized SNI host name as DNS
 * resolvable. However, the {@code ServerNameMatcher} can be configured to treat the SNI host name
 * as DNS resolvable by passing {@code true} to the {@code attemptDNSResolution} parameter of
 * the {@link #ServerNameMatcher(boolean, String) constructor}
 */
public class ServerNameMatcher extends SNIMatcher {

    private final Logger debug;
    private final boolean attemptDNSResolution;
    private final Set<String> recognizedSNINames;

    /**
     * Creates a ServerNameMatcher which recognizes the passed {@code recognizedSNIName}
     *
     * @param recognizedSNIName The SNI host name
     */
    public ServerNameMatcher(final String recognizedSNIName) {
        this(false, recognizedSNIName);
    }

    /**
     * Creates a ServerNameMatcher which recognizes the passed SNI host name
     * If {@code attemptDNSResolution} is {@code true}, then when
     * {@link #matches(SNIServerName) matching} a client requested SNI name against the server
     * recognized SNI name, the implementation will, as a last resort do a DNS resolution of the
     * client requested SNI name and the server recognized SNI name and compare them to
     * try and find a match. If {@code attemptDNSResolution} is false, then no DNS resolution is
     * attempted and instead the SNI names are literally compared.
     *
     * @param attemptDNSResolution If true then a DNS resolution will be attempted during
     *                             {@link #matches(SNIServerName) SNI matching}
     * @param recognizedSNIName    SNI host name
     */
    public ServerNameMatcher(final boolean attemptDNSResolution,
                             final String recognizedSNIName) {
        super(StandardConstants.SNI_HOST_NAME);
        Objects.requireNonNull(recognizedSNIName);
        this.debug = Utils.getDebugLogger(() -> "SNIMatcher");
        this.recognizedSNINames = Set.of(recognizedSNIName);
        this.attemptDNSResolution = attemptDNSResolution;
    }

    /**
     * @param clientRequestedSNI the SNI name requested by the client
     *                           {@return true if the {@code clientRequestedSNI} is recognized by
     *                           the server. false otherwise}
     */
    @Override
    public boolean matches(final SNIServerName clientRequestedSNI) {
        Objects.requireNonNull(clientRequestedSNI);
        if (!SNIHostName.class.isInstance(clientRequestedSNI)) {
            if (debug.on()) {
                debug.log("SNI match (against " + recognizedSNINames + ")" +
                        " failed - not a SNIHostName: " + clientRequestedSNI);
            }
            // we only support SNIHostName type
            return false;
        }
        final String requestedName = ((SNIHostName) clientRequestedSNI).getAsciiName();
        if (recognizedSNINames.contains(requestedName)) {
            if (debug.on()) {
                debug.log("SNI match (against " + recognizedSNINames + ") passed: "
                        + clientRequestedSNI);
            }
            return true;
        }
        if (attemptDNSResolution) {
            final boolean res = matchesAfterDNSResolution(requestedName);
            if (debug.on()) {
                debug.log("SNI match (against " + recognizedSNINames + ") "
                        + (res ? "passed" : "failed") + ": " + clientRequestedSNI);
            }
            return res;
        }
        if (debug.on()) {
            debug.log("SNI match (against " + recognizedSNINames + ") failed: " + clientRequestedSNI);
        }
        return false;
    }

    private boolean matchesAfterDNSResolution(final String clientRequestedSNI) {
        final InetAddress clientRequestedAddr;
        try {
            clientRequestedAddr = InetAddress.getByName(clientRequestedSNI);
        } catch (IOException e) {
            return false;
        }
        for (final String recognizedSNIName : recognizedSNINames) {
            final InetAddress serverRecognizedAddr;
            try {
                serverRecognizedAddr = InetAddress.getByName(recognizedSNIName);
            } catch (IOException e) {
                // try next
                continue;
            }
            if (serverRecognizedAddr.equals(clientRequestedAddr)) {
                return true;
            }
        }
        return false;
    }
}
