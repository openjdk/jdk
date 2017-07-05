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

import java.net.URI;
import java.net.ProxySelector;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class HttpRequestBuilderImpl extends HttpRequest.Builder {

    private HttpHeadersImpl userHeaders;
    private URI uri;
    private String method;
    private HttpClient.Redirect followRedirects;
    private boolean expectContinue;
    private HttpRequest.BodyProcessor body;
    private HttpClient.Version version;
    private final HttpClientImpl client;
    private ProxySelector proxy;
    private long timeval;

    public HttpRequestBuilderImpl(HttpClientImpl client, URI uri) {
        this.client = client;
        checkURI(uri);
        this.uri = uri;
        this.version = client.version();
        this.userHeaders = new HttpHeadersImpl();
    }

    @Override
    public HttpRequestBuilderImpl body(HttpRequest.BodyProcessor reqproc) {
        Objects.requireNonNull(reqproc);
        this.body = reqproc;
        return this;
    }

    @Override
    public HttpRequestBuilderImpl uri(URI uri) {
        Objects.requireNonNull(uri);
        checkURI(uri);
        this.uri = uri;
        return this;
    }

    private static void checkURI(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http"))
            throw new IllegalArgumentException("invalid URI scheme");
    }

    @Override
    public HttpRequestBuilderImpl followRedirects(HttpClient.Redirect follow) {
        Objects.requireNonNull(follow);
        this.followRedirects = follow;
        return this;
    }

    @Override
    public HttpRequestBuilderImpl header(String name, String value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        Utils.validateToken(name, "invalid header name");
        userHeaders.addHeader(name, value);
        return this;
    }

    @Override
    public HttpRequestBuilderImpl headers(String... params) {
        Objects.requireNonNull(params);
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("wrong number of parameters");
        }
        for (int i=0; i<params.length; ) {
            String name = params[i];
            String value = params[i+1];
            header(name, value);
            i+=2;
        }
        return this;
    }

    @Override
    public HttpRequestBuilderImpl proxy(ProxySelector proxy) {
        Objects.requireNonNull(proxy);
        this.proxy = proxy;
        return this;
    }

    @Override
    public HttpRequestBuilderImpl copy() {
        HttpRequestBuilderImpl b = new HttpRequestBuilderImpl(this.client, this.uri);
        b.userHeaders = this.userHeaders.deepCopy();
        b.method = this.method;
        b.followRedirects = this.followRedirects;
        b.expectContinue = this.expectContinue;
        b.body = body;
        b.uri = uri;
        b.proxy = proxy;
        return b;
    }

    @Override
    public HttpRequestBuilderImpl setHeader(String name, String value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        userHeaders.setHeader(name, value);
        return this;
    }

    @Override
    public HttpRequestBuilderImpl expectContinue(boolean enable) {
        expectContinue = enable;
        return this;
    }

    @Override
    public HttpRequestBuilderImpl version(HttpClient.Version version) {
        Objects.requireNonNull(version);
        this.version = version;
        return this;
    }

    HttpHeadersImpl headers() {  return userHeaders; }

    URI uri() { return uri; }

    String method() { return method; }

    HttpClient.Redirect followRedirects() { return followRedirects; }

    ProxySelector proxy() { return proxy; }

    boolean expectContinue() { return expectContinue; }

    HttpRequest.BodyProcessor body() { return body; }

    HttpClient.Version version() { return version; }

    @Override
    public HttpRequest GET() { return method("GET"); }

    @Override
    public HttpRequest POST() { return method("POST"); }

    @Override
    public HttpRequest PUT() { return method("PUT"); }

    @Override
    public HttpRequest method(String method) {
        Objects.requireNonNull(method);
        this.method = method;
        return new HttpRequestImpl(client, method, this);
    }

    @Override
    public HttpRequest.Builder timeout(TimeUnit timeunit, long timeval) {
        Objects.requireNonNull(timeunit);
        this.timeval = timeunit.toMillis(timeval);
        return this;
    }

    long timeval() { return timeval; }
}
