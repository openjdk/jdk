/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceFeature;

import com.sun.xml.internal.org.jvnet.ws.EnvelopeStyle;
import com.sun.xml.internal.org.jvnet.ws.EnvelopeStyleFeature;
import com.sun.xml.internal.org.jvnet.ws.message.MessageContext;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Codecs;

/**
 * The MessageContextFactory implements com.sun.xml.internal.org.jvnet.ws.message.MessageContextFactory as
 * a factory of Packet and public facade of Codec(s).
 *
 * @author shih-chang.chen@oracle.com
 */
public class MessageContextFactory extends com.sun.xml.internal.org.jvnet.ws.message.MessageContextFactory {

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

    protected com.sun.xml.internal.org.jvnet.ws.message.MessageContextFactory newFactory(WebServiceFeature... f) {
        return new com.sun.xml.internal.ws.api.message.MessageContextFactory(f);
    }

    public MessageContext createContext(SOAPMessage soap) {
        return packet(Messages.create(soap));
    }

    public MessageContext createContext(Source m, EnvelopeStyle.Style envelopeStyle) {
        return packet(Messages.create(m, SOAPVersion.from(envelopeStyle)));
    }

    public MessageContext createContext(Source m) {
        return packet(Messages.create(m, SOAPVersion.from(singleSoapStyle)));
    }

    public MessageContext createContext(InputStream in, String contentType) throws IOException {
        //TODO when do we use xmlCodec?
        Packet p = packet(null);
        soapCodec.decode(in, contentType, p);
        return p;
    }

    private Packet packet(Message m) {
        final Packet p = new Packet();
        //TODO when do we use xmlCodec?
        p.codec = soapCodec;
        if (m != null) p.setMessage(m);
        return p;
    }



    public MessageContext doCreate() {
        return packet(null);
    }
    public MessageContext doCreate(SOAPMessage m) {
        return createContext(m);
    }
    public MessageContext doCreate(Source x, SOAPVersion soapVersion) {
        return packet(Messages.create(x, soapVersion));
    }
}
