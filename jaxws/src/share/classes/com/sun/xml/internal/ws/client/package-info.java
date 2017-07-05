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

/**
 *  <h1>JAX-WS 2.0 Client Runtime</h1>
 * <P>This document describes the architecture of client side
 * JAX-WS 2.0 runtime.
 *
 * <h3>JAX-WS 2.0 Server Client Sequence Diagram</h3>
 * {@SequenceDiagram
 *    pobject(U,"user");
 *   object(A,"EndpointIFInvocationHandler");
 *   object(B,"DelegateBase");
 *   object(C,"MessageDispatcher");
 *   object(D,"Encoder/Decoder");
 *   object(F,"WSConnection");
 *   step();
 *
 *   message(U,A,"invoke Web Service");
 *   active(A);
 *   message(A,A,"invoke");
 *   active(A);
 *   step();
 *   inactive(A);
 *#    step();
 *
 *   message(A,A,"implementSEIMethod");
 *   active(A);
 *   step();
 *   inactive(A);
 *
 *   message(A,B,"send");
 *   active(B);
 *
 *   message(B,B,"getContactInfo");
 *   active(B);
 *   step();
 *   inactive(B);
 *
 *   message(B,B,"getMessageDispatcher");
 *   active(B);
 *   step();
 *  inactive(B);
 *
 *   message(B,C,"send");
 *   active(C);
 *#    step();
 *#    inactive(B);
 *
 *#    message(C,C,"doSend");
 *#    active(C);
 *#    step();
 *#    inactive(C);
 *
 *#    active(C);
 *   message(C,D,"toInternalMessage");
 *   active(D);
 *   step();
 *   inactive(D);
 *
 *   message(C,D,"toSOAPMessage");
 *   active(D);
 *   step();
 *   inactive(D);
 *
 *   message(C,C,"invokeHandlers");
 *   active(C);
 *   step();
 *   inactive(C);
 *
 *   message(C,F,"setHeaders");
 *   active(F);
 *   step();
 *   inactive(F);
 *
 *   message(C,F,"getOutput");
 *   active(F);
 *   step();
 *   inactive(F);
 *
 *   message(C,F,"writeTo");
 *   active(F);
 *   step();
 *   inactive(F);
 *
 *#    message(C,C,"receive");
 *#    active(C);
 *#    step();
 *#    inactive(C);
 *
 *   message(C,F,"readSOAPMessage");
 *   active(F);
 *   step();
 *   inactive(F);
 *
 *   message(C,C,"mustUnderstand");
 *   active(C);
 *   step();
 *   inactive(C);
 *
 *  message(C,C,"invokeHandlers");
 *  active(C);
 *  step();
 *  inactive(C);
 *
 *  message(C,D,"toInternalMessage");
 *  active(D);
 *  step();
 *  inactive(D);
 *
 *#    message(C,C,"doSendAsync");
 *#    active(C);
 *#    step();
 *#    inactive(C);
 *
 *#    message(C,C,"sendAsyncReceive");
 *#    active(C);
 *#    step();
 *#    inactive(C);
 *
 *   rmessage(C,B,"response");
 *   inactive(C);
 *
 *   rmessage(B,A,"response");
 *   inactive(B);
 *
 *   rmessage(A,U,"response");
 *   complete(A);
 * }
 *
 * <H3>Message Flow</H3>
 * {@link com.sun.xml.internal.ws.client.WebService} provides client view of a Web service.
 * WebService.getPort returns an instance of {@link com.sun.xml.internal.ws.client.EndpointIFInvocationHandler}
 * with {@link com.sun.pept.ept.ContactInfoList} and {@link com.sun.pept.Delegate}
 * initialized. A method invocation on the port, obtained from WebService, invokes
 * {@link com.sun.xml.internal.ws.client.EndpointIFInvocationHandler#invoke}. This method
 * then creates a {@link com.sun.pept.ept.MessageInfo} and populates the data
 * (parameters specified by the user) and metadata such as RuntimeContext, RequestContext,
 * Message Exchange Pattern into this MessageInfo. This method then invokes
 * {@link com.sun.pept.Delegate#send} and returns the response.
 * <P></P>
 * The Delegate.send method iterates through the ContactInfoList and picks up the
 * correct {@link com.sun.pept.ept.ContactInfo} based upon the binding id of
 * {@link javax.xml.ws.BindingProvider} and sets it on the MessageInfo. After the
 * Delegate obtains a specific ContactInfo it uses that ContactInfo to obtain a
 * protocol-specific {@link com.sun.pept.protocol.MessageDispatcher}. There will be
 * two types of client-side MessageDispatchers for JAX-WS 2.0 FCS,
 * {@link com.sun.xml.internal.ws.protocol.soap.client.SOAPMessageDispatcher} and
 * {@link com.sun.xml.internal.ws.protocol.xml.client.XMLMessageDispatcher}. The Delegate
 * then invokes {@link com.sun.pept.protocol.MessageDispatcher#send}. The
 * MessageDispatcher.send method makes a decision about the synchronous and
 * asynchronous nature of the message exchange pattern and invokes separate methods
 * accordingly.
 * <p></P>
 * The MessageDispatcher uses ContactInfo to obtain
 * a {@link com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLEncoder} which converts
 * the MessageInfo to {@link com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage}.
 * There will be two types of client-side SOAPXMLEncoder for JAX-WS 2.0 FCS,
 * SOAPXMEncoder for SOAP 1.1 and {@link com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLEncoder}
 * for SOAP 1.2. The MessageDispatcher invokes configured handlers and use the
 * encoder to convert the InternalMessage to a {@link javax.xml.soap.SOAPMessage}.
 * The metadata from the MessageInfo is classified into {@link javax.xml.soap.MimeHeaders}
 * of this SOAPMessage and context information for {@link com.sun.xml.internal.ws.spi.runtime.WSConnection}.
 * The SOAPMessge is then written to the output stream of the WSConnection
 * obtained from MessageInfo.
 *<P></P>
 * The MessageDispatcher.receive method handles the response. The
 * SOAPMessageDispatcher extracts the SOAPMessage from the input stream of
 * WSConnection and performs the mustUnderstand processing followed by invocation
 * of any handlers. The MessageDispatcher uses ContactInfo to obtain a
 * {@link com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLDecoder} which converts the SOAPMessage
 * to InternalMessage and then InternalMessage to MessageInfo. There will be two types of
 * client-side SOAPXMLDecoder for JAX-WS 2.0 FCS, SOAPXMLDencoder for SOAP 1.1 and
 * {@link com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLDecoder} for SOAP 1.2. The
 * response is returned back to the client code via Delegate.
 *
 * <H3>External Interactions</H3>
 * <H4>SAAJ API</H4>
 * <UL>
 *      <LI><P>JAX-WS creates SAAJ SOAPMessage from the HttpServletRequest.
 *      At present, JAX-WS reads all the bytes from the request stream and
 *      then creates SOAPMessage along with the HTTP headers.</P>
 * </UL>
 * <P>MessageFactory(binding).createMessage(MimeHeaders, InputStream)</P>
 * <UL>
 *      <LI><P>SOAPMessage parses the content from the stream including MIME
 *      data</P>
 *      <LI><P>com.sun.xml.internal.ws.server.SOAPMessageDispatcher::checkHeadersPeekBody()</P>
 *      <P>SOAPMessage.getSOAPHeader() is used for mustUnderstand processing
 *      of headers. It further uses
 *      SOAPHeader.examineMustUnderstandHeaderElements(role)</P>
 *      <P>SOAPMessage.getSOAPBody().getFistChild() is used for guessing the
 *      MEP of the request</P>
 *      <LI><P>com.sun.xml.internal.ws.handler.HandlerChainCaller:insertFaultMessage()</P>
 *      <P>SOAPMessage.getSOAPPart().getEnvelope() and some other SAAJ calls
 *      are made to create a fault in the SOAPMessage</P>
 *      <LI><P>com.sun.xml.internal.ws.handler.LogicalMessageImpl::getPayload()
 *      interacts with SAAJ to get body from SOAPMessage</P>
 *      <LI><P>com.sun.xml.internal.ws.encoding.soap.SOAPEncoder.toSOAPMessage(com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage,
 *      SOAPMessage). There is a scenario where there is SOAPMessage and a
 *      logical handler sets payload as Source. To write to the stream,
 *      SOAPMessage.writeTo() is used but before that the body needs to be
 *      updated with logical handler' Source. Need to verify if this
 *      scenario is still happening since Handler.close() is changed to take
 *      MessageContext.</P>
 *      <LI><P>com.sun.xml.internal.ws.handlerSOAPMessageContextImpl.getHeaders()
 *      uses SAAJ API to get headers.</P>
 *      <LI><P>SOAPMessage.writeTo() is used to write response. At present,
 *      it writes into byte[] and this byte[] is written to
 *      HttpServletResponse.</P>
 * </UL>
 * <H4>JAXB API</H4>
 * <P>JAX-WS RI uses the JAXB API to marshall/unmarshall user created
 * JAXB objects with user created {@link javax.xml.bind.JAXBContext JAXBContext}.
 * Handler, Dispatch in JAX-WS API provide ways for the user to specify his/her own
 * JAXBContext. {@link com.sun.xml.internal.ws.encoding.jaxb.JAXBTypeSerializer JAXBTypeSerializer} class uses all these methods.</P>
 * <UL>
 *      <LI><p>{@link javax.xml.bind.Marshaller#marshal(Object,XMLStreamWriter) Marshaller.marshal(Object,XMLStreamWriter)}</p>
 *      <LI><P>{@link javax.xml.bind.Marshaller#marshal(Object,Result) Marshaller.marshal(Object, DomResult)}</P>
 *      <LI><P>{@link javax.xml.bind.Unmarshaller#unmarshal(XMLStreamReader) Object Unmarshaller.unmarshal(XMLStreamReader)}</P>
 *      <LI><P>{@link javax.xml.bind.Unmarshaller#unmarshal(Source) Object Unmarshaller.unmarshal(Source)}</P>
 * </UL>
 * The following two JAXB classes are implemented by JAX-WS to enable/implement MTOM and XOP
 * <UL>
 *      <LI><P>{@link javax.xml.bind.attachment.AttachmentMarshaller AttachmentMarshaller}</P>
 *      <LI><P>{@link javax.xml.bind.attachment.AttachmentUnmarshaller AttachmentUnmarshaller}</P>
 * </UL>
 * <H4>JAXB Runtime-API (private contract)</H4>
 * <P>JAX-WS RI uses these private API for serialization/deserialization
 * purposes. This private API is used to serialize/deserialize method
 * parameters at the time of JAXBTypeSerializer class uses all
 * these methods.</P>
 * <UL>
 *      <LI><P>{@link com.sun.xml.internal.bind.api.Bridge#marshal(BridgeContext, Object, XMLStreamWriter) Bridge.marshal(BridgeContext, Object, XMLStreamWriter)}</P>
 *      <LI><P>{@link com.sun.xml.internal.bind.api.Bridge#marshal(BridgeContext, Object, Node) Bridge.marshal(BridgeContext, Object, Node)}</P>
 *      <LI><P>{@link com.sun.xml.internal.bind.api.Bridge#unmarshal(BridgeContext, XMLStreamReader) Object Bridge.unmarshal(BridgeContext, XMLStreamReader)}</P>
 * </UL>
 *
 * @ArchitectureDocument
 **/
package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.bind.api.BridgeContext;

import javax.xml.ws.Binding;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Node;
