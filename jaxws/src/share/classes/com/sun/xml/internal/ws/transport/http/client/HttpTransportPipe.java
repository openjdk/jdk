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
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.internal.ws.transport.http.WSHTTPConnection;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link Pipe} and {@link Tube} that sends a request to a remote HTTP server.
 *
 * @author Jitendra Kotamraju
 */
public class HttpTransportPipe extends AbstractTubeImpl {

    private final Codec codec;

    public HttpTransportPipe(Codec codec) {
        this.codec = codec;
    }

    /**
     * Copy constructor for {@link Tube#copy(TubeCloner)}.
     */
    private HttpTransportPipe(HttpTransportPipe that, TubeCloner cloner) {
        this( that.codec.copy() );
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
            Map<String, List<String>> reqHeaders = (Map<String, List<String>>) request.invocationProperties.get(MessageContext.HTTP_REQUEST_HEADERS);
            //assign empty map if its null
            if(reqHeaders == null){
                reqHeaders = new HashMap<String, List<String>>();
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
                writeSOAPAction(reqHeaders, ct.getSOAPActionHeader(),request);

                if(dump)
                    dump(buf, "HTTP request", reqHeaders);

                buf.writeTo(con.getOutput());
            } else {
                // Set static Content-Type
                reqHeaders.put("Content-Type", Collections.singletonList(ct.getContentType()));
                if (ct.getAcceptHeader() != null) {
                    reqHeaders.put("Accept", Collections.singletonList(ct.getAcceptHeader()));
                }
                writeSOAPAction(reqHeaders, ct.getSOAPActionHeader(), request);

                if(dump) {
                    ByteArrayBuffer buf = new ByteArrayBuffer();
                    codec.encode(request, buf);
                    dump(buf, "HTTP request", reqHeaders);
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

            con.checkResponseCode();
            if (con.statusCode== WSHTTPConnection.ONEWAY) {
                return request.createClientResponse(null);    // one way. no response given.
            }
            String contentType = con.getContentType();
            if (contentType == null) {
                throw new WebServiceException("No Content-type in the header!");
            }

            // TODO check if returned MIME type is the same as that which was sent
            // or is acceptable if an Accept header was used
            Packet reply = request.createClientResponse(null);
            //reply.addSatellite(new HttpResponseProperties(con));
            reply.wasTransportSecure = con.isSecure();
            InputStream response = con.getInput();
            if(dump) {
                ByteArrayBuffer buf = new ByteArrayBuffer();
                buf.write(response);
                dump(buf,"HTTP response "+con.statusCode, con.getHeaders());
                response = buf.newInputStream();
            }
            codec.decode(response, contentType, reply);
            return reply;
        } catch(WebServiceException wex) {
            throw wex;
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
    }

    /**
     * write SOAPAction header if the soapAction parameter is non-null or BindingProvider properties set.
     * BindingProvider properties take precedence.
     */
    private void writeSOAPAction(Map<String, List<String>> reqHeaders, String soapAction, Packet packet) {
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
