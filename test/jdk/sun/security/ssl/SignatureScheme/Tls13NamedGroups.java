/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8225766
 * @summary Curve in certificate should not affect signature scheme
 *          when using TLSv1.3
 * @library /javax/net/ssl/templates
 * @run main/othervm Tls13NamedGroups
 */

import javax.net.ssl.*;

public class Tls13NamedGroups extends SSLSocketTemplate {

    public static void main(String[] args) throws Exception {
        // Limit the supported named group to secp521r1.
        System.setProperty("jdk.tls.namedGroups", "secp521r1");

        new Tls13NamedGroups().run();
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(new Cert[]{Cert.CA_ECDSA_SECP256R1},
                new Cert[]{Cert.EE_ECDSA_SECP256R1},
                new ContextParameters("TLSv1.3", "PKIX", "NewSunX509"));
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setNeedClientAuth(true);
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(new Cert[]{Cert.CA_ECDSA_SECP256R1},
                new Cert[]{Cert.EE_ECDSA_SECP256R1},
                new ContextParameters("TLSv1.3", "PKIX", "NewSunX509"));
    }
}
