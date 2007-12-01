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
package com.sun.xml.internal.ws.encoding.soap.client;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.simpletype.EncoderUtils;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.message.FaultCode;
import com.sun.xml.internal.ws.encoding.soap.message.FaultCodeEnum;
import com.sun.xml.internal.ws.encoding.soap.message.FaultReason;
import com.sun.xml.internal.ws.encoding.soap.message.FaultReasonText;
import com.sun.xml.internal.ws.encoding.soap.message.FaultSubcode;
import com.sun.xml.internal.ws.encoding.soap.message.SOAP12FaultInfo;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.model.soap.SOAPRuntimeModel;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.client.dispatch.impl.encoding.DispatchSerializer;

import javax.xml.namespace.QName;
import static javax.xml.stream.XMLStreamConstants.*;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.soap.SOAPBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author WS Development Team
 */
public class SOAP12XMLDecoder extends SOAPXMLDecoder {

    //needs further cleanup
    private static final Logger logger =
        Logger.getLogger (new StringBuffer ().append (com.sun.xml.internal.ws.util.Constants.LoggingDomain).append (".client.dispatch").toString ());

    public SOAP12XMLDecoder () {
    }

    protected DispatchSerializer getSerializerInstance (){
        return DispatchSerializer.SOAP_1_2;
    }

    /*
     *
     * @see SOAPXMLDecoder#decodeFault(XMLStreamReader, InternalMessage, MessageInfo)
     */
    @Override
    protected SOAPFaultInfo decodeFault (XMLStreamReader reader, InternalMessage internalMessage, MessageInfo messageInfo) {
        XMLStreamReaderUtil.verifyReaderState (reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_SOAP_FAULT);

        // env:Code
        XMLStreamReaderUtil.nextElementContent (reader);
        XMLStreamReaderUtil.verifyReaderState (reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_CODE);
        XMLStreamReaderUtil.nextElementContent (reader);

        //env:Value
        QName faultcode = readFaultValue (reader);
        FaultCodeEnum codeValue = FaultCodeEnum.get (faultcode);
        if(codeValue == null)
            throw new DeserializationException ("unknown fault code:", faultcode.toString ());


        //Subcode
        FaultSubcode subcode = null;
        if(reader.getEventType () == START_ELEMENT)
            subcode = readFaultSubcode (reader);
        FaultCode code = new FaultCode (codeValue, subcode);

        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_CODE);
        XMLStreamReaderUtil.nextElementContent (reader);

        FaultReason reason = readFaultReason (reader);
        String node = null;
        String role = null;
        Object detail = null;

        QName name = reader.getName ();
        if(name.equals (SOAP12Constants.QNAME_FAULT_NODE)){
            node = reader.getText ();
        }

        if(name.equals (SOAP12Constants.QNAME_FAULT_ROLE)){
            XMLStreamReaderUtil.nextContent (reader);
            role = reader.getText ();
            XMLStreamReaderUtil.nextElementContent (reader);
            XMLStreamReaderUtil.nextElementContent (reader);
        }

