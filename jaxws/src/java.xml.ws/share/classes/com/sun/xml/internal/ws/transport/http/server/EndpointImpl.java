/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.api.server.*;
import com.sun.xml.internal.ws.server.EndpointFactory;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.transport.http.HttpAdapterList;
import com.sun.xml.internal.ws.transport.http.HttpAdapter;
import com.sun.istack.internal.NotNull;

import java.net.MalformedURLException;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.ws.*;
import javax.xml.ws.spi.http.HttpContext;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;
import org.w3c.dom.Element;


/**
 * Implements {@link Endpoint}.
 * <p/>
 * <p/>
 * This class accumulates the information necessary to create
 * {@link WSEndpoint}, and then when {@link #publish} method
 * is called it will be created.
 * <p/>
 * <p/>
 * This object also allows accumulated information to be retrieved.
 *
 * @author Jitendra Kotamraju
 */
public class EndpointImpl extends Endpoint {

    private static final WebServicePermission ENDPOINT_PUBLISH_PERMISSION =
            new WebServicePermission("publishEndpoint");

    /**
     * Once the service is published, this field will
     * be set to the {@link HttpEndpoint} instance.
     * <p/>
     * But don't declare the type as {@link HttpEndpoint}
     * to avoid static type dependency that cause the class loading to
     * fail if the LW HTTP server doesn't exist.
     */
    private Object actualEndpoint;

    // information accumulated for creating WSEndpoint
    private final WSBinding binding;
    private @Nullable final Object implementor;
    private List<Source> metadata;
    private Executor executor;
    private Map<String, Object> properties = Collections.emptyMap(); // always non-null
    private boolean stopped;
    private @Nullable EndpointContext endpointContext;
    private @NotNull final Class<?> implClass;
    private final Invoker invoker;
    private Container container;


    public EndpointImpl(@NotNull BindingID bindingId, @NotNull Object impl,
                        WebServiceFeature ... features) {
        this(bindingId, impl, impl.getClass(),
             InstanceResolver.createSingleton(impl).createInvoker(),  features);
    }

    public EndpointImpl(@NotNull BindingID bindingId, @NotNull Class implClass,
                        javax.xml.ws.spi.Invoker invoker,
                        WebServiceFeature ... features) {
        this(bindingId, null, implClass, new InvokerImpl(invoker),  features);
    }

    private EndpointImpl(@NotNull BindingID bindingId, Object impl, @NotNull Class implClass,
                        Invoker invoker, WebServiceFeature ... features) {
        binding = BindingImpl.create(bindingId, features);
        this.implClass = implClass;
        this.invoker = invoker;
        this.implementor = impl;
    }


    /**
     * Wraps an already created {@link WSEndpoint} into an {@link EndpointImpl},
     * and immediately publishes it with the given context.
     *
     * @param wse created endpoint
     * @param serverContext supported http context
     * @deprecated This is a backdoor method. Don't use it unless you know what you are doing.
     */
    public EndpointImpl(WSEndpoint wse, Object serverContext) {
        this(wse, serverContext, null);
    }

    /**
     * Wraps an already created {@link WSEndpoint} into an {@link EndpointImpl},
     * and immediately publishes it with the given context.
     *
     * @param wse created endpoint
     * @param serverContext supported http context
     * @param ctxt endpoint context
     * @deprecated This is a backdoor method. Don't use it unless you know what you are doing.
     */
    public EndpointImpl(WSEndpoint wse, Object serverContext, EndpointContext ctxt) {
        endpointContext = ctxt;
        actualEndpoint = new HttpEndpoint(null, getAdapter(wse, ""));
        ((HttpEndpoint) actualEndpoint).publish(serverContext);
        binding = wse.getBinding();
        implementor = null; // this violates the semantics, but hey, this is a backdoor.
        implClass = null;
        invoker = null;
    }

    /**
     * Wraps an already created {@link WSEndpoint} into an {@link EndpointImpl},
     * and immediately publishes it with the given context.
     *
     * @param wse created endpoint
     * @param address endpoint address
     * @deprecated This is a backdoor method. Don't use it unless you know what you are doing.
     */
    public EndpointImpl(WSEndpoint wse, String address) {
        this(wse, address, null);
    }


