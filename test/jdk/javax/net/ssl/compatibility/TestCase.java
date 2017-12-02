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

/*
 * A test case for a specific TLS communication use case between two JDKs.
 */
public class TestCase {

    public final JdkInfo serverJdk;
    public final JdkInfo clientJdk;
    public final UseCase useCase;

    public final boolean negativeCaseOnServer;
    public final boolean negativeCaseOnClient;

    private Status status;

    public TestCase(JdkInfo serverJdk, JdkInfo clientJdk, UseCase useCase) {
        this.serverJdk = serverJdk;
        this.clientJdk = clientJdk;
        this.useCase = useCase;

        negativeCaseOnServer = useCase.negativeCase
                || !serverJdk.supportsCipherSuite(useCase.cipherSuite);
        negativeCaseOnClient = useCase.negativeCase
                || !clientJdk.supportsCipherSuite(useCase.cipherSuite);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return Utils.join(Utils.PARAM_DELIMITER,
                "ServerJDK=" + serverJdk.version,
                "ClientJDK=" + clientJdk.version,
                useCase.toString());
    }
}