        if(name.equals (SOAP12Constants.QNAME_FAULT_DETAIL)){
            //TODO: process encodingStyle attribute information item
            XMLStreamReaderUtil.nextElementContent (reader);
            detail = readFaultDetail (reader, messageInfo);
            XMLStreamReaderUtil.nextElementContent (reader);
        }

        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_SOAP_FAULT);
        XMLStreamReaderUtil.nextElementContent (reader);

        return new SOAP12FaultInfo (code, reason, node, role, detail);
    }

    protected QName readFaultValue (XMLStreamReader reader){
        XMLStreamReaderUtil.verifyReaderState (reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_VALUE);

        XMLStreamReaderUtil.nextContent (reader);

        String tokens = reader.getText ();

        XMLStreamReaderUtil.next (reader);
        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_VALUE);
        XMLStreamReaderUtil.nextElementContent (reader);

        String uri = "";
        tokens = EncoderUtils.collapseWhitespace (tokens);
        String prefix = XmlUtil.getPrefix (tokens);
        if (prefix != null) {
            uri = reader.getNamespaceURI (prefix);
            if (uri == null) {
                throw new DeserializationException ("xsd.unknownPrefix", prefix);
            }
        }
        String localPart = XmlUtil.getLocalPart (tokens);
        return new QName (uri, localPart);
    }

    protected FaultSubcode readFaultSubcode (XMLStreamReader reader){
        FaultSubcode code = null;
        QName name = reader.getName ();
        if(name.equals (SOAP12Constants.QNAME_FAULT_SUBCODE)){
            XMLStreamReaderUtil.nextElementContent (reader);
            QName faultcode = readFaultValue (reader);
            FaultSubcode subcode = null;
            if(reader.getEventType () == START_ELEMENT)
                subcode = readFaultSubcode (reader);
            code = new FaultSubcode (faultcode, subcode);
            XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
            XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_SUBCODE);
            XMLStreamReaderUtil.nextElementContent (reader);
        }
        return code;
    }

    protected FaultReason readFaultReason (XMLStreamReader reader){
        XMLStreamReaderUtil.verifyReaderState (reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_REASON);
        XMLStreamReaderUtil.nextElementContent (reader);

        //soapenv:Text
        List<FaultReasonText> texts = new ArrayList<FaultReasonText>();
        readFaultReasonTexts (reader, texts);

        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_REASON);
        XMLStreamReaderUtil.nextElementContent (reader);

        FaultReasonText[] frt = texts.toArray (new FaultReasonText[0]);
        return new FaultReason (frt);
    }

    protected void readFaultReasonTexts (XMLStreamReader reader, List<FaultReasonText> texts) {
        QName name = reader.getName ();
        if (!name.equals (SOAP12Constants.QNAME_FAULT_REASON_TEXT)) {
            return;
        }
        String lang = reader.getAttributeValue (SOAP12NamespaceConstants.XML_NS, "lang");
        //lets be more forgiving, if its null lets assume its 'en'
        if(lang == null)
            lang = "en";

        //TODO: what to do when the lang is other than 'en', for example clingon?

        //get the text value
        XMLStreamReaderUtil.nextContent (reader);
        String text = null;
        if (reader.getEventType () == CHARACTERS) {
            text = reader.getText ();
            XMLStreamReaderUtil.next (reader);
        }
        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, SOAP12Constants.QNAME_FAULT_REASON_TEXT);
        XMLStreamReaderUtil.nextElementContent (reader);
        Locale loc = new Locale(lang);

        texts.add (new FaultReasonText (text, loc));

        //call again to see if there are more soapenv:Text elements
        readFaultReasonTexts (reader, texts);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#decodeHeader(com.sun.xml.internal.ws.streaming.XMLStreamReader, com.sun.pept.ept.MessageInfo, com.sun.xml.internal.ws.soap.internal.InternalMessage)
     */
    @Override
    protected void decodeHeader (XMLStreamReader reader, MessageInfo messageInfo, InternalMessage request) {
        XMLStreamReaderUtil.verifyReaderState (reader, START_ELEMENT);
        if (!SOAPNamespaceConstants.TAG_HEADER.equals (reader.getLocalName ())) {
            return;
        }
        XMLStreamReaderUtil.verifyTag (reader, getHeaderTag ());
        XMLStreamReaderUtil.nextElementContent (reader);
        while (true) {
            if (reader.getEventType () == START_ELEMENT) {
                decodeHeaderElement (reader, messageInfo, request);
            } else {
                break;
            }
        }
        XMLStreamReaderUtil.verifyReaderState (reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag (reader, getHeaderTag ());
        XMLStreamReaderUtil.nextElementContent (reader);
    }

    /*
     * If JAXB can deserialize a header, deserialize it.
     * Otherwise, just ignore the header
     */
    protected void decodeHeaderElement (XMLStreamReader reader, MessageInfo messageInfo,
        InternalMessage msg) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext (messageInfo);
        BridgeContext bridgeContext = rtCtxt.getBridgeContext ();
        Set<QName> knownHeaders = ((SOAPRuntimeModel) rtCtxt.getModel ()).getKnownHeaders ();
        QName name = reader.getName ();
        if (knownHeaders != null && knownHeaders.contains (name)) {
            QName headerName = reader.getName ();
            if (msg.isHeaderPresent (name)) {
                // More than one instance of header whose QName is mapped to a
                // method parameter. Generates a runtime error.
                raiseFault (getSenderFaultCode(), "Duplicate Header" + headerName);
            }
            Object decoderInfo = rtCtxt.getDecoderInfo (name);
            if (decoderInfo != null && decoderInfo instanceof JAXBBridgeInfo) {
                JAXBBridgeInfo bridgeInfo = (JAXBBridgeInfo) decoderInfo;
                // JAXB leaves on </env:Header> or <nextHeaderElement>
                bridgeInfo.deserialize(reader,bridgeContext);
                HeaderBlock headerBlock = new HeaderBlock (bridgeInfo);
                msg.addHeader (headerBlock);
            }
        } else {
            XMLStreamReaderUtil.skipElement (reader);                 // Moves to END state
            XMLStreamReaderUtil.nextElementContent (reader);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getFaultTag()
     */
    @Override
    protected QName getFaultTag (){
        return SOAP12Constants.QNAME_SOAP_FAULT;
    }
    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getBodyTag()
     */
    @Override
    protected QName getBodyTag () {
        return SOAP12Constants.QNAME_SOAP_BODY;
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getEnvelopeTag()
     */
    @Override
    protected QName getEnvelopeTag () {
        return SOAP12Constants.QNAME_SOAP_ENVELOPE;
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getHeaderTag()
     */
    @Override
    protected QName getHeaderTag () {
        return SOAP12Constants.QNAME_SOAP_HEADER;
    }

    @Override
    protected QName getMUAttrQName (){
        return SOAP12Constants.QNAME_MUSTUNDERSTAND;
    }

    @Override
    protected QName getRoleAttrQName (){
        return SOAP12Constants.QNAME_ROLE;
    }

    @Override
    protected QName getFaultDetailTag() {
        return SOAP12Constants.QNAME_FAULT_DETAIL;
    }

    @Override
    public String getBindingId() {
        return SOAPBinding.SOAP12HTTP_BINDING;
    }

    @Override
    protected QName getSenderFaultCode() {
        return SOAP12Constants.FAULT_CODE_SERVER;
    }

    @Override
    protected QName getReceiverFaultCode() {
        return SOAP12Constants.FAULT_CODE_CLIENT;
    }

    @Override
    protected QName getVersionMismatchFaultCode() {
        return SOAP12Constants.FAULT_CODE_VERSION_MISMATCH;
    }

}
