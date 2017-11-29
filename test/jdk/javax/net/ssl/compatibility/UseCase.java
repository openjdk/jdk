/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

    private static final Parameter[][] PARAMS = new Parameter[][] {
            FULL_CASES ? Protocol.values() : Protocol.getMandatoryValues(),
            FULL_CASES ? CipherSuite.values() : CipherSuite.getMandatoryValues(),
            FULL_CASES ? ClientAuth.values() : ClientAuth.getMandatoryValues(),
            FULL_CASES ? ServerName.values() : ServerName.getMandatoryValues(),
            FULL_CASES ? AppProtocol.values() : AppProtocol.getMandatoryValues() };

    public final Protocol protocol;
    public final CipherSuite cipherSuite;
    public final ClientAuth clientAuth;
    public final ServerName serverName;
    public final AppProtocol appProtocol;

    public final boolean negativeCase;

    public UseCase(
            Protocol protocol,
            CipherSuite cipherSuite,
            ClientAuth clientAuth,
            ServerName serverName,
            AppProtocol appProtocol) {
        this.protocol = protocol;
        this.cipherSuite = cipherSuite;
        this.clientAuth = clientAuth;
        this.serverName = serverName;
        this.appProtocol = appProtocol;

        negativeCase = !cipherSuite.supportedByProtocol(protocol);
    }

    // JDK 6 doesn't support EC key algorithm.
    public boolean ignoredByJdk(JdkInfo jdkInfo) {
        return cipherSuite.name().contains("_EC") && !jdkInfo.supportsECKey;
    }

    @Override
    public String toString() {
        return Utils.join(Utils.PARAM_DELIMITER,
                "Protocol=" + protocol.version,
                "CipherSuite=" + cipherSuite,
                "ClientAuth=" + clientAuth,
                "ServerName=" + serverName,
                "AppProtocols=" + appProtocol);
    }

    public static List<UseCase> getAllUseCases() {
        List<UseCase> useCases = new ArrayList<>();
        getUseCases(PARAMS, 0, new Parameter[PARAMS.length], useCases);
        return useCases;
    }

    private static void getUseCases(Parameter[][] params, int index,
            Parameter[] currentValues, List<UseCase> useCases) {
        if (index == params.length) {
            Protocol protocol = (Protocol) currentValues[0];
            CipherSuite cipherSuite = (CipherSuite) currentValues[1];

            UseCase useCase = new UseCase(
                    protocol,
                    cipherSuite,
                    (ClientAuth) currentValues[2],
                    (ServerName) currentValues[3],
                    (AppProtocol) currentValues[4]);
            useCases.add(useCase);
        } else {
            Parameter[] values = params[index];
            for (int i = 0; i < values.length; i++) {
                currentValues[index] = values[i];
                getUseCases(params, index + 1, currentValues, useCases);
            }
        }
    }
}
