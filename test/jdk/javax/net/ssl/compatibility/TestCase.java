/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * A test case for a specific TLS communication use case between two JDKs.
 */
public class TestCase {

    public final JdkInfo serverJdk;
    public final JdkInfo clientJdk;
    public final UseCase useCase;

    public final boolean protocolSupportsCipherSuite;

    public final boolean serverEnablesProtocol;
    public final boolean serverEnablesCipherSuite;

    public final boolean clientSupportsProtocol;
    public final boolean clientSupportsCipherSuite;

    public final boolean negativeCaseOnServer;
    public final boolean negativeCaseOnClient;

    private Status status;

    public TestCase(JdkInfo serverJdk, JdkInfo clientJdk, UseCase useCase) {
        this.serverJdk = serverJdk;
        this.clientJdk = clientJdk;
        this.useCase = useCase;

        serverEnablesProtocol = serverJdk.enablesProtocol(useCase.protocol);
        serverEnablesCipherSuite = serverJdk.enablesCipherSuite(useCase.cipherSuite);

        clientSupportsProtocol = clientJdk.supportsProtocol(useCase.protocol);
        clientSupportsCipherSuite = clientJdk.supportsCipherSuite(useCase.cipherSuite);

        protocolSupportsCipherSuite = useCase.protocolSupportsCipherSuite;

        negativeCaseOnServer = !protocolSupportsCipherSuite
                || !serverEnablesProtocol
                || !serverEnablesCipherSuite;
        negativeCaseOnClient = !protocolSupportsCipherSuite
                || !clientSupportsProtocol
                || !clientSupportsCipherSuite;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isNegative() {
        return negativeCaseOnServer || negativeCaseOnClient;
    }

    public boolean isFailed() {
        return status == Status.FAIL || status == Status.UNEXPECTED_SUCCESS;
    }

    public String negativeCaseReason() {
        return Utils.join(". ",
                !protocolSupportsCipherSuite
                        ? "Protocol doesn't support cipher suite"
                        : "",
                !serverEnablesProtocol
                        ? "Server doesn't enable protocol"
                        : "",
                !serverEnablesCipherSuite
                        ? "Server doesn't enable cipher suite"
                        : "",
                !clientSupportsProtocol
                        ? "Client doesn't support protocol"
                        : "",
                !clientSupportsCipherSuite
                        ? "Client doesn't support cipher suite"
                        : "");
    }

    public String reason() {
        if (status == Status.SUCCESS) {
            return "";
        }

        if (status == Status.EXPECTED_FAIL && isNegative()) {
            return negativeCaseReason();
        }

        return "Refer to log at case hyperlink for details...";
    }

    @Override
    public String toString() {
        return Utils.join(Utils.PARAM_DELIMITER,
                "ServerJDK=" + serverJdk.version,
                "ClientJDK=" + clientJdk.version,
                useCase.toString());
    }
}
