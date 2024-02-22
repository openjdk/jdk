/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetSocketAddress;

import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8326381
 * @summary verifies that the setNeedClientAuth() and setWantClientAuth()
 *          methods on HttpsParameters class work as expected
 * @run junit HttpsParametersClientAuthTest
 */
public class HttpsParametersClientAuthTest {

    /**
     * verifies default values of {@link HttpsParameters#setNeedClientAuth(boolean)}
     * and {@link HttpsParameters#setWantClientAuth(boolean)} methods
     */
    @Test
    public void testDefaultClientAuth() throws Exception {
        // test default values
        HttpsParameters defaultParams = new Params();
        assertFalse(defaultParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
        assertFalse(defaultParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
    }

    /**
     * sets {@link HttpsParameters#setNeedClientAuth(boolean)} to true and verifies
     * that subsequent calls to {@link HttpsParameters#getNeedClientAuth()} returns
     * true and {@link HttpsParameters#getWantClientAuth()} returns false
     */
    @Test
    public void testNeedClientAuth() throws Exception {
        // needClientAuth = true and thus wantClientAuth = false
        HttpsParameters needClientAuthParams = new Params();
        needClientAuthParams.setNeedClientAuth(true);
        assertTrue(needClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be true but wasn't");
        assertFalse(needClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
    }

    /**
     * sets {@link HttpsParameters#setWantClientAuth(boolean)} to true and verifies
     * that subsequent calls to {@link HttpsParameters#getWantClientAuth()} returns
     * true and {@link HttpsParameters#getNeedClientAuth()} returns false
     */
    @Test
    public void testWantClientAuth() throws Exception {
        // wantClientAuth = true and thus needClientAuth = false
        HttpsParameters wantClientAuthParams = new Params();
        wantClientAuthParams.setWantClientAuth(true);
        assertTrue(wantClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be true but wasn't");
        assertFalse(wantClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
    }

    private static final class Params extends HttpsParameters {

        @Override
        public HttpsConfigurator getHttpsConfigurator() {
            // no-op
            return null;
        }

        @Override
        public InetSocketAddress getClientAddress() {
            // no-op
            return null;
        }

        @Override
        public void setSSLParameters(SSLParameters params) {
            // no-op
        }
    }
}
