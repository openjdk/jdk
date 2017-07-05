/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api;


import com.sun.istack.internal.Nullable;

import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Iterator;

/**
 * Represents the endpoint address URI.
 *
 * <p>
 * Conceptually this can be really thought of as an {@link URI},
 * but it hides some of the details that improve the performance.
 *
 * <p>
 * Being an {@link URI} allows this class to represent custom made-up URIs
 * (like "jms" for example.) Whenever possible, this object
 * also creates an {@link URL} (this is only possible when the address
 * has a registered {@link URLStreamHandler}), so that if the clients
 * of this code wants to use it, it can do so.
 *
 *
 * <h3>How it improves the performance</h3>
 * <ol>
 *  <li>
 *  Endpoint address is often eventually turned into an {@link URLConnection},
 *  and given that generally this value is read more often than being set,
 *  it makes sense to eagerly turn it into an {@link URL},
 *  thereby avoiding a repeated conversion.
 *
 *  <li>
 *  JDK spends a lot of time choosing a list of {@link Proxy}
 *  to connect to an {@link URL}. Since the default proxy selector
 *  implementation always return the same proxy for the same URL,
 *  we can determine the proxy by ourselves to let JDK skip its
 *  proxy-discovery step.
 *
 *  (That said, user-defined proxy selector can do a lot of interesting things
 *  --- like doing a round-robin, or pick one from a proxy farm randomly,
 *  and so it's dangerous to stick to one proxy. For this case,
 *  we still let JDK decide the proxy. This shouldn't be that much of an
 *  disappointment, since most people only mess with system properties,
 *  and never with {@link ProxySelector}. Also, avoiding optimization
 *  with non-standard proxy selector allows people to effectively disable
 *  this optimization, which may come in handy for a trouble-shooting.)
 * </ol>
 *
 * @author Kohsuke Kawaguchi
 */
public final class EndpointAddress {
    @Nullable
    private URL url;
    private final URI uri;
    private final String stringForm;
    /**
     * Pre-selected proxy.
     *
     * If {@link #url} is null, this field is null.
     * Otherwise, this field could still be null if the proxy couldn't be chosen
     * upfront.
     */
    private Proxy proxy;

    public EndpointAddress(URI uri) {
        this.uri = uri;
        this.stringForm = uri.toString();
        try {
            this.url = uri.toURL();
            proxy = chooseProxy();
        } catch (MalformedURLException e) {
            // ignore
        }
    }

    /**
     *
     * @see #create(String)
     */
    public EndpointAddress(String url) throws URISyntaxException {
        this.uri = new URI(url);
        this.stringForm = url;
        try {
            this.url = new URL(url);
            proxy = chooseProxy();
        } catch (MalformedURLException e) {
            // ignore
        }
    }

    /**
     * Creates a new {@link EndpointAddress} with a reasonably
     * generic error handling.
     */
    public static EndpointAddress create(String url) {
        try {
            return new EndpointAddress(url);
        } catch(URISyntaxException e) {
            throw new WebServiceException("Illegal endpoint address: "+url,e);
        }
    }

    private Proxy chooseProxy() {
        ProxySelector sel =
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<ProxySelector>() {
                    public ProxySelector run() {
                        return ProxySelector.getDefault();
                    }
                });

        if(sel==null)
            return Proxy.NO_PROXY;


        if(!sel.getClass().getName().equals("sun.net.spi.DefaultProxySelector"))
            // user-defined proxy. may return a different proxy for each invocation
            return null;

        Iterator<Proxy> it = sel.select(uri).iterator();
        if(it.hasNext())
            return it.next();

        return Proxy.NO_PROXY;
    }

    /**
     * Returns an URL of this endpoint adress.
     *
     * @return
     *      null if this endpoint address doesn't have a registered {@link URLStreamHandler}.
     */
    public URL getURL() {
        return url;
    }

    /**
     * Returns an URI of the endpoint address.
     *
     * @return
     *      always non-null.
     */
    public URI getURI() {
        return uri;
    }

    /**
     * Tries to open {@link URLConnection} for this endpoint.
     *
     * <p>
     * This is possible only when an endpoint address has
     * the corresponding {@link URLStreamHandler}.
     *
     * @throws IOException
     *      if {@link URL#openConnection()} reports an error.
     * @throws AssertionError
     *      if this endpoint doesn't have an associated URL.
     *      if the code is written correctly this shall never happen.
     */
    public URLConnection openConnection() throws IOException {
        assert url!=null : uri+" doesn't have the corresponding URL";
        if(proxy!=null)
            return url.openConnection(proxy);
        else
            return url.openConnection();
    }

    public String toString() {
        return stringForm;
    }
}
