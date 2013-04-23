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

package com.sun.xml.internal.ws.api.message;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.MTOMFeature;

import com.oracle.webservices.internal.api.EnvelopeStyle;
import com.oracle.webservices.internal.api.EnvelopeStyleFeature;
import com.oracle.webservices.internal.api.message.MessageContext;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Codecs;
import static com.sun.xml.internal.ws.transport.http.HttpAdapter.fixQuotesAroundSoapAction;

/**
 * The MessageContextFactory implements com.oracle.webservices.internal.api.message.MessageContextFactory as
 * a factory of Packet and public facade of Codec(s).
 *
 * @author shih-chang.chen@oracle.com
 */
public class MessageContextFactory extends com.oracle.webservices.internal.api.message.MessageContextFactory {

    private WSFeatureList features;
    private Codec soapCodec;
    private Codec xmlCodec;
    private EnvelopeStyleFeature envelopeStyle;
    private EnvelopeStyle.Style singleSoapStyle;

    public MessageContextFactory(WebServiceFeature[] wsf) {
        this(new com.sun.xml.internal.ws.binding.WebServiceFeatureList(wsf));
    }

    public MessageContextFactory(WSFeatureList wsf) {
        features = wsf;
        envelopeStyle = features.get(EnvelopeStyleFeature.class);
        if (envelopeStyle == null) {//Default to SOAP11
            envelopeStyle = new EnvelopeStyleFeature(new EnvelopeStyle.Style[]{EnvelopeStyle.Style.SOAP11});
            features.mergeFeatures(new WebServiceFeature[]{envelopeStyle}, false);
        }
        for (EnvelopeStyle.Style s : envelopeStyle.getStyles()) {
            if (s.isXML()) {
                if (xmlCodec == null) xmlCodec = Codecs.createXMLCodec(features);
            } else {
                if (soapCodec == null) soapCodec = Codecs.createSOAPBindingCodec(features);
                singleSoapStyle = s;
            }
        }
    }

    protected com.oracle.webservices.internal.api.message.MessageContextFactory newFactory(WebServiceFeature... f) {
        return new com.sun.xml.internal.ws.api.message.MessageContextFactory(f);
    }


    public com.oracle.webservices.internal.api.message.MessageContext createContext() {
        return packet(null);
    }

    public com.oracle.webservices.internal.api.message.MessageContext createContext(SOAPMessage soap) {
        throwIfIllegalMessageArgument(soap);
        return packet(Messages.create(soap));
    }

    public MessageContext createContext(Source m, com.oracle.webservices.internal.api.EnvelopeStyle.Style envelopeStyle) {
        throwIfIllegalMessageArgument(m);
        return packet(Messages.create(m, SOAPVersion.from(envelopeStyle)));
    }

    public com.oracle.webservices.internal.api.message.MessageContext createContext(Source m) {
        throwIfIllegalMessageArgument(m);
        return packet(Messages.create(m, SOAPVersion.from(singleSoapStyle)));
    }

    public com.oracle.webservices.internal.api.message.MessageContext createContext(InputStream in, String contentType) throws IOException {
        throwIfIllegalMessageArgument(in);
        //TODO when do we use xmlCodec?
        Packet p = packet(null);
        soapCodec.decode(in, contentType, p);
        return p;
    }

    /**
     * @deprecated http://java.net/jira/browse/JAX_WS-1077
     */
    @Deprecated
    public com.oracle.webservices.internal.api.message.MessageContext createContext(InputStream in, MimeHeaders headers) throws IOException {
        String contentType = getHeader(headers, "Content-Type");
        Packet packet = (Packet) createContext(in, contentType);
        packet.acceptableMimeTypes = getHeader(headers, "Accept");
        packet.soapAction = fixQuotesAroundSoapAction(getHeader(headers, "SOAPAction"));
//      packet.put(Packet.INBOUND_TRANSPORT_HEADERS, toMap(headers));
        return packet;
    }

    static String getHeader(MimeHeaders headers, String name) {
        String[] values = headers.getHeader(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    static Map<String, List<String>> toMap(MimeHeaders headers) {
        HashMap<String, List<String>> map = new HashMap<String, List<String>>();
        for (Iterator<MimeHeader> i = headers.getAllHeaders(); i.hasNext();) {
            MimeHeader mh = i.next();
            List<String> values = map.get(mh.getName());
            if (values == null) {
                values = new ArrayList<String>();
                map.put(mh.getName(), values);
            }
            values.add(mh.getValue());
        }
        return map;
    }

    public MessageContext createContext(Message m) {
        throwIfIllegalMessageArgument(m);
        return packet(m);
    }

    private Packet packet(Message m) {
        final Packet p = new Packet();
        //TODO when do we use xmlCodec?
        p.codec = soapCodec;
        if (m != null) p.setMessage(m);
        MTOMFeature mf = features.get(MTOMFeature.class);
        if (mf != null) {
            p.setMtomFeature(mf);
        }
        return p;
    }

    private void throwIfIllegalMessageArgument(Object message)
        throws IllegalArgumentException
    {
        if (message == null) {
            throw new IllegalArgumentException("null messages are not allowed.  Consider using MessageContextFactory.createContext()");
        }
    }

    @Deprecated
    public com.oracle.webservices.internal.api.message.MessageContext doCreate() {
        return packet(null);
    }
    @Deprecated
    public com.oracle.webservices.internal.api.message.MessageContext doCreate(SOAPMessage m) {
        return createContext(m);
    }
    @Deprecated
    public com.oracle.webservices.internal.api.message.MessageContext doCreate(Source x, SOAPVersion soapVersion) {
        return packet(Messages.create(x, soapVersion));
    }
}
