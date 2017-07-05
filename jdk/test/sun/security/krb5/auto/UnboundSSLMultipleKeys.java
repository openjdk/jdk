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
 * @summary Checks if an unbound server pick up a correct key from keytab
 * @run main/othervm UnboundSSLMultipleKeys
 *                              unbound.ssl.jaas.conf server_star
 * @run main/othervm UnboundSSLMultipleKeys
 *                              unbound.ssl.jaas.conf server_multiple_principals
 */
public class UnboundSSLMultipleKeys {

    public static void main(String[] args)
            throws IOException, NoSuchAlgorithmException, LoginException,
            PrivilegedActionException, InterruptedException {
        UnboundSSLMultipleKeys test = new UnboundSSLMultipleKeys();
        test.start(args[0], args[1]);
    }

    private void start(String jaacConfigFile, String serverJaasConfig)
            throws IOException, NoSuchAlgorithmException, LoginException,
            PrivilegedActionException, InterruptedException {

        // define service principals
        String service1host = "service1." + UnboundSSLUtils.HOST;
        String service2host = "service2." + UnboundSSLUtils.HOST;
        String service3host = "service3." + UnboundSSLUtils.HOST;
        String service1Principal = "host/" + service1host + "@"
                + UnboundSSLUtils.REALM;
        String service2Principal = "host/" + service2host + "@"
                + UnboundSSLUtils.REALM;
        String service3Principal = "host/" + service3host + "@"
                + UnboundSSLUtils.REALM;

        Map<String, String> principals = new HashMap<>();
        principals.put(UnboundSSLUtils.USER_PRINCIPAL,
                UnboundSSLUtils.USER_PASSWORD);
        principals.put(UnboundSSLUtils.KRBTGT_PRINCIPAL, "pass");
        principals.put(service1Principal, "pass0");
        principals.put(service1Principal, "pass1");
        principals.put(service1Principal, "pass2");
        principals.put(service2Principal, "pass");
        principals.put(service3Principal, "pass");

        System.setProperty("java.security.krb5.conf",
                UnboundSSLUtils.KRB5_CONF_FILENAME);

        /*
         * Start a local KDC instance
         *
         * Keytab file contains 3 keys (with different KVNO) for service1
         * principal, but password for only one key is the same with the record
         * for service1 principal in KDC.
         */
        UnboundSSLUtils.startKDC(UnboundSSLUtils.REALM, principals,
                UnboundSSLUtils.KTAB_FILENAME, UnboundSSLUtils.KtabMode.APPEND);

        System.setProperty("java.security.auth.login.config",
                UnboundSSLUtils.TEST_SRC + UnboundSSLUtils.FS + jaacConfigFile);
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        // start an SSL server instance
        try (SSLEchoServer server = SSLEchoServer.init(
                UnboundSSLUtils.TLS_KRB5_FILTER, UnboundSSLUtils.SNI_PATTERN)) {

            UnboundSSLUtils.startServerWithJaas(server, serverJaasConfig);

            //  wait for the server is ready
            while (!server.isReady()) {
                Thread.sleep(UnboundSSLUtils.DELAY);
            }

            // run a client
            System.out.println("Successful connection is expected");
            SSLClient.init(UnboundSSLUtils.HOST, server.getPort(),
                    UnboundSSLUtils.TLS_KRB5_FILTER, service1host).connect();
        }

        System.out.println("Test passed");
    }

}
