/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8365086
 * @summary verify that the implementation of java.net.CookieStore works
 *          as expected
 * @run junit CookieStoreTest
 */
class CookieStoreTest {

    // neither the scheme, host nor the port matters in this test
    private static final URI COOKIE_TEST_URI = URI.create("https://127.0.0.1:12345");

    static List<Arguments> cookieStores() {
        final List<Arguments> params = new ArrayList<>();
        // empty CookieStore
        params.add(Arguments.of(new CookieManager().getCookieStore(), true));

        final CookieStore cookieStore = new CookieManager().getCookieStore();
        cookieStore.add(COOKIE_TEST_URI, new HttpCookie("foo", "bar"));
        // non-empty CookieStore
        params.add(Arguments.of(cookieStore, false));

        return params;
    }

    /*
     * Verify that the List returned by CookieStore.getURIs() is immutable.
     */
    @ParameterizedTest
    @MethodSource("cookieStores")
    void testImmutableGetURIs(final CookieStore cookieStore, final boolean expectEmpty) {
        final List<URI> uris = cookieStore.getURIs();
        assertNotNull(uris, "CookieStore.getURIs() returned null");
        assertEquals(expectEmpty, uris.isEmpty(), "CookieStore.getURIs() returned: " + uris);
        assertImmutableList(uris, COOKIE_TEST_URI);
    }

    /*
     * Verify that the List returned by CookieStore.getCookies() is immutable.
     */
    @ParameterizedTest
    @MethodSource("cookieStores")
    void testImmutableGetCookies(final CookieStore cookieStore, final boolean expectEmpty) {
        final List<HttpCookie> cookies = cookieStore.getCookies();
        assertNotNull(cookies, "CookieStore.getCookies() returned null");
        assertEquals(expectEmpty, cookies.isEmpty(), "CookieStore.getCookies() returned: " + cookies);
        assertImmutableList(cookies, new HttpCookie("hello", "world"));
    }

    /*
     * Verify that the List returned by CookieStore.get(URI) is immutable.
     */
    @ParameterizedTest
    @MethodSource("cookieStores")
    void testImmutableGetCookiesForURI(final CookieStore cookieStore, final boolean expectEmpty) {
        final List<HttpCookie> cookies = cookieStore.get(COOKIE_TEST_URI);
        assertNotNull(cookies, "CookieStore.get(URI) returned null");
        assertEquals(expectEmpty, cookies.isEmpty(), "CookieStore.get(URI) returned: " + cookies);
        assertImmutableList(cookies, new HttpCookie("hello", "world"));
    }

    /*
     * Verifies that the attempt to modify the contents of the list will fail
     * due to the list being immutable.
     */
    private static <T> void assertImmutableList(final List<T> list, T elementToAddOrRemove) {
        // the list is expected to be immutable, so each of these operations must fail
        assertThrows(UnsupportedOperationException.class, () -> list.add(elementToAddOrRemove));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(elementToAddOrRemove));
        assertThrows(UnsupportedOperationException.class, list::clear);
        // even try the replace operation when the list isn't empty
        if (!list.isEmpty()) {
            assertThrows(UnsupportedOperationException.class, () -> list.set(0, elementToAddOrRemove));
        }
    }
}
