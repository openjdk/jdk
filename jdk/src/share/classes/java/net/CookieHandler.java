/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package java.net;

import java.util.Map;
import java.util.List;
import java.io.IOException;
import sun.security.util.SecurityConstants;

/**
 * A CookieHandler object provides a callback mechanism to hook up a
 * HTTP state management policy implementation into the HTTP protocol
 * handler. The HTTP state management mechanism specifies a way to
 * create a stateful session with HTTP requests and responses.
 *
 * <p>A system-wide CookieHandler that to used by the HTTP protocol
 * handler can be registered by doing a
 * CookieHandler.setDefault(CookieHandler). The currently registered
 * CookieHandler can be retrieved by calling
 * CookieHandler.getDefault().
 *
 * For more information on HTTP state management, see <a
 * href="http://www.ietf.org/rfc/rfc2965.txt""><i>RFC&nbsp;2965: HTTP
 * State Management Mechanism</i></a>
 *
 * @author Yingxian Wang
 * @since 1.5
 */
public abstract class CookieHandler {
    /**
     * The system-wide cookie handler that will apply cookies to the
     * request headers and manage cookies from the response headers.
     *
     * @see setDefault(CookieHandler)
     * @see getDefault()
     */
    private static CookieHandler cookieHandler;

    /**
     * Gets the system-wide cookie handler.
     *
     * @return the system-wide cookie handler; A null return means
     *        there is no system-wide cookie handler currently set.
     * @throws SecurityException
     *       If a security manager has been installed and it denies
     * {@link NetPermission}<tt>("getCookieHandler")</tt>
     * @see #setDefault(CookieHandler)
     */
    public synchronized static CookieHandler getDefault() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.GET_COOKIEHANDLER_PERMISSION);
        }
        return cookieHandler;
    }

    /**
     * Sets (or unsets) the system-wide cookie handler.
     *
     * Note: non-standard http protocol handlers may ignore this setting.
     *
     * @param cHandler The HTTP cookie handler, or
     *       <code>null</code> to unset.
     * @throws SecurityException
     *       If a security manager has been installed and it denies
     * {@link NetPermission}<tt>("setCookieHandler")</tt>
     * @see #getDefault()
     */
    public synchronized static void setDefault(CookieHandler cHandler) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.SET_COOKIEHANDLER_PERMISSION);
        }
        cookieHandler = cHandler;
    }

    /**
     * Gets all the applicable cookies from a cookie cache for the
     * specified uri in the request header.
     *
     * HTTP protocol implementers should make sure that this method is
     * called after all request headers related to choosing cookies
     * are added, and before the request is sent.
     *
     * @param uri a <code>URI</code> to send cookies to in a request
     * @param requestHeaders - a Map from request header
     *            field names to lists of field values representing
     *            the current request headers
     * @return an immutable map from state management headers, with
     *            field names "Cookie" or "Cookie2" to a list of
     *            cookies containing state information
     *
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if either argument is null
     * @see #put(URI, Map)
     */
    public abstract Map<String, List<String>>
        get(URI uri, Map<String, List<String>> requestHeaders)
        throws IOException;

    /**
     * Sets all the applicable cookies, examples are response header
     * fields that are named Set-Cookie2, present in the response
     * headers into a cookie cache.
     *
     * @param uri a <code>URI</code> where the cookies come from
     * @param responseHeaders an immutable map from field names to
     *            lists of field values representing the response
     *            header fields returned
     * @throws  IOException if an I/O error occurs
     * @throws  IllegalArgumentException if either argument is null
     * @see #get(URI, Map)
     */
    public abstract void
        put(URI uri, Map<String, List<String>> responseHeaders)
        throws IOException;
}
