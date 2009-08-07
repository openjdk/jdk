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
package com.sun.xml.internal.ws.transport.http.client;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.*;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.internal.ws.transport.http.WSHTTPConnection;
import com.sun.xml.internal.ws.transport.Headers;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.client.ClientTransportException;
import com.sun.xml.internal.ws.resources.ClientMessages;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.net.HttpURLConnection;

/**
 * {@link Pipe} and {@link Tube} that sends a request to a remote HTTP server.
 *
 * TODO: need to create separate HTTP transport pipes for binding. SOAP1.1, SOAP1.2,
 * TODO: XML/HTTP differ in handling status codes.
 *
 * @author Jitendra Kotamraju
 */
public class HttpTransportPipe extends AbstractTubeImpl {

    private final Codec codec;
    private final WSBinding binding;

    public HttpTransportPipe(Codec codec, WSBinding binding) {
        this.codec = codec;
        this.binding = binding;
    }

    /**
     * Copy constructor for {@link Tube#copy(TubeCloner)}.
     */
    private HttpTransportPipe(HttpTransportPipe that, TubeCloner cloner) {
        this( that.codec.copy(), that.binding);
        cloner.add(that,this);
    }

    public NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException("HttpTransportPipe's processException shouldn't be called.");
    }

    public NextAction processRequest(@NotNull Packet request) {
        return doReturnWith(process(request));
    }

    public NextAction processResponse(@NotNull Packet response) {
        throw new IllegalStateException("HttpTransportPipe's processResponse shouldn't be called.");
    }

    public Packet process(Packet request) {
        HttpClientTransport con;
        try {
            // get transport headers from message
            Map<String, List<String>> reqHeaders = new Headers();
            Map<String, List<String>> userHeaders = (Map<String, List<String>>) request.invocationProperties.get(MessageContext.HTTP_REQUEST_HEADERS);
            if (userHeaders != null) {
                // userHeaders may not be modifiable like SingletonMap, just copy them
                reqHeaders.putAll(userHeaders);
            }

            con = new HttpClientTransport(request,reqHeaders);
            request.addSatellite(new HttpResponseProperties(con));

            ContentType ct = codec.getStaticContentType(request);
            if (ct == null) {
                ByteArrayBuffer buf = new ByteArrayBuffer();

                ct = codec.encode(request, buf);
                // data size is available, set it as Content-Length
                reqHeaders.put("Content-Length", Collections.singletonList(Integer.toString(buf.size())));
                reqHeaders.put("Content-Type", Collections.singletonList(ct.getContentType()));
                if (ct.getAcceptHeader() != null) {
                    reqHeaders.put("Accept", Collections.singletonList(ct.getAcceptHeader()));
                }
                if (binding instanceof SOAPBinding) {
                    writeSOAPAction(reqHeaders, ct.getSOAPActionHeader(),request);
                }

                if(dump)
                    dump(buf, "HTTP request", reqHeaders);

                buf.writeTo(con.getOutput());
            } else {
                // Set static Content-Type
                reqHeaders.put("Content-Type", Collections.singletonList(ct.getContentType()));
                if (ct.getAcceptHeader() != null) {
                    reqHeaders.put("Accept", Collections.singletonList(ct.getAcceptHeader()));
                }
                if (binding instanceof SOAPBinding) {
                    writeSOAPAction(reqHeaders, ct.getSOAPActionHeader(), request);
                }

                if(dump) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    codec.encode(request, buf);
                    dump(buf, "HTTP request - "+request.endpointAddress, reqHeaders);
                    OutputStream out = con.getOutput();
                    if (out != null) {
                        buf.writeTo(out);
                    }
                } else {
                    OutputStream os = con.getOutput();
                    if (os != null) {
                        codec.encode(request, os);
                    }
                }
            }

            con.closeOutput();

            con.readResponseCodeAndMessage();   // throws IOE
            InputStream response = con.getInput();
            if(dump) {
                ByteArrayBuffer buf = new ByteArrayBuffer();
                if (response != null) {
                    buf.write(response);
                    response.close();
                }
                dump(buf,"HTTP response - "+request.endpointAddress+" - "+con.statusCode, con.getHeaders());
                response = buf.newInputStream();
            }

            if (con.statusCode== WSHTTPConnection.ONEWAY || (request.expectReply != null && !request.expectReply)) {
                checkStatusCodeOneway(response, con.statusCode, con.statusMessage);   // throws ClientTransportException
                return request.createClientResponse(null);    // one way. no response given.
            }

            checkStatusCode(response, con.statusCode, con.statusMessage); // throws ClientTransportException

            String contentType = con.getContentType();
            if (contentType == null) {
                throw new WebServiceException("No Content-type in the header!");
            }

            // TODO check if returned MIME type is the same as that which was sent
            // or is acceptable if an Accept header was used
            Packet reply = request.createClientResponse(null);
            //reply.addSatellite(new HttpResponseProperties(con));
            reply.wasTransportSecure = con.isSecure();
            codec.decode(response, contentType, reply);
            return reply;
        } catch(WebServiceException wex) {
            throw wex;
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
    }

    private void checkStatusCode(InputStream in, int statusCode, String statusMessage) throws IOException {
        // SOAP1.1 and SOAP1.2 differ here
        if (binding instanceof SOAPBinding) {
            if (statusCode != HttpURLConnection.HTTP_OK && statusCode != HttpURLConnection.HTTP_INTERNAL_ERROR) {
                if (in != null) {
                    in.close();
                }
                throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode, statusMessage));
            }
        }
        // Every status code is OK for XML/HTTP
    }

    private void checkStatusCodeOneway(InputStream in, int statusCode, String statusMessage) throws IOException {
        if (statusCode != WSHTTPConnection.ONEWAY && statusCode != WSHTTPConnection.OK) {
            if (in != null) {
                in.close();
            }
            throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(statusCode,statusMessage));
        }
    }

    /**
     * write SOAPAction header if the soapAction parameter is non-null or BindingProvider properties set.
     * BindingProvider properties take precedence.
     */
    private void writeSOAPAction(Map<String, List<String>> reqHeaders, String soapAction, Packet packet) {
        //dont write SOAPAction HTTP header for SOAP 1.2 messages.
        if(SOAPVersion.SOAP_12.equals(binding.getSOAPVersion()))
            return;
        if (soapAction != null)
            reqHeaders.put("SOAPAction", Collections.singletonList(soapAction));
        else
            reqHeaders.put("SOAPAction", Collections.singletonList("\"\""));
    }

    public void preDestroy() {
        // nothing to do. Intentionally left empty.
    }

    public HttpTransportPipe copy(TubeCloner cloner) {
        return new HttpTransportPipe(this,cloner);
    }

    private void dump(ByteArrayBuffer buf, String caption, Map<String, List<String>> headers) throws IOException {
        System.out.println("---["+caption +"]---");
        for (Entry<String,List<String>> header : headers.entrySet()) {
            if(header.getValue().isEmpty()) {
                // I don't think this is legal, but let's just dump it,
                // as the point of the dump is to uncover problems.
                System.out.println(header.getValue());
            } else {
                for (String value : header.getValue()) {
                    System.out.println(header.getKey()+": "+value);
                }
            }
        }

        buf.writeTo(System.out);
        System.out.println("--------------------");
    }

    /**
     * Dumps what goes across HTTP transport.
     */
    public static boolean dump;

    static {
        boolean b;
        try {
            b = Boolean.getBoolean(HttpTransportPipe.class.getName()+".dump");
        } catch( Throwable t ) {
            b = false;
        }
        dump = b;
    }
}
