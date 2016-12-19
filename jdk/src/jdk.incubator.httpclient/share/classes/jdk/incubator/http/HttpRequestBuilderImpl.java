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

package jdk.incubator.http;

import java.net.URI;
import jdk.incubator.http.HttpRequest.BodyProcessor;
import java.time.Duration;
import static java.util.Objects.requireNonNull;
import jdk.incubator.http.internal.common.HttpHeadersImpl;
import static jdk.incubator.http.internal.common.Utils.isValidName;
import static jdk.incubator.http.internal.common.Utils.isValidValue;

class HttpRequestBuilderImpl extends HttpRequest.Builder {

    private HttpHeadersImpl userHeaders;
    private URI uri;
    private String method;
    //private HttpClient.Redirect followRedirects;
    private boolean expectContinue;
    private HttpRequest.BodyProcessor body;
    private HttpClient.Version version;
    //private final HttpClientImpl client;
    //private ProxySelector proxy;
    private Duration duration;

    public HttpRequestBuilderImpl(URI uri) {
        //this.client = client;
        checkURI(uri);
        this.uri = uri;
        this.userHeaders = new HttpHeadersImpl();
        this.method = "GET"; // default, as per spec
    }

    public HttpRequestBuilderImpl() {
        this.userHeaders = new HttpHeadersImpl();
    }

    @Override
    public HttpRequestBuilderImpl uri(URI uri) {
        requireNonNull(uri);
        checkURI(uri);
        this.uri = uri;
        return this;
    }

    private static void checkURI(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("invalid URI scheme");
        }
    }
/*
    @Override
    public HttpRequestBuilderImpl followRedirects(HttpClient.Redirect follow) {
        requireNonNull(follow);
        this.followRedirects = follow;
        return this;
    }
*/
    @Override
    public HttpRequestBuilderImpl header(String name, String value) {
        checkNameAndValue(name, value);
        userHeaders.addHeader(name, value);
        return this;
    }

    @Override
    public HttpRequestBuilderImpl headers(String... params) {
        requireNonNull(params);
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("wrong number of parameters");
        }
        for (int i = 0; i < params.length; i += 2) {
            String name  = params[i];
            String value = params[i + 1];
            header(name, value);
        }
        return this;
    }

    /*
    @Override
    public HttpRequestBuilderImpl proxy(ProxySelector proxy) {
        requireNonNull(proxy);
        this.proxy = proxy;
        return this;
    }
*/
    @Override
    public HttpRequestBuilderImpl copy() {
        HttpRequestBuilderImpl b = new HttpRequestBuilderImpl(this.uri);
        b.userHeaders = this.userHeaders.deepCopy();
        b.method = this.method;
        //b.followRedirects = this.followRedirects;
        b.expectContinue = this.expectContinue;
        b.body = body;
        b.uri = uri;
        //b.proxy = proxy;
        return b;
    }

    @Override
    public HttpRequestBuilderImpl setHeader(String name, String value) {
        checkNameAndValue(name, value);
        userHeaders.setHeader(name, value);
        return this;
    }

    private void checkNameAndValue(String name, String value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid header name");
        }
        if (!isValidValue(value)) {
            throw new IllegalArgumentException("invalid header value");
        }
    }

    @Override
    public HttpRequestBuilderImpl expectContinue(boolean enable) {
        expectContinue = enable;
        return this;
    }

    @Override
    public HttpRequestBuilderImpl version(HttpClient.Version version) {
        requireNonNull(version);
        this.version = version;
        return this;
    }

    HttpHeadersImpl headers() {  return userHeaders; }

    //HttpClientImpl client() {return client;}

    URI uri() { return uri; }

    String method() { return method; }

    //HttpClient.Redirect followRedirects() { return followRedirects; }

    //ProxySelector proxy() { return proxy; }

    boolean expectContinue() { return expectContinue; }

    public HttpRequest.BodyProcessor body() { return body; }

    HttpClient.Version version() { return version; }

    @Override
    public HttpRequest.Builder GET() { return method("GET", null); }

    @Override
    public HttpRequest.Builder POST(BodyProcessor body) {
        return method("POST", body);
    }

    @Override
    public HttpRequest.Builder DELETE(BodyProcessor body) {
        return method("DELETE", body);
    }

    @Override
    public HttpRequest.Builder PUT(BodyProcessor body) {
        return method("PUT", body);
    }

    @Override
    public HttpRequest.Builder method(String method, BodyProcessor body) {
        this.method = requireNonNull(method);
        this.body = body;
        return this;
    }

    @Override
    public HttpRequest build() {
        return new HttpRequestImpl(this);
    }

    @Override
    public HttpRequest.Builder timeout(Duration duration) {
        requireNonNull(duration);
        if (duration.isNegative() || Duration.ZERO.equals(duration))
            throw new IllegalArgumentException("Invalid duration: " + duration);
        this.duration = duration;
        return this;
    }

    Duration duration() { return duration; }

}
