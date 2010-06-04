/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6216082
 * @library ../../../httpstest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction TunnelProxy
 * @run main/othervm B6216082
 * @summary  Redirect problem with HttpsURLConnection using a proxy
*/

import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.util.*;

public class B6216082 {
    static SimpleHttpTransaction httpTrans;
    static HttpServer server;
    static TunnelProxy proxy;

    // it seems there's no proxy ever if a url points to 'localhost',
    // even if proxy related properties are set. so we need to bind
    // our simple http proxy and http server to a non-loopback address
    static InetAddress firstNonLoAddress = null;

    public static void main(String[] args) throws Exception {
        // XXX workaround for CNFE
        Class.forName("java.nio.channels.ClosedByInterruptException");
        setupEnv();

        startHttpServer();

        // https.proxyPort can only be set after the TunnelProxy has been
        // created as it will use an ephemeral port.
        System.setProperty( "https.proxyPort", (new Integer(proxy.getLocalPort())).toString() );

        makeHttpCall();

        if (httpTrans.hasBadRequest) {
            throw new RuntimeException("Test failed : bad http request");
        }
    }

    /*
     * Where do we find the keystores for ssl?
     */
    static String pathToStores = "../../../../../../etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";
    public static void setupEnv() {
        try {
            firstNonLoAddress = getNonLoAddress();

            if (firstNonLoAddress == null) {
                System.out.println("The test needs at least one non-loopback address to run. Quit now.");
                System.exit(0);
            }
            System.out.println(firstNonLoAddress.getHostAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public static void startHttpServer() {
        try {
            // Both the https server and the proxy let the
            // system pick up an ephemeral port.
            httpTrans = new SimpleHttpTransaction();
            server = new HttpServer(httpTrans, 1, 10, 0);
            proxy = new TunnelProxy(1, 10, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void makeHttpCall() {
        try {
            System.out.println("https server listen on: " + server.getLocalPort());
            System.out.println("https proxy listen on: " + proxy.getLocalPort());
            URL url = new URL("https" , firstNonLoAddress.getHostAddress(),
                                server.getLocalPort(), "/");
            HttpURLConnection uc = (HttpURLConnection)url.openConnection();
            System.out.println(uc.getResponseCode());
            uc.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            proxy.terminate();
            server.terminate();
        }
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
            e.printStackTrace();
        }
    }
}
