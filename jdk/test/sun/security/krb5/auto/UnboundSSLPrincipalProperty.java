/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.login.LoginException;

/*
 * @test
 * @bug 8025123
 * @summary Checks if an unbound server uses a service principal
 *          from sun.security.krb5.principal system property if specified
 * @run main/othervm UnboundSSLPrincipalProperty
 *                              unbound.ssl.jaas.conf server_star
 * @run main/othervm UnboundSSLPrincipalProperty
 *                              unbound.ssl.jaas.conf server_multiple_principals
 */
public class UnboundSSLPrincipalProperty {

    public static void main(String[] args) throws IOException,
            NoSuchAlgorithmException,LoginException, PrivilegedActionException,
            InterruptedException {
        UnboundSSLPrincipalProperty test = new UnboundSSLPrincipalProperty();
        test.start(args[0], args[1]);
    }

    public void start(String jaacConfigFile, String serverJaasConfig)
            throws IOException, NoSuchAlgorithmException,LoginException,
            PrivilegedActionException, InterruptedException {

        // define principals
        String service1host = "service1." + UnboundSSLUtils.HOST;
        String service3host = "service3." + UnboundSSLUtils.HOST;
        String service1Principal = "host/" + service1host + "@"
                + UnboundSSLUtils.REALM;
        String service3Principal = "host/" + service3host
                + "@" + UnboundSSLUtils.REALM;

        Map<String, String> principals = new HashMap<>();
        principals.put(UnboundSSLUtils.USER_PRINCIPAL,
                UnboundSSLUtils.USER_PASSWORD);
        principals.put(UnboundSSLUtils.KRBTGT_PRINCIPAL, null);
        principals.put(service1Principal, null);
        principals.put(service3Principal, null);

        System.setProperty("java.security.krb5.conf",
                UnboundSSLUtils.KRB5_CONF_FILENAME);

        // start a local KDC instance
        KDC.startKDC(UnboundSSLUtils.HOST, UnboundSSLUtils.KRB5_CONF_FILENAME,
                UnboundSSLUtils.REALM, principals,
                UnboundSSLUtils.KTAB_FILENAME, KDC.KtabMode.APPEND);

        System.setProperty("java.security.auth.login.config",
                UnboundSSLUtils.TEST_SRC + UnboundSSLUtils.FS + jaacConfigFile);
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        // start an SSL server instance
        try (final SSLEchoServer server = SSLEchoServer.init(
                UnboundSSLUtils.TLS_KRB5_FILTER, UnboundSSLUtils.SNI_PATTERN)) {

            // specify a service principal for the server
            System.setProperty("sun.security.krb5.principal",
                    service3Principal);

            UnboundSSLUtils.startServerWithJaas(server, serverJaasConfig);

            // wait for the server is ready
            while (!server.isReady()) {
                Thread.sleep(UnboundSSLUtils.DELAY);
            }

            int port = server.getPort();

            // connetion failure is expected
            // since service3 principal was specified to use by the server
            System.out.println("Connect: SNI hostname = " + service1host
                    + ", connection failure is expected");
            try {
                SSLClient.init(UnboundSSLUtils.HOST, port,
                        UnboundSSLUtils.TLS_KRB5_FILTER, service1host)
                            .connect();
                throw new RuntimeException("Test failed: "
                        + "expected IOException not thrown");
            } catch (IOException e) {
                System.out.println("Expected exception: " + e);
            }

            System.out.println("Connect: SNI hostname = " + service3host
                    + ", successful connection is expected");
            SSLClient.init(UnboundSSLUtils.HOST, port,
                    UnboundSSLUtils.TLS_KRB5_FILTER, service3host).connect();
        }

        System.out.println("Test passed");
    }
}
