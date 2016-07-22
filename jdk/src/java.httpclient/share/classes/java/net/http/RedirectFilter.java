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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

class RedirectFilter implements HeaderFilter {

    HttpRequestImpl requestImpl;
    HttpRequest request;
    HttpClientImpl client;
    HttpClient.Redirect policy;
    String method;
    final static int DEFAULT_MAX_REDIRECTS = 5;
    URI uri;

    final static int max_redirects = Utils.getIntegerNetProperty(
            "java.net.httpclient.redirects.retrylimit", DEFAULT_MAX_REDIRECTS
    );

    @Override
    public void request(HttpRequestImpl r) throws IOException {
        this.request = r;
        this.policy = request.followRedirects();
        this.client = r.getClient();
        this.method = r.method();
        this.requestImpl = r;
        this.uri = r.uri();
    }

    @Override
    public HttpRequestImpl response(HttpResponseImpl r) throws IOException {
        return handleResponse(r);
    }

    /**
     * checks to see if new request needed and returns it.
     * Null means response is ok to return to user.
     */
    private HttpRequestImpl handleResponse(HttpResponseImpl r) {
        int rcode = r.statusCode();
        if (rcode == 200 || policy == HttpClient.Redirect.NEVER) {
            return null;
        }
        if (rcode >= 300 && rcode <= 399) {
            URI redir = getRedirectedURI(r.headers());
            if (canRedirect(r) && ++r.request.exchange.numberOfRedirects < max_redirects) {
                //System.out.println("Redirecting to: " + redir);
                return new HttpRequestImpl(redir, request, client, method, requestImpl);
            } else {
                //System.out.println("Redirect: giving up");
                return null;
            }
        }
        return null;
    }

    private URI getRedirectedURI(HttpHeaders headers) {
        URI redirectedURI;
        String ss = headers.firstValue("Location").orElse("Not present");
        redirectedURI = headers.firstValue("Location")
                .map((s) -> URI.create(s))
                .orElseThrow(() -> new UncheckedIOException(
                        new IOException("Invalid redirection")));

        // redirect could be relative to original URL, but if not
        // then redirect is used.
        redirectedURI = uri.resolve(redirectedURI);
        return redirectedURI;
    }

    private boolean canRedirect(HttpResponse r) {
        return requestImpl.followRedirectsImpl().redirect(r);
    }
}
