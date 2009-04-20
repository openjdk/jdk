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

package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.LogicalMessage;
import javax.xml.ws.WebServiceException;

/**
 * Implementation of {@link LogicalMessage}. This class implements the methods
 * used by LogicalHandlers to get/set the request or response either
 * as a JAXB object or as javax.xml.transform.Source.
 *
 * <p>The {@link Message} that is passed into the constructor
 * is used to retrieve the payload of the request or response.
 *
 * @see Message
 * @see LogicalMessageContextImpl
 *
 * @author WS Development Team
 */
/**
* TODO: Take care of variations in behavior wrt to vaious sources.
* DOMSource : changes made should be reflected, StreamSource or SAXSource, Give copy
*/
class LogicalMessageImpl implements LogicalMessage {
    private Packet packet;
    // This holds the (modified)payload set by User
    private Source payloadSrc = null;
    // Flag to check if the PayloadSrc is accessed/modified
    private boolean payloadModifed = false;

    /** Creates a new instance of LogicalMessageImplRearch */
    public LogicalMessageImpl(Packet packet) {
        // don't create extract payload until Users wants it.
        this.packet = packet;
    }

    boolean isPayloadModifed(){
        return payloadModifed;
    }
    Source getModifiedPayload(){
        if(!payloadModifed)
            throw new RuntimeException("Payload not modified.");
        return payloadSrc;

    }
    public Source getPayload() {
        if(!payloadModifed) {
            payloadSrc = packet.getMessage().readPayloadAsSource();
            payloadModifed = true;
        }
        if (payloadSrc == null)
            return null;
        if(payloadSrc instanceof DOMSource){
            return payloadSrc;
        } else {
            try {
            Transformer transformer = XmlUtil.newTransformer();
            DOMResult domResult = new DOMResult();
            transformer.transform(payloadSrc, domResult);
            payloadSrc = new DOMSource(domResult.getNode());
            return payloadSrc;
            } catch(TransformerException te) {
                throw new WebServiceException(te);
            }
        }
        /*
        Source copySrc;
        if(payloadSrc instanceof DOMSource){
            copySrc = payloadSrc;
        } else {
            copySrc = copy(payloadSrc);
        }
        return copySrc;
         */
    }

    public void setPayload(Source payload) {
        payloadModifed = true;
        payloadSrc = payload;
    }
    /*
     * Converts to DOMSource and then it unmarshalls this  DOMSource
     * to a jaxb object. Any changes done in jaxb object are lost if
     * the object isn't set again.
     */
    public Object getPayload(JAXBContext context) {
        try {
            Source payloadSrc = getPayload();
            if(payloadSrc == null)
                return null;
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return unmarshaller.unmarshal(payloadSrc);
        } catch (JAXBException e){
            throw new WebServiceException(e);
        }
    }

    public void setPayload(Object payload, JAXBContext context) {
        payloadModifed = true;
        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.fragment", true);
            DOMResult domResult = new DOMResult();
            marshaller.marshal(payload, domResult);
            payloadSrc = new DOMSource(domResult.getNode());
        } catch(JAXBException e) {
            throw new WebServiceException(e);
        }
    }
    /*
    private Source copy(Source src) {
        if(src instanceof StreamSource){
            StreamSource origSrc = (StreamSource)src;
            byte[] payloadbytes;
            try {
                payloadbytes = ASCIIUtility.getBytes(origSrc.getInputStream());
            } catch (IOException e) {
                throw new WebServiceException(e);
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(payloadbytes);
            origSrc.setInputStream(new ByteArrayInputStream(payloadbytes));
            StreamSource copySource = new StreamSource(bis, src.getSystemId());
            return copySource;
        } else if(src instanceof SAXSource){
            SAXSource saxSrc = (SAXSource)src;
            try {
                XMLStreamBuffer xsb = new XMLStreamBuffer();
                XMLReader reader = saxSrc.getXMLReader();
                if(reader == null)
                    reader = new SAXBufferProcessor();
                saxSrc.setXMLReader(reader);
                reader.setContentHandler(new SAXBufferCreator(xsb));
                reader.parse(saxSrc.getInputSource());
                src = new XMLStreamBufferSource(xsb);
                return new XMLStreamBufferSource(xsb);
            } catch (IOException e) {
                throw new WebServiceException(e);
            } catch (SAXException e) {
                throw new WebServiceException(e);
            }
        }
        throw new WebServiceException("Copy is not needed for this Source");
    }
     */
}
