/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/*
 * The TLS communication use case.
 */
public class UseCase {

    private static final boolean FULL_CASES
            = Utils.getBoolProperty("fullCases");

    public static final boolean FULL_CIPHER_SUITES
            = Utils.getBoolProperty("fullCipherSuites");

    public static final Protocol[] PROTOCOLS = new Protocol[] {
            Protocol.TLSV1,
            Protocol.TLSV1_1,
            Protocol.TLSV1_2,
            Protocol.TLSV1_3 };

    public static final CipherSuite[] CIPHER_SUITES = new CipherSuite[] {
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_DHE_DSS_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_DHE_DSS_WITH_AES_128_GCM_SHA256 };

    public static final CipherSuite[] MANDATORY_CIPHER_SUITES = new CipherSuite[] {
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384 };

    enum ServerName {

        NONE(null),
        EXAMPLE("EXAMPLE");

        final String name;

        private ServerName(String name) {
            this.name = name;
        }
    }

    enum AppProtocol {

        NONE(null, null),
        EXAMPLE(new String[] { Utils.HTTP_2, Utils.HTTP_1_1 }, Utils.HTTP_2);

        final String[] appProtocols;

        // Expected negotiated application protocol
        final String negoAppProtocol;

        private AppProtocol(String[] appProtocols, String negoAppProtocol) {
            this.appProtocols = appProtocols;
            this.negoAppProtocol = negoAppProtocol;
        }
    }

    private static final Object[][] PARAMS = new Object[][] {
            FULL_CASES ? PROTOCOLS : PROTOCOLS,
            FULL_CASES ? CIPHER_SUITES : MANDATORY_CIPHER_SUITES,
            FULL_CASES ? new Boolean[] { false, true } : new Boolean[] { true },
            FULL_CASES
                    ? new ServerName[] { ServerName.NONE, ServerName.EXAMPLE }
                    : new ServerName[] { ServerName.EXAMPLE },
            FULL_CASES
                    ? new AppProtocol[] {
                            AppProtocol.NONE,
                            AppProtocol.EXAMPLE }
                    : new AppProtocol[] {
                            AppProtocol.EXAMPLE } };

    public final Protocol protocol;
    public final CipherSuite cipherSuite;
    public final Boolean clientAuth;
    public final ServerName serverName;
    public final AppProtocol appProtocol;

    public final boolean negativeCase;

    public UseCase(
            Protocol protocol,
            CipherSuite cipherSuite,
            boolean clientAuth,
            ServerName serverName,
            AppProtocol appProtocol) {
        this.protocol = protocol;
        this.cipherSuite = cipherSuite;
        this.clientAuth = clientAuth;
        this.serverName = serverName;
        this.appProtocol = appProtocol;

        negativeCase = !cipherSuite.supportedByProtocol(protocol);
    }

    @Override
    public String toString() {
        return Utils.join(Utils.PARAM_DELIMITER,
                "Protocol=" + protocol.name,
                "CipherSuite=" + cipherSuite,
                "ClientAuth=" + clientAuth,
                "ServerName=" + serverName,
                "AppProtocols=" + appProtocol);
    }

    public static List<UseCase> getAllUseCases() {
        List<UseCase> useCases = new ArrayList<>();
        getUseCases(PARAMS, 0, new Object[PARAMS.length], useCases);
        return useCases;
    }

    private static void getUseCases(Object[][] params, int index,
            Object[] currentValues, List<UseCase> useCases) {
        if (index == params.length) {
            Protocol protocol = (Protocol) currentValues[0];
            CipherSuite cipherSuite = (CipherSuite) currentValues[1];

            UseCase useCase = new UseCase(
                    protocol,
                    cipherSuite,
                    (Boolean) currentValues[2],
                    (ServerName) currentValues[3],
                    (AppProtocol) currentValues[4]);
            useCases.add(useCase);
        } else {
            Object[] values = params[index];
            for (int i = 0; i < values.length; i++) {
                currentValues[index] = values[i];
                getUseCases(params, index + 1, currentValues, useCases);
            }
        }
    }
}
