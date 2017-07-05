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

package com.sun.xml.internal.ws.transport.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.ws.Binding;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPBinding;

import com.oracle.webservices.internal.api.message.PropertySet;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.NonAnonymousResponseProcessor;
import com.sun.xml.internal.ws.api.ha.HaInfo;
import com.sun.xml.internal.ws.api.message.ExceptionHasMessage;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.server.AbstractServerAsyncTransport;
import com.sun.xml.internal.ws.api.server.Adapter;
import com.sun.xml.internal.ws.api.server.BoundEndpoint;
import com.sun.xml.internal.ws.api.server.DocumentAddressResolver;
import com.sun.xml.internal.ws.api.server.Module;
import com.sun.xml.internal.ws.api.server.PortAddressResolver;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.ServiceDefinition;
import com.sun.xml.internal.ws.api.server.TransportBackChannel;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.resources.WsservletMessages;
import com.sun.xml.internal.ws.server.UnsupportedMediaException;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.Pool;


/**
 * {@link com.sun.xml.internal.ws.api.server.Adapter} that receives messages in HTTP.
 *
 * <p>
 * This object also assigns unique query string (such as "xsd=1") to
 * each {@link com.sun.xml.internal.ws.api.server.SDDocument} so that they can be served by HTTP GET requests.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class HttpAdapter extends Adapter<HttpAdapter.HttpToolkit> {

    /**
     * {@link com.sun.xml.internal.ws.api.server.SDDocument}s keyed by the query string like "?abc".
     * Used for serving documents via HTTP GET.
     *
     * Empty if the endpoint doesn't have {@link com.sun.xml.internal.ws.api.server.ServiceDefinition}.
     * Read-only.
     */
    protected Map<String,SDDocument> wsdls;

    /**
     * Reverse map of {@link #wsdls}. Read-only.
     */
    private Map<SDDocument,String> revWsdls;

    /**
     * A reference to the service definition from which the map of wsdls/revWsdls
     * was created. This allows us to establish if the service definition documents
     * have changed in the meantime.
     */
    private ServiceDefinition serviceDefinition = null;

    public final HttpAdapterList<? extends HttpAdapter> owner;

    /**
     * Servlet URL pattern with which this {@link com.sun.xml.internal.ws.transport.http.HttpAdapter} is associated.
     */
    public final String urlPattern;

    protected boolean stickyCookie;

    protected boolean disableJreplicaCookie = false;

    /**
     * Creates a lone {@link com.sun.xml.internal.ws.transport.http.HttpAdapter} that does not know of any other
     * {@link com.sun.xml.internal.ws.transport.http.HttpAdapter}s.
     *
     * This is convenient for creating an {@link com.sun.xml.internal.ws.transport.http.HttpAdapter} for an environment
     * where they don't know each other (such as JavaSE deployment.)
     *
     * @param endpoint web service endpoint
     * @return singe adapter to process HTTP messages
     */
    public static HttpAdapter createAlone(WSEndpoint endpoint) {
        return new DummyList().createAdapter("","",endpoint);
    }

    /**
     * @deprecated
     *      remove as soon as we can update the test util.
     * @param endpoint web service endpoint
     * @param owner list of related adapters
     */
    protected HttpAdapter(WSEndpoint endpoint,
                          HttpAdapterList<? extends HttpAdapter> owner) {
        this(endpoint,owner,null);
    }

    protected HttpAdapter(WSEndpoint endpoint,
                          HttpAdapterList<? extends HttpAdapter> owner,
                          String urlPattern) {
        super(endpoint);
        this.owner = owner;
        this.urlPattern = urlPattern;

        initWSDLMap(endpoint.getServiceDefinition());
    }

    /**
     * Return the last known service definition of the endpoint.
     *
     * @return The service definition of the endpoint
     */
    public ServiceDefinition getServiceDefinition() {
        return this.serviceDefinition;
    }

    /**
     * Fill in WSDL map.
     *
     * @param sdef service definition
     */
    public final void initWSDLMap(ServiceDefinition sdef) {
        this.serviceDefinition = sdef;
        if(sdef==null) {
            wsdls = Collections.emptyMap();
            revWsdls = Collections.emptyMap();
        } else {
            wsdls = new HashMap<String, SDDocument>();  // wsdl=1 --> Doc
            // Sort WSDL, Schema documents based on SystemId so that the same
            // document gets wsdl=x mapping
            Map<String, SDDocument> systemIds = new TreeMap<String, SDDocument>();
            for (SDDocument sdd : sdef) {
                if (sdd == sdef.getPrimary()) { // No sorting for Primary WSDL
                    wsdls.put("wsdl", sdd);
                    wsdls.put("WSDL", sdd);
                } else {
                    systemIds.put(sdd.getURL().toString(), sdd);
                }
            }

            int wsdlnum = 1;
            int xsdnum = 1;
            for (Entry<String, SDDocument> e : systemIds.entrySet()) {
                SDDocument sdd = e.getValue();
                if (sdd.isWSDL()) {
                    wsdls.put("wsdl="+(wsdlnum++),sdd);
                }
                if (sdd.isSchema()) {
                    wsdls.put("xsd="+(xsdnum++),sdd);
                }
            }

            revWsdls = new HashMap<SDDocument,String>();    // Doc --> wsdl=1
            for (Entry<String,SDDocument> e : wsdls.entrySet()) {
                if (!e.getKey().equals("WSDL")) {           // map Doc --> wsdl, not WSDL
                    revWsdls.put(e.getValue(),e.getKey());
                }
            }
        }
    }

    /**
     * Returns the "/abc/def/ghi" portion if
     * the URL pattern is "/abc/def/ghi/*".
     */
    public String getValidPath() {
        if (urlPattern.endsWith("/*")) {
            return urlPattern.substring(0, urlPattern.length() - 2);
        } else {
            return urlPattern;
        }
    }

    @Override
    protected HttpToolkit createToolkit() {
        return new HttpToolkit();
    }

    /**
     * Receives the incoming HTTP connection and dispatches
     * it to JAX-WS. This method returns when JAX-WS completes
     * processing the request and the whole reply is written
     * to {@link WSHTTPConnection}.
     *
     * <p>
     * This method is invoked by the lower-level HTTP stack,
     * and "connection" here is an HTTP connection.
     *
     * <p>
     * To populate a request {@link com.sun.xml.internal.ws.api.message.Packet} with more info,
     * define {@link com.oracle.webservices.internal.api.message.PropertySet.Property properties} on
     * {@link WSHTTPConnection}.
     *
     * @param connection to receive/send HTTP messages for web service endpoints
     * @throws java.io.IOException when I/O errors happen
     */
    public void handle(@NotNull WSHTTPConnection connection) throws IOException {
        if (handleGet(connection)) {
            return;
        }

        // Make sure the Toolkit is recycled by the same pool instance from which it was taken
        final Pool<HttpToolkit> currentPool = getPool();
        // normal request handling
        final HttpToolkit tk = currentPool.take();
        try {
            tk.handle(connection);
        } finally {
            currentPool.recycle(tk);
        }
    }

    public boolean handleGet(@NotNull WSHTTPConnection connection) throws IOException {
        if (connection.getRequestMethod().equals("GET")) {
            // metadata query. let the interceptor run
            for (Component c : endpoint.getComponents()) {
                HttpMetadataPublisher spi = c.getSPI(HttpMetadataPublisher.class);
                if (spi != null && spi.handleMetadataRequest(this, connection)) {
                    return true;
                } // handled
            }

            if (isMetadataQuery(connection.getQueryString())) {
                // Sends published WSDL and schema documents as the default action.
                publishWSDL(connection);
                return true;
            }

            Binding binding = getEndpoint().getBinding();
            if (!(binding instanceof HTTPBinding)) {
                // Writes HTML page with all the endpoint descriptions
                writeWebServicesHtmlPage(connection);
                return true;
            }
        } else if (connection.getRequestMethod().equals("HEAD")) {
            connection.getInput().close();
            Binding binding = getEndpoint().getBinding();
            if (isMetadataQuery(connection.getQueryString())) {
                SDDocument doc = wsdls.get(connection.getQueryString());
                connection.setStatus(doc != null
                        ? HttpURLConnection.HTTP_OK
                        : HttpURLConnection.HTTP_NOT_FOUND);
                connection.getOutput().close();
                connection.close();
                return true;
            } else if (!(binding instanceof HTTPBinding)) {
                connection.setStatus(HttpURLConnection.HTTP_NOT_FOUND);
                connection.getOutput().close();
                connection.close();
                return true;
            }
            // Let the endpoint handle for HTTPBinding
        }

        return false;

    }
    /*
     *
     * @param con
     * @param codec
     * @return
     * @throws IOException
     *         ExceptionHasMessage exception that contains particular fault message
     *         UnsupportedMediaException to indicate to send 415 error code
     */
    private Packet decodePacket(@NotNull WSHTTPConnection con, @NotNull Codec codec) throws IOException {
        String ct = con.getRequestHeader("Content-Type");
        InputStream in = con.getInput();
        Packet packet = new Packet();
        packet.soapAction = fixQuotesAroundSoapAction(con.getRequestHeader("SOAPAction"));
        packet.wasTransportSecure = con.isSecure();
        packet.acceptableMimeTypes = con.getRequestHeader("Accept");
        packet.addSatellite(con);
        addSatellites(packet);
        packet.isAdapterDeliversNonAnonymousResponse = true;
        packet.component = this;
        packet.transportBackChannel = new Oneway(con);
        packet.webServiceContextDelegate = con.getWebServiceContextDelegate();
        packet.setState(Packet.State.ServerRequest);
        if (dump || LOGGER.isLoggable(Level.FINER)) {
            ByteArrayBuffer buf = new ByteArrayBuffer();
            buf.write(in);
            in.close();
            dump(buf, "HTTP request", con.getRequestHeaders());
            in = buf.newInputStream();
        }
        codec.decode(in, ct, packet);
        return packet;
    }

    protected void addSatellites(Packet packet) {
    }

    /**
     * Some stacks may send non WS-I BP 1.2 conforming SoapAction.
     * Make sure SOAPAction is quoted as {@link com.sun.xml.internal.ws.api.message.Packet#soapAction} expects quoted soapAction value.
     *
     * @param soapAction SoapAction HTTP Header
     * @return quoted SOAPAction value
     */
    static public String fixQuotesAroundSoapAction(String soapAction) {
        if(soapAction != null && (!soapAction.startsWith("\"") || !soapAction.endsWith("\"")) ) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Received WS-I BP non-conformant Unquoted SoapAction HTTP header: {0}", soapAction);
            }
            String fixedSoapAction = soapAction;
            if(!soapAction.startsWith("\"")) {
                fixedSoapAction = "\"" + fixedSoapAction;
            }
            if(!soapAction.endsWith("\"")) {
                fixedSoapAction = fixedSoapAction + "\"";
            }
            return fixedSoapAction;
        }
        return soapAction;
    }

    protected NonAnonymousResponseProcessor getNonAnonymousResponseProcessor() {
        return NonAnonymousResponseProcessor.getDefault();
    }

    /**
     * This method is added for the case of the sub-class wants to override the method to
     * print details. E.g. convert soapfault as HTML msg for 403 error connstatus.
     * @param os
     */
    protected void writeClientError(int connStatus, @NotNull OutputStream os, @NotNull Packet packet) throws IOException {
        //do nothing
    }

    private boolean isClientErrorStatus(int connStatus)
    {
        return (connStatus == HttpURLConnection.HTTP_FORBIDDEN); // add more for future.
    }

    private boolean isNonAnonymousUri(EndpointAddress addr){
        return (addr != null) && !addr.toString().equals(AddressingVersion.W3C.anonymousUri) &&
                        !addr.toString().equals(AddressingVersion.MEMBER.anonymousUri);
    }

    private void encodePacket(@NotNull Packet packet, @NotNull WSHTTPConnection con, @NotNull Codec codec) throws IOException {
        if (isNonAnonymousUri(packet.endpointAddress) && packet.getMessage() != null) {
           try {
                // Message is targeted to non-anonymous response endpoint.
                // After call to non-anonymous processor, typically, packet.getMessage() will be null
                // however, processors could use this pattern to modify the response sent on the back-channel,
                // e.g. send custom HTTP headers with the HTTP 202
                    packet = getNonAnonymousResponseProcessor().process(packet);
            } catch (RuntimeException re) {
                // if processing by NonAnonymousResponseProcessor fails, new SOAPFaultMessage is created to be sent
                // to back-channel client
                SOAPVersion soapVersion = packet.getBinding().getSOAPVersion();
                Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, re);
                packet = packet.createServerResponse(faultMsg, packet.endpoint.getPort(), null, packet.endpoint.getBinding());
            }
        }

        if (con.isClosed()) {
            return;                 // Connection is already closed
        }
        Message responseMessage = packet.getMessage();
        addStickyCookie(con);
        addReplicaCookie(con, packet);
        if (responseMessage == null) {
            if (!con.isClosed()) {
                // set the response code if not already set
                // for example, 415 may have been set earlier for Unsupported Content-Type
                if (con.getStatus() == 0) {
                    con.setStatus(WSHTTPConnection.ONEWAY);
                }
                OutputStream os = con.getProtocol().contains("1.1") ? con.getOutput() : new Http10OutputStream(con);
                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    codec.encode(packet, buf);
                    dump(buf, "HTTP response " + con.getStatus(), con.getResponseHeaders());
                    buf.writeTo(os);
                } else {
                    codec.encode(packet, os);
                }
                // close the response channel now
                try {
                    os.close(); // no payload
                } catch (IOException e) {
                    throw new WebServiceException(e);
                }
            }
        } else {
            if (con.getStatus() == 0) {
                // if the appliation didn't set the status code,
                // set the default one.
                con.setStatus(responseMessage.isFault()
                        ? HttpURLConnection.HTTP_INTERNAL_ERROR
                        : HttpURLConnection.HTTP_OK);
            }

            if (isClientErrorStatus(con.getStatus())) {
                OutputStream os = con.getOutput();
                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    writeClientError(con.getStatus(), buf, packet);
                    dump(buf, "HTTP response " + con.getStatus(), con.getResponseHeaders());
                    buf.writeTo(os);
                } else {
                    writeClientError(con.getStatus(), os, packet);
                }
                os.close();
                  return;
            }

            ContentType contentType = codec.getStaticContentType(packet);
            if (contentType != null) {
                con.setContentTypeResponseHeader(contentType.getContentType());
                OutputStream os = con.getProtocol().contains("1.1") ? con.getOutput() : new Http10OutputStream(con);
                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    codec.encode(packet, buf);
                    dump(buf, "HTTP response " + con.getStatus(), con.getResponseHeaders());
                    buf.writeTo(os);
                } else {
                    codec.encode(packet, os);
                }
                os.close();
            } else {

                ByteArrayBuffer buf = new ByteArrayBuffer();
                contentType = codec.encode(packet, buf);
                con.setContentTypeResponseHeader(contentType.getContentType());
                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    dump(buf, "HTTP response " + con.getStatus(), con.getResponseHeaders());
                }
                OutputStream os = con.getOutput();
                buf.writeTo(os);
                os.close();
            }
        }
    }

    /*
     * GlassFish Load-balancer plugin always add a header proxy-jroute on
     * request being send from load-balancer plugin to server
     *
     * JROUTE cookie need to be stamped in two cases
     * 1 : At the time of session creation. In this case, request will not have
     * any JROUTE cookie.
     * 2 : At the time of fail-over. In this case, value of proxy-jroute
     * header(will point to current instance) and JROUTE cookie(will point to
     * previous failed instance) will be different. This logic can be used
     * to determine fail-over scenario.
     */
    private void addStickyCookie(WSHTTPConnection con) {
        if (stickyCookie) {
            String proxyJroute = con.getRequestHeader("proxy-jroute");
            if (proxyJroute == null) {
                // Load-balancer plugin is not front-ending this instance
                return;
            }

            String jrouteId = con.getCookie("JROUTE");
            if (jrouteId == null || !jrouteId.equals(proxyJroute)) {
                // Initial request or failover
                con.setCookie("JROUTE", proxyJroute);
            }
        }
    }

    private void addReplicaCookie(WSHTTPConnection con, Packet packet) {
        if (stickyCookie) {
            HaInfo haInfo = null;
            if (packet.supports(Packet.HA_INFO)) {
                haInfo = (HaInfo)packet.get(Packet.HA_INFO);
            }
            if (haInfo != null) {
                con.setCookie("METRO_KEY", haInfo.getKey());
                if (!disableJreplicaCookie) {
                    con.setCookie("JREPLICA", haInfo.getReplicaInstance());
                }
            }
        }
    }

    public void invokeAsync(final WSHTTPConnection con) throws IOException {
        invokeAsync(con, NO_OP_COMPLETION_CALLBACK);
    }

    public void invokeAsync(final WSHTTPConnection con, final CompletionCallback callback) throws IOException {

            if (handleGet(con)) {
                callback.onCompletion();
                return;
            }
            final Pool<HttpToolkit> currentPool = getPool();
            final HttpToolkit tk = currentPool.take();
            final Packet request;

            try {

                request = decodePacket(con, tk.codec);
            } catch (ExceptionHasMessage e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                Packet response = new Packet();
                response.setMessage(e.getFaultMessage());
                encodePacket(response, con, tk.codec);
                currentPool.recycle(tk);
                con.close();
                callback.onCompletion();
                return;
            } catch (UnsupportedMediaException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                Packet response = new Packet();
                con.setStatus(WSHTTPConnection.UNSUPPORTED_MEDIA);
                encodePacket(response, con, tk.codec);
                currentPool.recycle(tk);
                con.close();
                callback.onCompletion();
                return;
            }

            endpoint.process(request, new WSEndpoint.CompletionCallback() {
                @Override
                public void onCompletion(@NotNull Packet response) {
                    try {
                        try {
                            encodePacket(response, con, tk.codec);
                        } catch (IOException ioe) {
                            LOGGER.log(Level.SEVERE, ioe.getMessage(), ioe);
                        }
                        currentPool.recycle(tk);
                    } finally {
                        con.close();
                        callback.onCompletion();

                    }
                }
            },null);

    }

    public static  final CompletionCallback NO_OP_COMPLETION_CALLBACK = new CompletionCallback() {

        @Override
        public void onCompletion() {
            //NO-OP
        }
    };

    public interface CompletionCallback{
        void onCompletion();
    }

    final class AsyncTransport extends AbstractServerAsyncTransport<WSHTTPConnection> {

        public AsyncTransport() {
            super(endpoint);
        }

        public void handleAsync(WSHTTPConnection con) throws IOException {
            super.handle(con);
        }

        @Override
        protected void encodePacket(WSHTTPConnection con, @NotNull Packet packet, @NotNull Codec codec) throws IOException {
            HttpAdapter.this.encodePacket(packet, con, codec);
        }

        protected @Override @Nullable String getAcceptableMimeTypes(WSHTTPConnection con) {
            return null;
        }

        protected @Override @Nullable TransportBackChannel getTransportBackChannel(WSHTTPConnection con) {
            return new Oneway(con);
        }

        protected @Override @NotNull
        PropertySet getPropertySet(WSHTTPConnection con) {
            return con;
        }

        protected @Override @NotNull WebServiceContextDelegate getWebServiceContextDelegate(WSHTTPConnection con) {
            return con.getWebServiceContextDelegate();
        }
    }

    static final class Oneway implements TransportBackChannel {
        WSHTTPConnection con;
        boolean closed;

        Oneway(WSHTTPConnection con) {
            this.con = con;
        }
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                // close the response channel now
                if (con.getStatus() == 0) {
                    // if the appliation didn't set the status code,
                    // set the default one.
                    con.setStatus(WSHTTPConnection.ONEWAY);
                }

                OutputStream output = null;
                try {
                    output = con.getOutput();
                } catch (IOException e) {
                    // no-op
                }

                if (dump || LOGGER.isLoggable(Level.FINER)) {
                    try {
                        ByteArrayBuffer buf = new ByteArrayBuffer();
                        dump(buf, "HTTP response " + con.getStatus(), con.getResponseHeaders());
                    } catch (Exception e) {
                        throw new WebServiceException(e.toString(), e);
                    }
                }

                if (output != null) {
                        try {
                                output.close(); // no payload
                        } catch (IOException e) {
                                throw new WebServiceException(e);
                        }
                }
                con.close();
            }
        }
    }

    final class HttpToolkit extends Adapter.Toolkit {
        public void handle(WSHTTPConnection con) throws IOException {
            try {
                boolean invoke = false;
                Packet packet;
                try {
                    packet = decodePacket(con, codec);
                    invoke = true;
                } catch(Exception e) {
                    packet = new Packet();
                    if (e instanceof ExceptionHasMessage) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        packet.setMessage(((ExceptionHasMessage)e).getFaultMessage());
                    } else if (e instanceof UnsupportedMediaException) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        con.setStatus(WSHTTPConnection.UNSUPPORTED_MEDIA);
                    } else {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        con.setStatus(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    }
                }
                if (invoke) {
                    try {
                        packet = head.process(packet, con.getWebServiceContextDelegate(),
                                packet.transportBackChannel);
                    } catch(Throwable e) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        if (!con.isClosed()) {
                            writeInternalServerError(con);
                        }
                        return;
                    }
                }
                encodePacket(packet, con, codec);
            } finally {
                if (!con.isClosed()) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Closing HTTP Connection with status: {0}", con.getStatus());
                    }
                    con.close();
                }
            }
        }
    }

    /**
     * Returns true if the given query string is for metadata request.
     *
     * @param query
     *      String like "xsd=1" or "perhaps=some&amp;unrelated=query".
     *      Can be null.
     * @return true for metadata requests
     *         false for web service requests
     */
    private boolean isMetadataQuery(String query) {
        // we intentionally return true even if documents don't exist,
        // so that they get 404.
        return query != null && (query.equals("WSDL") || query.startsWith("wsdl") || query.startsWith("xsd="));
    }

    /**
     * Sends out the WSDL (and other referenced documents)
     * in response to the GET requests to URLs like "?wsdl" or "?xsd=2".
     *
     * @param con
     *      The connection to which the data will be sent.
     *
     * @throws java.io.IOException when I/O errors happen
     */
    public void publishWSDL(@NotNull WSHTTPConnection con) throws IOException {
        con.getInput().close();

        SDDocument doc = wsdls.get(con.getQueryString());
        if (doc == null) {
            writeNotFoundErrorPage(con,"Invalid Request");
            return;
        }

        con.setStatus(HttpURLConnection.HTTP_OK);
        con.setContentTypeResponseHeader("text/xml;charset=utf-8");

        OutputStream os = con.getProtocol().contains("1.1") ? con.getOutput() : new Http10OutputStream(con);

        PortAddressResolver portAddressResolver = getPortAddressResolver(con.getBaseAddress());
        DocumentAddressResolver resolver = getDocumentAddressResolver(portAddressResolver);

        doc.writeTo(portAddressResolver, resolver, os);
        os.close();
    }

    public PortAddressResolver getPortAddressResolver(String baseAddress) {
        return owner.createPortAddressResolver(baseAddress, endpoint.getImplementationClass());
    }

    public DocumentAddressResolver getDocumentAddressResolver(
                        PortAddressResolver portAddressResolver) {
        final String address = portAddressResolver.getAddressFor(endpoint.getServiceName(), endpoint.getPortName().getLocalPart());
        assert address != null;
        return new DocumentAddressResolver() {
            @Override
            public String getRelativeAddressFor(@NotNull SDDocument current, @NotNull SDDocument referenced) {
                // the map on endpoint should account for all SDDocument
                assert revWsdls.containsKey(referenced);
                return address+'?'+ revWsdls.get(referenced);
            }
        };
    }

    /**
     * HTTP/1.0 connections require Content-Length. So just buffer to find out
     * the length.
     */
    private final static class Http10OutputStream extends ByteArrayBuffer {
        private final WSHTTPConnection con;

        Http10OutputStream(WSHTTPConnection con) {
            this.con = con;
        }

        @Override
        public void close() throws IOException {
            super.close();
            con.setContentLengthResponseHeader(size());
            OutputStream os = con.getOutput();
            writeTo(os);
            os.close();
        }
    }

    private void writeNotFoundErrorPage(WSHTTPConnection con, String message) throws IOException {
        con.setStatus(HttpURLConnection.HTTP_NOT_FOUND);
        con.setContentTypeResponseHeader("text/html; charset=utf-8");

        PrintWriter out = new PrintWriter(new OutputStreamWriter(con.getOutput(),"UTF-8"));
        out.println("<html>");
        out.println("<head><title>");
        out.println(WsservletMessages.SERVLET_HTML_TITLE());
        out.println("</title></head>");
        out.println("<body>");
        out.println(WsservletMessages.SERVLET_HTML_NOT_FOUND(message));
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

    private void writeInternalServerError(WSHTTPConnection con) throws IOException {
        con.setStatus(HttpURLConnection.HTTP_INTERNAL_ERROR);
        con.getOutput().close();        // Sets the status code
    }

    private static final class DummyList extends HttpAdapterList<HttpAdapter> {
        @Override
        protected HttpAdapter createHttpAdapter(String name, String urlPattern, WSEndpoint<?> endpoint) {
            return new HttpAdapter(endpoint,this,urlPattern);
        }
    }

    private static void dump(ByteArrayBuffer buf, String caption, Map<String, List<String>> headers) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);
        pw.println("---["+caption +"]---");
        if (headers != null) {
            for (Entry<String, List<String>> header : headers.entrySet()) {
                if (header.getValue().isEmpty()) {
                    // I don't think this is legal, but let's just dump it,
                    // as the point of the dump is to uncover problems.
                    pw.println(header.getValue());
                } else {
                    for (String value : header.getValue()) {
                        pw.println(header.getKey() + ": " + value);
                    }
                }
            }
        }
        buf.writeTo(baos);
        pw.println("--------------------");

        String msg = baos.toString();
        if (dump) {
          System.out.println(msg);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
          LOGGER.log(Level.FINER, msg);
        }
    }

    /*
     * Generates the listing of all services.
     */
    private void writeWebServicesHtmlPage(WSHTTPConnection con) throws IOException {
        if (!publishStatusPage) {
            return;
        }

        // TODO: resurrect the ability to localize according to the current request.

        con.getInput().close();

        // standard browsable page
        con.setStatus(WSHTTPConnection.OK);
        con.setContentTypeResponseHeader("text/html; charset=utf-8");

        PrintWriter out = new PrintWriter(new OutputStreamWriter(con.getOutput(),"UTF-8"));
        out.println("<html>");
        out.println("<head><title>");
        // out.println("Web Services");
        out.println(WsservletMessages.SERVLET_HTML_TITLE());
        out.println("</title></head>");
        out.println("<body>");
        // out.println("<h1>Web Services</h1>");
        out.println(WsservletMessages.SERVLET_HTML_TITLE_2());

        // what endpoints do we have in this system?
        Module module = getEndpoint().getContainer().getSPI(Module.class);
        List<BoundEndpoint> endpoints = Collections.emptyList();
        if(module!=null) {
            endpoints = module.getBoundEndpoints();
        }

        if (endpoints.isEmpty()) {
            // out.println("<p>No JAX-WS context information available.</p>");
            out.println(WsservletMessages.SERVLET_HTML_NO_INFO_AVAILABLE());
        } else {
            out.println("<table width='100%' border='1'>");
            out.println("<tr>");
            out.println("<td>");
            // out.println("Endpoint");
            out.println(WsservletMessages.SERVLET_HTML_COLUMN_HEADER_PORT_NAME());
            out.println("</td>");

            out.println("<td>");
            // out.println("Information");
            out.println(WsservletMessages.SERVLET_HTML_COLUMN_HEADER_INFORMATION());
            out.println("</td>");
            out.println("</tr>");

            for (BoundEndpoint a : endpoints) {
                String endpointAddress = a.getAddress(con.getBaseAddress()).toString();
                out.println("<tr>");

                out.println("<td>");
                out.println(WsservletMessages.SERVLET_HTML_ENDPOINT_TABLE(
                    a.getEndpoint().getServiceName(),
                    a.getEndpoint().getPortName()
                ));
                out.println("</td>");

                out.println("<td>");
                out.println(WsservletMessages.SERVLET_HTML_INFORMATION_TABLE(
                    endpointAddress,
                    a.getEndpoint().getImplementationClass().getName()
                ));
                out.println("</td>");

                out.println("</tr>");
            }
            out.println("</table>");
        }
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

    /**
     * Dumps what goes across HTTP transport.
     */
    public static volatile boolean dump = false;

    public static volatile boolean publishStatusPage = true;

    public static synchronized void setPublishStatus(boolean publish) {
        publishStatusPage = publish;
    }

    static {
        try {
            dump = Boolean.getBoolean(HttpAdapter.class.getName()+".dump");
        } catch( Throwable t ) {
            // OK to ignore this
        }
        try {
            setPublishStatus(System.getProperty(HttpAdapter.class.getName()+".publishStatusPage").equals("true"));
        } catch( Throwable t ) {
            // OK to ignore this
        }
    }

    public static void setDump(boolean dumpMessages) {
        HttpAdapter.dump = dumpMessages;
    }
    private static final Logger LOGGER = Logger.getLogger(HttpAdapter.class.getName());
}
