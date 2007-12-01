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

import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.TargetFinder;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.ServerEncoderDecoder;
import com.sun.xml.internal.ws.protocol.soap.server.ProviderSOAPMD;
import com.sun.xml.internal.ws.spi.runtime.MessageContext;
import javax.xml.ws.Provider;
import javax.xml.ws.soap.SOAPBinding;

import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.server.provider.ProviderPeptTie;
import com.sun.xml.internal.ws.encoding.soap.server.ProviderSED;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.protocol.soap.server.SOAPMessageDispatcher;
import com.sun.xml.internal.ws.encoding.soap.server.SOAP12XMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.server.SOAP12XMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.server.SOAPXMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.server.SOAPXMLEncoder;
import com.sun.xml.internal.ws.encoding.xml.XMLDecoder;
import com.sun.xml.internal.ws.encoding.xml.XMLEncoder;
import com.sun.xml.internal.ws.protocol.xml.server.ProviderXMLMD;
import javax.xml.ws.http.HTTPBinding;

/**
 * factory for creating the appropriate EPTFactory given the BindingId from the EndpointInfo
 * in the RuntimeContext of the MessageInfo
 * Based on MessageInfo data(Binding, Implementor) it selects one the static EPTFactories
 * using Binding information
 * Has a static EPTFactory object for each particular binding.
 * EPTFactories are reused for all the requests.
 * Has static provider EPTFactory objects.
 * The provider EPTFactories are reused for all the requests.
 * The factories reuse encoder, decoder, message dispatcher objects since these objects
 * are Stateless. They are reused for all the requests.
 *
 * @author WS Development Team
 */
public abstract class EPTFactoryFactoryBase {

    public static final ProviderSOAPMD providerMessageDispatcher =
        new ProviderSOAPMD();
    public static final SOAPEncoder soap11Encoder = new SOAPXMLEncoder();
    public static final SOAPDecoder soap11Decoder = new SOAPXMLDecoder();
    public static final SOAPEncoder soap12Encoder = new SOAP12XMLEncoder();
    public static final SOAPDecoder soap12Decoder = new SOAP12XMLDecoder();

    public static final XMLEncoder xmlEncoder = null; //new XMLEncoder();
    public static final XMLDecoder xmlDecoder = null; //new XMLDecoder();

    public static final SOAPMessageDispatcher soap11MessageDispatcher =
        new SOAPMessageDispatcher();
    public static final MessageDispatcher providerXmlMD =
        new ProviderXMLMD();
    public static final InternalEncoder internalSED = new ServerEncoderDecoder();
    public static final InternalEncoder providerSED = new ProviderSED();
    public static final TargetFinder providerTargetFinder =
            new TargetFinderImpl(new ProviderPeptTie());
    public static final TargetFinder targetFinder =
            new TargetFinderImpl(new PeptTie());

    public static final EPTFactory providerSoap11 =
            new EPTFactoryBase(soap11Encoder, soap11Decoder,
                    providerSED, providerTargetFinder,
                providerMessageDispatcher);

    public static final EPTFactory providerSoap12 =
            new EPTFactoryBase(soap12Encoder, soap12Decoder,
                    providerSED, providerTargetFinder,
                providerMessageDispatcher);

    public static final EPTFactory soap11 =
            new EPTFactoryBase(
                soap11Encoder, soap11Decoder,
                    internalSED, targetFinder,
                soap11MessageDispatcher);

    public static final EPTFactory soap12 =
            new EPTFactoryBase(
                soap12Encoder, soap12Decoder,
                    internalSED, targetFinder,
                soap11MessageDispatcher);

    public static final EPTFactory providerXml =
        new XMLEPTFactoryImpl(
            xmlEncoder, xmlDecoder,
                providerSED, providerTargetFinder,
            providerXmlMD);

    /**
     * Choose correct EPTFactory. MessageInfo contains all the needed
     * information like Binding, WSConnection to make that decision.
     * @param mi the MessageInfo object to obtain the BindingID from.
     * @return returns the appropriate EPTFactory for the BindingID in the mi
     */
    public static EPTFactory getEPTFactory(MessageInfo mi) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(mi);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        String bindingId = ((BindingImpl)endpointInfo.getBinding()).getBindingId();
        if(bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING)){
            if (endpointInfo.getImplementor() instanceof Provider) {
                return providerSoap11;
            } else {
                return soap11;
            }
        }else if(bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING)){
            if (endpointInfo.getImplementor() instanceof Provider) {
                return providerSoap12;
            } else {
                return soap12;
            }
        } else if(bindingId.equals(HTTPBinding.HTTP_BINDING)){
            if (endpointInfo.getImplementor() instanceof Provider) {
                return providerXml;
            }
        }
        return null;
    }
}
