/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8161016
 * @summary When proxy is set HttpURLConnection should not use DIRECT connection.
 * @run main/othervm HttpURLConWithProxy
 */
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class HttpURLConWithProxy {

    public static void main(String... arg) {
        // Remove the default nonProxyHosts to use localhost for testing
        System.setProperty("http.nonProxyHosts", "");

        System.setProperty("http.proxyHost", "1.1.1.1");
        System.setProperty("http.proxyPort", "1111");

        ServerSocket ss;
        URL url;
        URLConnection con;

        // Test1: using Proxy set by System Property:
        try {
            ss = new ServerSocket(0);
            url = new URL("http://localhost:" + ss.getLocalPort());
            con = url.openConnection();
            con.setConnectTimeout(10 * 1000);
            con.connect();
            throw new RuntimeException("Shouldn't use DIRECT connection "
                    + "when proxy is invalid/down");
        } catch (IOException ie) {
            System.out.println("Test1 Passed with: " + ie.getMessage());
        }

        // Test2: using custom ProxySelector implementation
        MyProxySelector myProxySel = new MyProxySelector();
        ProxySelector.setDefault(myProxySel);
        try {
            ss = new ServerSocket(0);
            url = new URL("http://localhost:" + ss.getLocalPort());
            con = url.openConnection();
            con.setConnectTimeout(10 * 1000);
            con.connect();
            throw new RuntimeException("Shouldn't use DIRECT connection "
                    + "when proxy is invalid/down");
        } catch (IOException ie) {
            System.out.println("Test2 Passed with: " + ie.getMessage());
        }
    }
}


class MyProxySelector extends ProxySelector {

    List<Proxy> proxies = new ArrayList<>();

    MyProxySelector() {
        Proxy p1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("2.2.2.2", 2222));
        Proxy p2 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("3.3.3.3", 3333));
        proxies.add(p1);
        proxies.add(p2);
    }

    @Override
    public List<Proxy> select(URI uri) {
        return proxies;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // System.out.println("MyProxySelector.connectFailed(): "+sa);
    }
}
