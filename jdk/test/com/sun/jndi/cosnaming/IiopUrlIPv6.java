/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 5042453
 * @summary Ipv6 address throws Non-numeric port number error
 */

import com.sun.jndi.cosnaming.*;
import com.sun.jndi.cosnaming.IiopUrl.Address;
import java.util.*;
import java.net.MalformedURLException;

public class IiopUrlIPv6 {

    public static void main(String[] args) {

        String[] urls = {"iiop://[::1]:2809",
                        "iiop://[::1]",
                        "iiop://:2890",
                        "iiop://129.158.2.2:80"
                      };

        for (int u = 0; u < urls.length; u++) {
            try {
                IiopUrl url = new IiopUrl(urls[u]);
                Vector addrs = url.getAddresses();

                for (int i = 0; i < addrs.size(); i++) {
                    Address addr = (Address)addrs.elementAt(i);
                    System.out.println("================");
                    System.out.println("url: " + urls[u]);
                    System.out.println("host: " + addr.host);
                    System.out.println("port: " + addr.port);
                    System.out.println("version: " + addr.major
                                + " " + addr.minor);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }
}
