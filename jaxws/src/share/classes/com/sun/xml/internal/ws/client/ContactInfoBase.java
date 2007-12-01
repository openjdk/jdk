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
package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.pept.encoding.Decoder;
import com.sun.xml.internal.ws.pept.encoding.Encoder;
import com.sun.xml.internal.ws.pept.ept.ContactInfo;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.TargetFinder;
import com.sun.xml.internal.ws.pept.protocol.Interceptors;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.soap.ClientEncoderDecoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;

import javax.xml.ws.soap.SOAPBinding;


/**
 * @author WS Development Team
 */
public class ContactInfoBase implements ContactInfo, SOAPEPTFactory {
    protected WSConnection _connection;
    protected MessageDispatcher _messageDispatcher;
    protected Encoder _encoder;
    protected Decoder _decoder;
    private String bindingId;
    private InternalEncoder internalEncoder;

    public ContactInfoBase(WSConnection connection,
                           MessageDispatcher messageDispatcher, Encoder encoder, Decoder decoder,
                           String bindingId) {
        _connection = connection;
        _messageDispatcher = messageDispatcher;
        _encoder = encoder;
        _decoder = decoder;
        internalEncoder = new ClientEncoderDecoder();
        this.bindingId = bindingId;
    }

    public ContactInfoBase() {
        _connection = null;
        _messageDispatcher = null;
        _encoder = null;
        _decoder = null;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.ContactInfo#getConnection(com.sun.pept.ept.MessageInfo)
     */
    public WSConnection getConnection(MessageInfo arg0) {
        return _connection;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.EPTFactory#getMessageDispatcher(com.sun.pept.ept.MessageInfo)
     */
    public MessageDispatcher getMessageDispatcher(MessageInfo arg0) {
        return _messageDispatcher;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.EPTFactory#getEncoder(com.sun.pept.ept.MessageInfo)
     */
    public Encoder getEncoder(MessageInfo arg0) {
        return _encoder;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.EPTFactory#getDecoder(com.sun.pept.ept.MessageInfo)
     */
    public Decoder getDecoder(MessageInfo arg0) {
        return _decoder;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.EPTFactory#getInterceptors(com.sun.pept.ept.MessageInfo)
     */
    public Interceptors getInterceptors(MessageInfo arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.sun.pept.ept.EPTFactory#getTargetFinder(com.sun.pept.ept.MessageInfo)
     */
    public TargetFinder getTargetFinder(MessageInfo arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    public SOAPEncoder getSOAPEncoder() {
        return (SOAPEncoder) _encoder;
    }

    public SOAPDecoder getSOAPDecoder() {
        return (SOAPDecoder) _decoder;
    }

    public InternalEncoder getInternalEncoder() {
        return internalEncoder;
    }

    public String getBindingId() {
        if (bindingId == null) {
            return SOAPBinding.SOAP11HTTP_BINDING;
        }

        return bindingId;
    }
}
