/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This is a manual test to determine the proxies set on the system for various
 * protocols. See bug 6912868.
 */
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class SystemProxies {

    static final String uriAuthority = "myMachine/";
    static final ProxySelector proxySel = ProxySelector.getDefault();

    public static void main(String[] args) {
        if (! "true".equals(System.getProperty("java.net.useSystemProxies"))) {
            System.out.println("Usage: java -Djava.net.useSystemProxies SystemProxies");
            return;
        }

        printProxies("http://");
        printProxies("https://");
        printProxies("ftp://");
    }

    static void printProxies(String proto) {
        String uriStr =  proto + uriAuthority;
        try {
            List<Proxy> proxies = proxySel.select(new URI(uriStr));
            System.out.println("Proxies returned for " + uriStr);
            for (Proxy proxy : proxies)
                System.out.println("\t" + proxy);
        } catch (URISyntaxException e) {
            System.err.println(e);
        }
    }
}
