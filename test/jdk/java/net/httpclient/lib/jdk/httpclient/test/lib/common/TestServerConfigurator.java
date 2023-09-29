/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.common;

import java.net.InetAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

/**
 * A {@link HttpsConfigurator} that can be used with the HTTP1 test server over HTTPS.
 * This configurator {@link #configure(HttpsParameters) configures} the server's
 * {@link HttpsParameters} with the necessary {@link SSLParameters} including a
 * {@link SNIMatcher}
 */
public final class TestServerConfigurator extends HttpsConfigurator {

    private final InetAddress serverAddr;

    /**
     * Creates a Https configuration, with the given {@link SSLContext}.
     *
     * @param serverAddr the address to which the server is bound
     * @param context    the {@code SSLContext} to use for this configurator
     * @throws NullPointerException if no {@code SSLContext} supplied
     */
    public TestServerConfigurator(final InetAddress serverAddr, final SSLContext context) {
        super(context);
        this.serverAddr = serverAddr;
    }

    @Override
    public void configure(final HttpsParameters params) {
        final SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
        @SuppressWarnings("removal") final SecurityManager sm = System.getSecurityManager();
        final String hostname;
        if (sm == null) {
            hostname = serverAddr.getHostName();
        } else {
            final PrivilegedAction<String> action = () -> serverAddr.getHostName();
            hostname = AccessController.doPrivileged(action);
        }
        final List<SNIMatcher> sniMatchers = List.of(new ServerNameMatcher(hostname));
        sslParams.setSNIMatchers(sniMatchers);
        // configure the server with these custom SSLParameters
        params.setSSLParameters(sslParams);
    }
}
