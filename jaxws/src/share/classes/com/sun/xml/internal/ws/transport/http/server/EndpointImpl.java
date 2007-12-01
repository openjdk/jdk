
/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.http.server;

import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.ws.Endpoint;
import javax.xml.ws.Binding;
import javax.xml.transform.Source;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.xml.ws.WebServicePermission;


/**
 *
 * @author WS Development Team
 */
public class EndpointImpl extends Endpoint {

    private static final WebServicePermission ENDPOINT_PUBLISH_PERMISSION =
        new WebServicePermission("publishEndpoint");
    private Object actualEndpoint;        // Don't declare as HttpEndpoint type
    private Executor executor;
    private boolean published;
    private boolean stopped;
    private final com.sun.xml.internal.ws.spi.runtime.Binding binding;
    private final Object implementor;
    private Map<String, Object> properties;
    private java.util.List<Source> metadata;

    public EndpointImpl(String bindingId, Object impl) {
        this.implementor = impl;
        this.binding = BindingImpl.getBinding(bindingId, impl.getClass(), null, false);
    }

    public Binding getBinding() {
        return binding;
    }

    public Object getImplementor() {
        return implementor;
    }

    public void publish(String address) {
        canPublish();
        URL url;
        try {
            url = new URL(address);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Cannot create URL for this address "+address);
        }
        if (!url.getProtocol().equals("http")) {
            throw new IllegalArgumentException(url.getProtocol()+" protocol based address is not supported");
        }
        if (!url.getPath().startsWith("/")) {
            throw new IllegalArgumentException("Incorrect WebService address="+address+
                    ". The address's path should start with /");
        }
        checkPlatform();
        // Don't load HttpEndpoint class before as it may load HttpServer classes
        actualEndpoint = new HttpEndpoint(implementor, binding, metadata, properties, executor);
        ((HttpEndpoint)actualEndpoint).publish(address);
        published = true;
    }

    public void publish(Object serverContext) {
        canPublish();
        checkPlatform();
        if (!com.sun.net.httpserver.HttpContext.class.isAssignableFrom(serverContext.getClass())) {
            throw new IllegalArgumentException(serverContext.getClass()+" is not a supported context.");
        }
        // Don't load HttpEndpoint class before as it may load HttpServer classes
        actualEndpoint = new HttpEndpoint(implementor, binding, metadata, properties, executor);
        ((HttpEndpoint)actualEndpoint).publish(serverContext);
        published = true;
    }

    public void stop() {
        if (published) {
            ((HttpEndpoint)actualEndpoint).stop();
            published = false;
            stopped = true;
        }
    }

    public boolean isPublished() {
        return published;
    }

    private void canPublish() {
        if (published) {
            throw new IllegalStateException(
                "Cannot publish this endpoint. Endpoint has been already published.");
        }
        if (stopped) {
            throw new IllegalStateException(
                "Cannot publish this endpoint. Endpoint has been already stopped.");
        }
    }

    public java.util.List<Source> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.List<Source> metadata) {
        if (published) {
            throw new IllegalStateException("Cannot set Metadata. Endpoint is already published");
        }
        this.metadata = metadata;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> map) {
        this.properties = map;
    }

    /**
     * Checks the permission of "publishEndpoint" before accessing HTTP classes.
     * Also it checks if there is an available HTTP server implementation.
     */
    private void checkPlatform() {

        // Checks permission for "publishEndpoint"
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ENDPOINT_PUBLISH_PERMISSION);
        }

        // See if HttpServer implementation is available
        try {
            Class.forName("com.sun.net.httpserver.HttpServer");
        } catch(Exception e) {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

    }

}
