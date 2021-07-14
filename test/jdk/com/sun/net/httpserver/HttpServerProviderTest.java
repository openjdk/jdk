/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8270286
 * @summary Test for HttpServerProvider::loadProviderFromProperty
 * @modules jdk.httpserver/sun.net.httpserver:+open
 *          jdk.httpserver/com.sun.net.httpserver.spi:+open
 * @run testng/othervm HttpServerProviderTest
 */

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.ServiceConfigurationError;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

public class HttpServerProviderTest {
    public final static String PROPERTY_KEY = "com.sun.net.httpserver.HttpServerProvider";

    @Test
    public void testPublic() throws Exception {
        resetProperty();

        var n = ProviderP.class.getName();
        System.setProperty(PROPERTY_KEY, n);
        assertEquals(System.getProperty(PROPERTY_KEY), n);

        var p = HttpServerProvider.provider();
        assertNull(p.createHttpServer(null, 0));
        assertNull(p.createHttpsServer(null, 0));
    }

    @DataProvider
    public Object[][] classNames() {
        return new Object[][] { {ProviderPNPC.class.getName()}, {ProviderNP.class.getName()} };
    }

    @Test(dataProvider = "classNames")
    public void testNonPublic(String n) throws Exception {
        resetProperty();

        System.setProperty(PROPERTY_KEY, n);
        assertEquals(System.getProperty(PROPERTY_KEY), n);

        var e = expectThrows(ServiceConfigurationError.class, HttpServerProvider::provider);
        assertEquals(e.getClass(), ServiceConfigurationError.class);
        assertEquals(e.getCause().getClass(), IllegalAccessException.class);
    }

    @Test
    public void testThrowingConstructor() throws Exception {
        resetProperty();

        var cn = ProviderT.class.getName();
        System.setProperty(PROPERTY_KEY, cn);
        assertEquals(System.getProperty(PROPERTY_KEY), cn);

        var e = expectThrows(ServiceConfigurationError.class, HttpServerProvider::provider);
        assertEquals(e.getClass(), ServiceConfigurationError.class);
        assertEquals(e.getCause().getClass(), InvocationTargetException.class);
        assertEquals(e.getCause().getCause().getMessage(), "throwing constructor");
    }

    // --- infra ---

    private static void resetProperty() throws Exception {
        var field = HttpServerProvider.class.getDeclaredField("provider");
        field.setAccessible(true);
        field.set(null, null);
    }

    /**
     * Test provider that is public (P)
     */
    public static class ProviderP extends HttpServerProvider {
        // public default constructor
        @Override
        public HttpServer createHttpServer(InetSocketAddress addr, int backlog) { return null; }
        @Override
        public HttpsServer createHttpsServer(InetSocketAddress addr, int backlog) { return null; }
    }

    /**
     * Test provider that is public with a non-public constructor (PNPC)
     */
    public static class ProviderPNPC extends HttpServerProvider {
        ProviderPNPC() { super(); }
        @Override
        public HttpServer createHttpServer(InetSocketAddress addr, int backlog) { return null; }
        @Override
        public HttpsServer createHttpsServer(InetSocketAddress addr, int backlog) { return null; }
    }

    /**
     * Test provider that is not public (NP)
     */
    static class ProviderNP extends HttpServerProvider {
        // package-private default constructor
        @Override
        public HttpServer createHttpServer(InetSocketAddress addr, int backlog) { return null; }
        @Override
        public HttpsServer createHttpsServer(InetSocketAddress addr, int backlog) { return null; }
    }

    /**
     * Test provider with a constructor that throws
     */
    public static class ProviderT extends HttpServerProvider {
        public ProviderT() { throw new AssertionError("throwing constructor"); }
        @Override
        public HttpServer createHttpServer(InetSocketAddress addr, int backlog) { return null; }
        @Override
        public HttpsServer createHttpsServer(InetSocketAddress addr, int backlog) { return null; }
    }
}
