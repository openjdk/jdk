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
package com.sun.xml.internal.ws.server;

import com.sun.xml.internal.ws.pept.encoding.Decoder;
import com.sun.xml.internal.ws.pept.encoding.Encoder;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.TargetFinder;
import com.sun.xml.internal.ws.pept.protocol.Interceptors;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.xml.XMLDecoder;
import com.sun.xml.internal.ws.encoding.xml.XMLEPTFactory;
import com.sun.xml.internal.ws.encoding.xml.XMLEncoder;

/**
 * @author WS Development Team
 */
public class XMLEPTFactoryImpl implements XMLEPTFactory {
    private Encoder encoder;
    private Decoder decoder;
    private XMLEncoder xmlEncoder;
    private XMLDecoder xmlDecoder;
    private InternalEncoder internalEncoder;
    private TargetFinder targetFinder;
    private MessageDispatcher messageDispatcher;

    public XMLEPTFactoryImpl(Encoder encoder, Decoder decoder,
            TargetFinder targetFinder, MessageDispatcher messageDispatcher) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.targetFinder = targetFinder;
        this.messageDispatcher = messageDispatcher;
    }

    public XMLEPTFactoryImpl(XMLEncoder xmlEncoder, XMLDecoder xmlDecoder,
                             InternalEncoder internalEncoder,
                             TargetFinder targetFinder, MessageDispatcher messageDispatcher) {
        this.xmlEncoder = xmlEncoder;
        this.xmlDecoder = xmlDecoder;
        this.encoder = null;
        this.decoder = null;
        this.internalEncoder = internalEncoder;
        this.targetFinder = targetFinder;
        this.messageDispatcher = messageDispatcher;
    }

    public Encoder getEncoder(MessageInfo messageInfo) {
        messageInfo.setEncoder(encoder);
        return messageInfo.getEncoder();
    }

    public Decoder getDecoder(MessageInfo messageInfo) {
        messageInfo.setDecoder(decoder);
        return messageInfo.getDecoder();
    }

    public TargetFinder getTargetFinder(MessageInfo messageInfo) {
        return targetFinder;
    }

    public MessageDispatcher getMessageDispatcher(MessageInfo messageInfo) {
        messageInfo.setMessageDispatcher(messageDispatcher);
        return messageDispatcher;
    }

    /*
     * @see com.sun.istack.internal.pept.ept.EPTFactory#getInterceptors(com.sun.istack.internal.pept.ept.MessageInfo)
     */
    public Interceptors getInterceptors(MessageInfo x) {
        return null;
    }

    /*
     * @see com.sun.xml.internal.ws.encoding.jaxb.LogicalEPTFactory#getSoapEncoder()
     */
    public XMLEncoder getXMLEncoder() {
        return xmlEncoder;
    }

    /*
     * @see com.sun.xml.internal.ws.encoding.jaxb.LogicalEPTFactory#getSoapDecoder()
     */
    public XMLDecoder getXMLDecoder() {
        return xmlDecoder;
    }

    public InternalEncoder getInternalEncoder() {
        return internalEncoder;
    }


}
