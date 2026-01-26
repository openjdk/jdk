/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8246330 8314323
 * @library /javax/net/ssl/templates /test/lib
 * @run main/othervm -Djdk.tls.namedGroups="secp384r1"
        DisabledCurve DISABLE_NONE PASS
 * @run main/othervm -Djdk.tls.namedGroups="secp384r1"
        DisabledCurve secp384r1 FAIL
 * @run main/othervm -Djdk.tls.namedGroups="X25519MLKEM768"
        DisabledCurve DISABLE_NONE PASS
 * @run main/othervm -Djdk.tls.namedGroups="X25519MLKEM768"
        DisabledCurve X25519MLKEM768 FAIL
 * @run main/othervm -Djdk.tls.namedGroups="SecP256r1MLKEM768"
        DisabledCurve DISABLE_NONE PASS
 * @run main/othervm -Djdk.tls.namedGroups="SecP256r1MLKEM768"
        DisabledCurve SecP256r1MLKEM768 FAIL
 * @run main/othervm -Djdk.tls.namedGroups="SecP384r1MLKEM1024"
        DisabledCurve DISABLE_NONE PASS
 * @run main/othervm -Djdk.tls.namedGroups="SecP384r1MLKEM1024"
        DisabledCurve SecP384r1MLKEM1024 FAIL
*/
import java.security.Security;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import jdk.test.lib.security.SecurityUtils;

public class DisabledCurve extends SSLSocketTemplate {

    private static volatile int index;
    private static final String[][][] protocols = {
            { { "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1" }, { "TLSv1.2" } },
            { { "TLSv1.2" }, { "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1" } },
            { { "TLSv1.2" }, { "TLSv1.2" } },
            { { "TLSv1.1" }, { "TLSv1.1" } },
            { { "TLSv1" }, { "TLSv1" } },
            { { "TLSv1.3" }, { "TLSv1.3" } } };

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(
                new SSLContextTemplate.Cert[] {
                        SSLContextTemplate.Cert.CA_ECDSA_SECP384R1 },
                new SSLContextTemplate.Cert[] {
                        SSLContextTemplate.Cert.EE_ECDSA_SECP384R1 },
                getClientContextParameters());
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(
                new SSLContextTemplate.Cert[] {
                        SSLContextTemplate.Cert.CA_ECDSA_SECP384R1 },
                new SSLContextTemplate.Cert[] {
                        SSLContextTemplate.Cert.EE_ECDSA_SECP384R1 },
                getServerContextParameters());
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        String[] ps = protocols[index][0];

        System.out.print("Setting client protocol(s): ");
        Arrays.stream(ps).forEachOrdered(System.out::print);
        System.out.println();

        socket.setEnabledProtocols(ps);
    }

    @Override
    protected void configureServerSocket(SSLServerSocket serverSocket) {
        String[] ps = protocols[index][1];

        System.out.print("Setting server protocol(s): ");
        Arrays.stream(ps).forEachOrdered(System.out::print);
        System.out.println();

        serverSocket.setEnabledProtocols(ps);
    }

    public static void main(String[] args) throws Exception {
        String expected = args[1];
        String disabledName = ("DISABLE_NONE".equals(args[0]) ? "" : args[0]);
        boolean disabled = false;

        if (disabledName.isEmpty()) {
            Security.setProperty("jdk.disabled.namedCurves", "");
            Security.setProperty("jdk.certpath.disabledAlgorithms", "");
        } else {
            disabled = true;
            Security.setProperty("jdk.certpath.disabledAlgorithms", disabledName);
            if (!disabledName.contains("MLKEM")) {
                Security.setProperty("jdk.disabled.namedCurves", disabledName);
            } else {
                Security.setProperty("jdk.disabled.namedCurves", "");
            }
        }

        // Re-enable TLSv1 and TLSv1.1 since test depends on it.
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");

        String namedGroups = System.getProperty("jdk.tls.namedGroups", "");
        boolean hybridGroup = namedGroups.contains("MLKEM");

        for (index = 0; index < protocols.length; index++) {
            if (hybridGroup) {
                String[] clientProtos = protocols[index][0];
                String[] serverProtos = protocols[index][1];

                if (!(isTLS13(clientProtos) && isTLS13(serverProtos))) {
                    continue;
                }
            }

            try {
                (new DisabledCurve()).run();
                if (expected.equals("FAIL")) {
                    throw new RuntimeException(
                            "Expected test to fail, but it passed");
                }
            } catch (SSLException | IllegalStateException ssle) {
                if (expected.equals("FAIL") && disabled) {
                    System.out.println(
                            "Expected exception was thrown: TEST PASSED");
                } else {
                    throw new RuntimeException(ssle);
                }

            }

        }
    }

    private static boolean isTLS13(String[] protocols) {
        return protocols.length == 1 && "TLSv1.3".equals(protocols[0]);
    }
}
