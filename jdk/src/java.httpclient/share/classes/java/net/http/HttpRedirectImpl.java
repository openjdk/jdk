/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.net.http;

import java.net.*;

interface HttpRedirectImpl {

    static HttpRedirectImpl getRedirects(java.net.http.HttpClient.Redirect redir) {
        switch (redir) {
            case NEVER:
                return HttpRedirectImpl.NEVER;
            case ALWAYS:
                return HttpRedirectImpl.ALWAYS;
            case SECURE:
                return HttpRedirectImpl.SECURE;
            case SAME_PROTOCOL:
                return HttpRedirectImpl.SAME_PROTOCOL;
        }
        return HttpRedirectImpl.NEVER;
    }

    static HttpClient.Redirect getRedirects(HttpRedirectImpl redir) {
        if (redir == HttpRedirectImpl.NEVER) {
            return HttpClient.Redirect.NEVER;
        } else if (redir == HttpRedirectImpl.ALWAYS) {
            return HttpClient.Redirect.ALWAYS;
        } else if (redir == HttpRedirectImpl.SECURE) {
            return HttpClient.Redirect.SECURE;
        } else {
            return HttpClient.Redirect.SAME_PROTOCOL;
        }
    }

    /**
     * Called to determine whether the given intermediate response
     * with a redirection response code should be redirected. The target URI
     * can be obtained from the "Location" header in the given response object.
     *
     * @param rsp the response from the redirected resource
     * @return {@code true} if the redirect should be attempted automatically
     * or {@code false} if not.
     */
    boolean redirect(HttpResponse rsp);

    /**
     * Never redirect.
     */
    static HttpRedirectImpl NEVER = (HttpResponse rsp) -> false;

    /**
     * Always redirect.
     */
    static HttpRedirectImpl ALWAYS = (HttpResponse rsp) -> true;

    /**
     * Redirect to same protocol only. Redirection may occur from HTTP URLs to
     * other THHP URLs and from HTTPS URLs to other HTTPS URLs.
     */
    static HttpRedirectImpl SAME_PROTOCOL = (HttpResponse rsp) -> {
        String orig = rsp.request().uri().getScheme().toLowerCase();
        String redirect = URI.create(
                rsp.headers().firstValue("Location").orElse(""))
                .getScheme().toLowerCase();
        return orig.equals(redirect);
    };

    /**
     * Redirect always except from HTTPS URLs to HTTP URLs.
     */
    static HttpRedirectImpl SECURE = (HttpResponse rsp) -> {
        String orig = rsp.request().uri().getScheme().toLowerCase();
        String redirect = URI.create(
                rsp.headers().firstValue("Location").orElse(""))
                .getScheme().toLowerCase();
        if (orig.equals("https")) {
            return redirect.equals("https");
        }
        return true;
    };
}
