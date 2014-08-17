/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.transport.http.server;

import com.sun.net.httpserver.HttpContext;
import com.sun.xml.internal.ws.transport.http.HttpAdapter;
import com.sun.xml.internal.ws.transport.http.HttpAdapterList;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.resources.ServerMessages;

import javax.xml.ws.EndpointReference;
import java.util.concurrent.Executor;
import java.net.MalformedURLException;
import java.net.URL;

import org.w3c.dom.Element;

/**
 * Hides {@link HttpContext} so that {@link EndpointImpl}
 * may load even without {@link HttpContext}.
 *
 * TODO: But what's the point? If Light-weight HTTP server isn't present,
 * all the publish operations will fail way. Why is it better to defer
 * the failure, as opposed to cause the failure as earyl as possible? -KK
 *
 * @author Jitendra Kotamraju
 */
public final class HttpEndpoint extends com.sun.xml.internal.ws.api.server.HttpEndpoint {
    private String address;
    private HttpContext httpContext;
    private final HttpAdapter adapter;
    private final Executor executor;

    public HttpEndpoint(Executor executor, HttpAdapter adapter) {
        this.executor = executor;
        this.adapter = adapter;
    }

    public void publish(String address) {
        this.address = address;
        httpContext = ServerMgr.getInstance().createContext(address);
        publish(httpContext);
    }

    public void publish(Object serverContext) {
        if (serverContext instanceof javax.xml.ws.spi.http.HttpContext) {
            setHandler((javax.xml.ws.spi.http.HttpContext)serverContext);
            return;
        }
        if (serverContext instanceof HttpContext) {
            this.httpContext = (HttpContext)serverContext;
            setHandler(httpContext);
            return;
        }
        throw new ServerRtException(ServerMessages.NOT_KNOW_HTTP_CONTEXT_TYPE(
                serverContext.getClass(), HttpContext.class,
                javax.xml.ws.spi.http.HttpContext.class));
    }

    HttpAdapterList getAdapterOwner() {
        return adapter.owner;
    }

    /**
     * This can be called only after publish
     * @return address of the Endpoint
     */
    private String getEPRAddress() {
        if (address == null)
                return httpContext.getServer().getAddress().toString();
        try {
                URL u = new URL(address);
                if (u.getPort() == 0) {
                        return new URL(u.getProtocol(),u.getHost(),
                                        httpContext.getServer().getAddress().getPort(),u.getFile()).toString();
                }
        } catch (MalformedURLException murl) {}
        return address;
    }

    public void stop() {
        if (httpContext != null) {
            if (address == null) {
                // Application created its own HttpContext
                // httpContext.setHandler(null);
                httpContext.getServer().removeContext(httpContext);
            } else {
                // Remove HttpContext created by JAXWS runtime
                ServerMgr.getInstance().removeContext(httpContext);
            }
        }

        // Invoke WebService Life cycle method
        adapter.getEndpoint().dispose();
    }

    private void setHandler(HttpContext context) {
        context.setHandler(new WSHttpHandler(adapter, executor));
    }

    private void setHandler(javax.xml.ws.spi.http.HttpContext context) {
        context.setHandler(new PortableHttpHandler(adapter, executor));
    }

    public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, Element...referenceParameters) {
        String eprAddress = getEPRAddress();
        return clazz.cast(adapter.getEndpoint().getEndpointReference(clazz, eprAddress,eprAddress+"?wsdl", referenceParameters));
    }

}
