/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8023862
 * @summary Verify that the default value of the java.rmi.server.disableHttp
 *          has been changed from false to true.
 * @compile -XDignore.symbol.file DisableHttpDefaultValue.java
 *
 * @run main/othervm                                     DisableHttpDefaultValue true
 * @run main/othervm -Djava.rmi.server.disableHttp       DisableHttpDefaultValue false
 * @run main/othervm -Djava.rmi.server.disableHttp=false DisableHttpDefaultValue false
 * @run main/othervm -Djava.rmi.server.disableHttp=xyzzy DisableHttpDefaultValue false
 * @run main/othervm -Djava.rmi.server.disableHttp=true  DisableHttpDefaultValue true
 */

import sun.rmi.transport.proxy.RMIMasterSocketFactory;

public class DisableHttpDefaultValue {
    /**
     * Subclass RMIMasterSocketFactory to get access to
     * protected field altFactoryList. This list has a
     * zero size if proxying is disabled.
     */
    static class SocketFactory extends RMIMasterSocketFactory {
        boolean proxyDisabled() {
            return altFactoryList.size() == 0;
        }
    }

    /**
     * Takes a single arg, which is the expected boolean value of
     * java.rmi.server.disableHttp.
     */
    public static void main(String[] args) throws Exception {
        // Force there to be a proxy host, so that we are able to
        // tell whether proxying is enabled or disabled.
        System.setProperty("http.proxyHost", "proxy.example.com");

        String propval = System.getProperty("java.rmi.server.disableHttp");
        String propdisp = (propval == null) ? "null" : ("\"" + propval + "\"");
        boolean expected = Boolean.parseBoolean(args[0]);
        boolean actual = new SocketFactory().proxyDisabled();
        System.out.printf("### prop=%s exp=%s act=%s%n", propdisp, expected, actual);
        if (expected != actual)
            throw new AssertionError();
    }
}
