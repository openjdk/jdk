/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6216082
 * @summary  Redirect problem with HttpsURLConnection using a proxy
 * @modules java.base/sun.net.www
 * @library ..
 * @build HttpCallback TestHttpsServer ClosedChannelList
 *        HttpTransaction TunnelProxy
 * @key intermittent
 * @run main/othervm B6216082
 */

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.util.*;

public class B6216082 {
    static SimpleHttpTransaction httpTrans;
    static TestHttpsServer server;
    static TunnelProxy proxy;

    // it seems there's no proxy ever if a url points to 'localhost',
    // even if proxy related properties are set. so we need to bind
    // our simple http proxy and http server to a non-loopback address
    static InetAddress firstNonLoAddress = null;

    public static void main(String[] args) throws Exception {
        HostnameVerifier reservedHV =
            HttpsURLConnection.getDefaultHostnameVerifier();
        try {
            // XXX workaround for CNFE
            Class.forName("java.nio.channels.ClosedByInterruptException");
            if (!setupEnv()) {
                return;
            }

            startHttpServer();

            // https.proxyPort can only be set after the TunnelProxy has been
            // created as it will use an ephemeral port.
            System.setProperty("https.proxyPort",
                        (new Integer(proxy.getLocalPort())).toString() );

            makeHttpCall();

            if (httpTrans.hasBadRequest) {
                throw new RuntimeException("Test failed : bad http request");
            }
        } finally {
            if (proxy != null) {
                proxy.terminate();
            }
            if (server != null) {
               server.terminate();
            }
            HttpsURLConnection.setDefaultHostnameVerifier(reservedHV);
        }
    }

    /*
     * Where do we find the keystores for ssl?
     */
    static String pathToStores = "../../../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";
    public static boolean setupEnv() throws Exception {
        firstNonLoAddress = getNonLoAddress();
        if (firstNonLoAddress == null) {
            System.err.println("The test needs at least one non-loopback address to run. Quit now.");
            return false;
        }
        System.out.println(firstNonLoAddress.getHostAddress());
        // will use proxy
        System.setProperty( "https.proxyHost", firstNonLoAddress.getHostAddress());

        // setup properties to do ssl
        String keyFilename = System.getProperty("test.src", "./") + "/" +
                             pathToStores + "/" + keyStoreFile;
        String trustFilename = System.getProperty("test.src", "./") + "/" +
                               pathToStores + "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);
        HttpsURLConnection.setDefaultHostnameVerifier(new NameVerifier());
        return true;
    }

    public static InetAddress getNonLoAddress() throws Exception {
        NetworkInterface loNIC = NetworkInterface.getByInetAddress(InetAddress.getByName("localhost"));
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (!nic.getName().equalsIgnoreCase(loNIC.getName())) {
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress())
                        return addr;
                }
            }
        }

        return null;
    }

    public static void startHttpServer() throws IOException {
        // Both the https server and the proxy let the
        // system pick up an ephemeral port.
        httpTrans = new SimpleHttpTransaction();
        server = new TestHttpsServer(httpTrans, 1, 10, 0);
        proxy = new TunnelProxy(1, 10, 0);
    }

    public static void makeHttpCall() throws Exception {
        System.out.println("https server listen on: " + server.getLocalPort());
        System.out.println("https proxy listen on: " + proxy.getLocalPort());
        URL url = new URL("https" , firstNonLoAddress.getHostAddress(),
                            server.getLocalPort(), "/");
        HttpURLConnection uc = (HttpURLConnection)url.openConnection();
        System.out.println(uc.getResponseCode());
        uc.disconnect();
    }

    static class NameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}

class SimpleHttpTransaction implements HttpCallback {
    public boolean hasBadRequest = false;

    /*
     * Our http server which simply redirect first call
     */
    public void request(HttpTransaction trans) {
        try {
            String path = trans.getRequestURI().getPath();
            if (path.equals("/")) {
                // the first call, redirect it
                String location = "/redirect";
                trans.addResponseHeader("Location", location);
                trans.sendResponse(302, "Moved Temporarily");
            } else {
                // if the bug exsits, it'll send 2 GET commands
                // check 2nd GET here
                String duplicatedGet = trans.getRequestHeader(null);
                if (duplicatedGet != null &&
                    duplicatedGet.toUpperCase().indexOf("GET") >= 0) {
                    trans.sendResponse(400, "Bad Request");
                    hasBadRequest = true;
                } else {
                    trans.sendResponse(200, "OK");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
