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

package com.sun.xml.internal.ws.api.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.v2.runtime.MarshallerImpl;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.Codecs;
import com.sun.xml.internal.ws.encoding.StreamSOAPCodec;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.DOMMessage;
import com.sun.xml.internal.ws.message.EmptyMessageImpl;
import com.sun.xml.internal.ws.message.ProblemActionHeader;
import com.sun.xml.internal.ws.message.stream.PayloadStreamReaderMessage;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.message.saaj.SAAJMessage;
import com.sun.xml.internal.ws.message.source.PayloadSourceMessage;
import com.sun.xml.internal.ws.message.source.ProtocolSourceMessage;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.WebServiceException;

/**
 * Factory methods for various {@link Message} implementations.
 *
 * <p>
 * This class provides various methods to create different
 * flavors of {@link Message} classes that store data
 * in different formats.
 *
 * <p>
 * This is a part of the JAX-WS RI internal API so that
 * {@link Tube} implementations can reuse the implementations
 * done inside the JAX-WS.
 *
 * <p>
 * If you find some of the useful convenience methods missing
 * from this class, please talk to us.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Messages {
    private Messages() {}

    /**
     * Creates a {@link Message} backed by a JAXB bean.
     *
     * @param context
     *      The context to be used to produce infoset from the object. Must not be null.
     * @param jaxbObject
     *      The JAXB object that represents the payload. must not be null. This object
     *      must be bound to an element (which means it either is a {@link JAXBElement} or
     *      an instanceof a class with {@link XmlRootElement}).
     * @param soapVersion
     *      The SOAP version of the message. Must not be null.
     */
    public static Message create(JAXBRIContext context, Object jaxbObject, SOAPVersion soapVersion) {
        return JAXBMessage.create(context,jaxbObject,soapVersion);
    }

    /**
     * @deprecated
     *      Use {@link #create(JAXBRIContext, Object, SOAPVersion)}
     */
    public static Message create(Marshaller marshaller, Object jaxbObject, SOAPVersion soapVersion) {
        return create(((MarshallerImpl)marshaller).getContext(),jaxbObject,soapVersion);
    }

    /**
     * Creates a {@link Message} backed by a SAAJ {@link SOAPMessage} object.
     *
     * <p>
     * If the {@link SOAPMessage} contains headers and attachments, this method
     * does the right thing.
     *
     * @param saaj
     *      The SOAP message to be represented as a {@link Message}.
     *      Must not be null. Once this method is invoked, the created
     *      {@link Message} will own the {@link SOAPMessage}, so it shall
     *      never be touched directly.
     */
    public static Message create(SOAPMessage saaj) {
        return new SAAJMessage(saaj);
    }

    /**
     * Creates a {@link Message} using {@link Source} as payload.
     *
     * @param payload
     *      Source payload is {@link Message}'s payload
     *      Must not be null. Once this method is invoked, the created
     *      {@link Message} will own the {@link Source}, so it shall
     *      never be touched directly.
     *
     * @param ver
     *      The SOAP version of the message. Must not be null.
     */
    public static Message createUsingPayload(Source payload, SOAPVersion ver) {
        return new PayloadSourceMessage(payload, ver);
    }

    /**
     * Creates a {@link Message} using {@link XMLStreamReader} as payload.
     *
     * @param payload
     *      XMLStreamReader payload is {@link Message}'s payload
     *      Must not be null. Once this method is invoked, the created
     *      {@link Message} will own the {@link XMLStreamReader}, so it shall
     *      never be touched directly.
     *
     * @param ver
     *      The SOAP version of the message. Must not be null.
     */
    public static Message createUsingPayload(XMLStreamReader payload, SOAPVersion ver) {
        return new PayloadStreamReaderMessage(payload, ver);
    }

    /**
     * Creates a {@link Message} from an {@link Element} that represents
     * a payload.
     *
     * @param payload
     *      The element that becomes the child element of the SOAP body.
     *      Must not be null.
     *
     * @param ver
     *      The SOAP version of the message. Must not be null.
     */
    public static Message createUsingPayload(Element payload, SOAPVersion ver) {
        return new DOMMessage(ver,payload);
    }

    /**
     * Creates a {@link Message} from an {@link Element} that represents
     * the whole SOAP message.
     *
     * @param soapEnvelope
     *      The SOAP envelope element.
     */
    public static Message create(Element soapEnvelope) {
        SOAPVersion ver = SOAPVersion.fromNsUri(soapEnvelope.getNamespaceURI());
        // find the headers
        Element header = DOMUtil.getFirstChild(soapEnvelope, ver.nsUri, "Header");
        HeaderList headers = null;
        if(header!=null) {
            for( Node n=header.getFirstChild(); n!=null; n=n.getNextSibling() ) {
                if(n.getNodeType()==Node.ELEMENT_NODE) {
                    if(headers==null)
                        headers = new HeaderList();
                    headers.add(Headers.create((Element)n));
                }
            }
        }

        // find the payload
        Element body = DOMUtil.getFirstChild(soapEnvelope, ver.nsUri, "Body");
        if(body==null)
            throw new WebServiceException("Message doesn't have <S:Body> "+soapEnvelope);
        Element payload = DOMUtil.getFirstChild(soapEnvelope, ver.nsUri, "Body");

        if(payload==null) {
            return new EmptyMessageImpl(headers, new AttachmentSetImpl(), ver);
        } else {
            return new DOMMessage(ver,headers,payload);
        }
    }

    /**
     * Creates a {@link Message} using Source as entire envelope.
     *
     * @param envelope
     *      Source envelope is used to create {@link Message}
     *      Must not be null. Once this method is invoked, the created
     *      {@link Message} will own the {@link Source}, so it shall
     *      never be touched directly.
     *
     */
    public static Message create(Source envelope, SOAPVersion soapVersion) {
        return new ProtocolSourceMessage(envelope, soapVersion);
    }


    /**
     * Creates a {@link Message} that doesn't have any payload.
     */
    public static Message createEmpty(SOAPVersion soapVersion) {
        return new EmptyMessageImpl(soapVersion);
    }

    /**
     * Creates a {@link Message} from {@link XMLStreamReader} that points to
     * the start of the envelope.
     *
     * @param reader
     *      can point to the start document or the start element (of &lt;s:Envelope>)
     */
    public static @NotNull Message create(@NotNull XMLStreamReader reader) {
        // skip until the root element
        if(reader.getEventType()!=XMLStreamConstants.START_ELEMENT)
            XMLStreamReaderUtil.nextElementContent(reader);
        assert reader.getEventType()== XMLStreamConstants.START_ELEMENT :reader.getEventType();

        SOAPVersion ver = SOAPVersion.fromNsUri(reader.getNamespaceURI());

        return Codecs.createSOAPEnvelopeXmlCodec(ver).decode(reader);
    }

    /**
     * Creates a {@link Message} from {@link XMLStreamBuffer} that retains the
     * whole envelope infoset.
     *
     * @param xsb
     *      This buffer must contain the infoset of the whole envelope.
     */
    public static @NotNull Message create(@NotNull XMLStreamBuffer xsb) {
        // TODO: we should be able to let Messae know that it's working off from a buffer,
        // to make some of the operations more efficient.
        // meanwhile, adding this as an API so that our users can take advantage of it
        // when we get around to such an implementation later.
        try {
            return create(xsb.readAsXMLStreamReader());
        } catch (XMLStreamException e) {
            throw new XMLStreamReaderException(e);
        }
    }

    /**
     * Creates a {@link Message} that represents an exception as a fault. The
     * created message reflects if t or t.getCause() is SOAPFaultException.
     *
     * creates a fault message with default faultCode env:Server if t or t.getCause()
     * is not SOAPFaultException. Otherwise, it use SOAPFaultException's faultCode
     *
     * @return
     *      Always non-null. A message that wraps this {@link Throwable}.
     *
     */
    public static Message create(Throwable t, SOAPVersion soapVersion) {
        return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, t);
    }

    /**
     * Creates a fault {@link Message}.
     *
     * <p>
     * This method is not designed for efficiency, and we don't expect
     * to be used for the performance critical codepath.
     *
     * @param fault
     *      The populated SAAJ data structure that represents a fault
     *      in detail.
     *
     * @return
     *      Always non-null. A message that wraps this {@link SOAPFault}.
     */
    public static Message create(SOAPFault fault) {
        SOAPVersion ver = SOAPVersion.fromNsUri(fault.getNamespaceURI());
        return new DOMMessage(ver,fault);
    }

    /**
     * Creates a fault {@link Message} that captures the code/subcode/subsubcode
     * defined by WS-Addressing if wsa:Action is not supported.
     *
     * @param unsupportedAction The unsupported Action. Must not be null.
     * @param av The WS-Addressing version of the message. Must not be null.
     * @param sv The SOAP Version of the message. Must not be null.
     *
     * @return
     *      A message representing SOAPFault that contains the WS-Addressing code/subcode/subsubcode.
     */
    public static Message create(@NotNull String unsupportedAction, @NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        QName subcode = av.actionNotSupportedTag;
        String faultstring = String.format(av.actionNotSupportedText, unsupportedAction);

        Message faultMessage;
        SOAPFault fault;
        try {
            if (sv == SOAPVersion.SOAP_12) {
                fault = SOAPVersion.SOAP_12.saajSoapFactory.createFault();
                fault.setFaultCode(SOAPConstants.SOAP_SENDER_FAULT);
                fault.appendFaultSubcode(subcode);
                Detail detail = fault.addDetail();
                SOAPElement se = detail.addChildElement(av.problemActionTag);
                se = se.addChildElement(av.actionTag);
                se.addTextNode(unsupportedAction);
            } else {
                fault = SOAPVersion.SOAP_11.saajSoapFactory.createFault();
                fault.setFaultCode(subcode);
            }
            fault.setFaultString(faultstring);

            faultMessage = SOAPFaultBuilder.createSOAPFaultMessage(sv, fault);
            if (sv == SOAPVersion.SOAP_11) {
                faultMessage.getHeaders().add(new ProblemActionHeader(unsupportedAction, av));
            }
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }

        return faultMessage;
    }

    /**
     * To be called to convert a  {@link ProtocolException} and faultcode for a given {@link SOAPVersion} in to a {@link Message}.
     *
     * @param soapVersion {@link SOAPVersion#SOAP_11} or {@link SOAPVersion#SOAP_12}
     * @param pex a ProtocolException
     * @param faultcode soap faultcode. Its ignored if the {@link ProtocolException} instance is {@link javax.xml.ws.soap.SOAPFaultException} and it has a
     * faultcode present in the underlying {@link SOAPFault}.
     * @return {@link Message} representing SOAP fault
     */
    public static @NotNull Message create(@NotNull SOAPVersion soapVersion, @NotNull ProtocolException pex, @Nullable QName faultcode){
        return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, pex, faultcode);
    }
}