    /**
     * Wraps an already created {@link WSEndpoint} into an {@link EndpointImpl},
     * and immediately publishes it with the given context.
     *
     * @param wse created endpoint
     * @param address endpoint address
     * @param ctxt endpoint context
     * @deprecated This is a backdoor method. Don't use it unless you know what you are doing.
     */
    public EndpointImpl(WSEndpoint wse, String address, EndpointContext ctxt) {
        URL url;
        try {
            url = new URL(address);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Cannot create URL for this address " + address);
        }
        if (!url.getProtocol().equals("http")) {
            throw new IllegalArgumentException(url.getProtocol() + " protocol based address is not supported");
        }
        if (!url.getPath().startsWith("/")) {
            throw new IllegalArgumentException("Incorrect WebService address=" + address +
                    ". The address's path should start with /");
        }
        endpointContext = ctxt;
        actualEndpoint = new HttpEndpoint(null, getAdapter(wse, url.getPath()));
        ((HttpEndpoint) actualEndpoint).publish(address);
        binding = wse.getBinding();
        implementor = null; // this violates the semantics, but hey, this is a backdoor.
        implClass = null;
        invoker = null;
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
            throw new IllegalArgumentException("Cannot create URL for this address " + address);
        }
        if (!url.getProtocol().equals("http")) {
            throw new IllegalArgumentException(url.getProtocol() + " protocol based address is not supported");
        }
        if (!url.getPath().startsWith("/")) {
            throw new IllegalArgumentException("Incorrect WebService address=" + address +
                    ". The address's path should start with /");
        }
        createEndpoint(url.getPath());
        ((HttpEndpoint) actualEndpoint).publish(address);
    }

    public void publish(Object serverContext) {
        canPublish();
        if (!com.sun.net.httpserver.HttpContext.class.isAssignableFrom(serverContext.getClass())) {
            throw new IllegalArgumentException(serverContext.getClass() + " is not a supported context.");
        }
        createEndpoint(((com.sun.net.httpserver.HttpContext)serverContext).getPath());
        ((HttpEndpoint) actualEndpoint).publish(serverContext);
    }

    public void publish(HttpContext serverContext) {
        canPublish();
        createEndpoint(serverContext.getPath());
        ((HttpEndpoint) actualEndpoint).publish(serverContext);
    }

    public void stop() {
        if (isPublished()) {
            ((HttpEndpoint) actualEndpoint).stop();
            actualEndpoint = null;
            stopped = true;
        }
    }

    public boolean isPublished() {
        return actualEndpoint != null;
    }

    public List<Source> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.List<Source> metadata) {
        if (isPublished()) {
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
        return new HashMap<String, Object>(properties);
    }

    public void setProperties(Map<String, Object> map) {
        this.properties = new HashMap<String, Object>(map);
    }

    /*
    * Checks the permission of "publishEndpoint" before accessing HTTP classes.
    * Also it checks if there is an available HTTP server implementation.
    */
    private void createEndpoint(String urlPattern) {
        // Checks permission for "publishEndpoint"
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ENDPOINT_PUBLISH_PERMISSION);
        }

        // See if HttpServer implementation is available
        try {
            Class.forName("com.sun.net.httpserver.HttpServer");
        } catch (Exception e) {
            throw new UnsupportedOperationException("Couldn't load light weight http server", e);
        }
        container = getContainer();
        MetadataReader metadataReader = EndpointFactory.getExternalMetadatReader(implClass, binding);
        WSEndpoint wse = WSEndpoint.create(
                implClass, true,
                invoker,
                getProperty(QName.class, Endpoint.WSDL_SERVICE),
                getProperty(QName.class, Endpoint.WSDL_PORT),
                container,
                binding,
                getPrimaryWsdl(metadataReader),
                buildDocList(),
                (EntityResolver) null,
                false
        );
        // Don't load HttpEndpoint class before as it may load HttpServer classes
        actualEndpoint = new HttpEndpoint(executor, getAdapter(wse, urlPattern));
    }

    private <T> T getProperty(Class<T> type, String key) {
        Object o = properties.get(key);
        if (o == null) return null;
        if (type.isInstance(o))
            return type.cast(o);
        else
            throw new IllegalArgumentException("Property " + key + " has to be of type " + type);   // i18n
    }

    /**
     * Convert metadata sources using identity transform. So that we can
     * reuse the Source object multiple times.
     */
    private List<SDDocumentSource> buildDocList() {
        List<SDDocumentSource> r = new ArrayList<SDDocumentSource>();

        if (metadata != null) {
            for (Source source : metadata) {
                try {
                    XMLStreamBufferResult xsbr = XmlUtil.identityTransform(source, new XMLStreamBufferResult());
                    String systemId = source.getSystemId();

                    r.add(SDDocumentSource.create(new URL(systemId), xsbr.getXMLStreamBuffer()));
                } catch (TransformerException te) {
                    throw new ServerRtException("server.rt.err", te);
                } catch (IOException te) {
                    throw new ServerRtException("server.rt.err", te);
                } catch (SAXException e) {
                    throw new ServerRtException("server.rt.err", e);
                } catch (ParserConfigurationException e) {
                    throw new ServerRtException("server.rt.err", e);
                }
            }
        }

        return r;
    }

    /**
     * Gets wsdl from @WebService or @WebServiceProvider
     */
    private @Nullable SDDocumentSource getPrimaryWsdl(MetadataReader metadataReader) {
        // Takes care of @WebService, @WebServiceProvider's wsdlLocation
        EndpointFactory.verifyImplementorClass(implClass, metadataReader);
        String wsdlLocation = EndpointFactory.getWsdlLocation(implClass, metadataReader);
        if (wsdlLocation != null) {
            ClassLoader cl = implClass.getClassLoader();
            URL url = cl.getResource(wsdlLocation);
            if (url != null) {
                return SDDocumentSource.create(url);
            }
            return SDDocumentSource.create(implClass, wsdlLocation);
        }
        return null;
    }

    private void canPublish() {
        if (isPublished()) {
            throw new IllegalStateException(
                    "Cannot publish this endpoint. Endpoint has been already published.");
        }
        if (stopped) {
            throw new IllegalStateException(
                    "Cannot publish this endpoint. Endpoint has been already stopped.");
        }
    }

    public EndpointReference getEndpointReference(Element...referenceParameters) {
        return getEndpointReference(W3CEndpointReference.class, referenceParameters);
    }

    public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, Element...referenceParameters) {
        if (!isPublished()) {
            throw new WebServiceException("Endpoint is not published yet");
        }
        return ((HttpEndpoint)actualEndpoint).getEndpointReference(clazz,referenceParameters);
    }

    @Override
    public void setEndpointContext(EndpointContext ctxt) {
        this.endpointContext = ctxt;
    }

    private HttpAdapter getAdapter(WSEndpoint endpoint, String urlPattern) {
        HttpAdapterList adapterList = null;
        if (endpointContext != null) {
                if (endpointContext instanceof Component) {
                        adapterList = ((Component) endpointContext).getSPI(HttpAdapterList.class);
                }

                if (adapterList == null) {
                    for(Endpoint e : endpointContext.getEndpoints()) {
                        if (e.isPublished() && e != this) {
                            adapterList = ((HttpEndpoint)(((EndpointImpl)e).actualEndpoint)).getAdapterOwner();
                            assert adapterList != null;
                            break;
                        }
                    }
                }
        }
        if (adapterList == null) {
            adapterList = new ServerAdapterList();
        }
        return adapterList.createAdapter("", urlPattern, endpoint);
    }

    /**
     * Endpoints within a EndpointContext get the same container.
     */
    private Container getContainer() {
        if (endpointContext != null) {
                if (endpointContext instanceof Component) {
                        Container c = ((Component) endpointContext).getSPI(Container.class);
                        if (c != null)
                                return c;
                }

            for(Endpoint e : endpointContext.getEndpoints()) {
                if (e.isPublished() && e != this) {
                    return ((EndpointImpl)e).container;
                }
            }
        }
        return new ServerContainer();
    }

    private static class InvokerImpl extends Invoker {
        private javax.xml.ws.spi.Invoker spiInvoker;

        InvokerImpl(javax.xml.ws.spi.Invoker spiInvoker) {
            this.spiInvoker = spiInvoker;
        }

        @Override
        public void start(@NotNull WSWebServiceContext wsc, @NotNull WSEndpoint endpoint) {
            try {
                spiInvoker.inject(wsc);
            } catch (IllegalAccessException e) {
                throw new WebServiceException(e);
            } catch (InvocationTargetException e) {
                throw new WebServiceException(e);
            }
        }

        public Object invoke(@NotNull Packet p, @NotNull Method m, @NotNull Object... args) throws InvocationTargetException, IllegalAccessException {
            return spiInvoker.invoke(m, args);
        }
    }
}
