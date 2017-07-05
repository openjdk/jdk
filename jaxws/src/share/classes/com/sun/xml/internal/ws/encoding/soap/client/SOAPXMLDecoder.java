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
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.dispatch.DispatchContext;
import com.sun.xml.internal.ws.client.dispatch.impl.encoding.DispatchSerializer;
import com.sun.xml.internal.ws.client.dispatch.impl.encoding.DispatchUtil;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBeanInfo;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.simpletype.EncoderUtils;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.internal.BodyBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.streaming.SourceReaderFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.streaming.Attributes;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPConnectionUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.xml.StAXSource;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.bind.api.BridgeContext;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import static javax.xml.stream.XMLStreamConstants.*;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * @author WS Development Team
 */

public class SOAPXMLDecoder extends SOAPDecoder {
    public SOAPXMLDecoder() {
    }

    protected DispatchSerializer getSerializerInstance() {
        return DispatchSerializer.SOAP_1_0;
    }

    @Override
    public SOAPMessage toSOAPMessage(MessageInfo messageInfo) {
        WSConnection connection = (WSConnection) messageInfo.getConnection();
        return SOAPConnectionUtil.getSOAPMessage(connection, messageInfo, getBindingId());
    }

    /*protected void decodeBody(XMLStreamReader reader, InternalMessage response,
                              MessageInfo messageInfo) {
        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, getBodyTag());
        int state = XMLStreamReaderUtil.nextElementContent(reader);
        decodeBodyContent(reader, response, messageInfo);
        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, getBodyTag());
        XMLStreamReaderUtil.nextElementContent(reader);
    }
    */


    /*
    * skipBody is true, the body is skipped during parsing.
    */
    protected void decodeEnvelope(XMLStreamReader reader, InternalMessage request,
                                  boolean skipBody, MessageInfo messageInfo) {

        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        //XMLStreamReaderUtil.verifyTag(reader, getEnvelopeTag());
        if (isDispatch(messageInfo))
           dispatchUtil.collectPrefixes(reader);

        XMLStreamReaderUtil.nextElementContent(reader);

        if (skipHeader(messageInfo)) {
            //System.out.println("Skipping header");
            skipHeader(reader, messageInfo);
        } else {
            //System.out.println("Decoding header");
            decodeHeader(reader, messageInfo, request);
        }

        if (skipBody) {
            //System.out.println("Skipping body");
            skipBody(reader);
        } else {
            //System.out.println("Decodong body");
            decodeBody(reader, request, messageInfo);
        }

        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, getEnvelopeTag());
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader, END_DOCUMENT);
    }

    private static String convertNull(String s) {
        return (s != null) ? s : "";
    }

    //@Override
    protected void decodeBody(XMLStreamReader reader, InternalMessage response, MessageInfo messageInfo) {
        DispatchContext context = (DispatchContext) messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);
        if (context != null) {
            //System.out.println("Dispatch context not null");
            XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
            XMLStreamReaderUtil.verifyTag(reader, getBodyTag());
            int state = XMLStreamReaderUtil.nextElementContent(reader);
            // if Body is not empty, then deserialize the Body
            if (state != END_ELEMENT) {
                BodyBlock responseBody;
                QName responseBodyName = reader.getName();   // Operation name

                if (responseBodyName.equals(getFaultTag())) {
                    SOAPFaultInfo soapFaultInfo = decodeFault(reader, response, messageInfo);
                    responseBody = new BodyBlock(soapFaultInfo);
                } else {
                   // System.out.println("Deserializing body");
                    JAXBContext jaxbContext = getJAXBContext(messageInfo);
                    if (jaxbContext == null) {
                        //System.out.println("JAXBContext == null");
                        responseBody = new BodyBlock(getSerializerInstance().deserializeSource(reader, dispatchUtil));
                    } else {
                        //jaxb will leave reader on ending </body> element
                        //System.out.println("Doing a jaxb bean");
                        JAXBBeanInfo jaxBean = null;
                        Unmarshaller um = (Unmarshaller)messageInfo.getMetaData(BindingProviderProperties.DISPATCH_UNMARSHALLER);
                        if (um != null)
                            jaxBean = JAXBBeanInfo.fromStAX(reader, jaxbContext, um);
                        else
                            jaxBean = JAXBBeanInfo.fromStAX(reader, jaxbContext);

                        responseBody = new BodyBlock(jaxBean);
                    }
                }
                response.setBody(responseBody);
            }

            XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
            XMLStreamReaderUtil.verifyTag(reader, getBodyTag());
            XMLStreamReaderUtil.nextElementContent(reader);
        } //else
        else super.decodeBody(reader, response, messageInfo);
    }

    @Override
    //Todo:kw 102505
    public void toMessageInfo(InternalMessage internalMessage, MessageInfo messageInfo) {

        RuntimeContext rtContext =
            (RuntimeContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
        if (rtContext != null) {
            SOAPEPTFactory eptf = (SOAPEPTFactory) messageInfo.getEPTFactory();
            InternalEncoder encoder = eptf.getInternalEncoder();
            encoder.toMessageInfo(internalMessage, messageInfo);

        } else {  //here we need to set attachments in response context
            if (internalMessage != null && internalMessage.getBody() != null) {
                if (internalMessage.getBody().getValue() instanceof SOAPFaultInfo)
                {
                    messageInfo.setResponseType(MessageStruct.CHECKED_EXCEPTION_RESPONSE);
                    messageInfo.setResponse(internalMessage.getBody().getValue());
                } else
                if (internalMessage.getBody().getValue() instanceof Exception) {
                    messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
                    messageInfo.setResponse(internalMessage.getBody().getValue());
                } else {
                    messageInfo.setResponseType(MessageStruct.NORMAL_RESPONSE);
                    setAttachments(internalMessage, messageInfo);
                    //unfortunately we must do this
                    if (internalMessage.getBody().getValue() instanceof JAXBBeanInfo)
                        messageInfo.setResponse(((JAXBBeanInfo) internalMessage.getBody().getValue()).getBean());
                    else
                        messageInfo.setResponse(internalMessage.getBody().getValue());
                }
            } else
                messageInfo.setResponseType(MessageStruct.NORMAL_RESPONSE);
        }
    }

    private void setAttachments(InternalMessage internalMessage, MessageInfo messageInfo) {

    }

    protected void decodeEnvelope(XMLStreamReader reader, MessageInfo messageInfo) {
        InternalMessage im = decodeInternalMessage(reader, messageInfo);
        toMessageInfo(im, messageInfo);
    }

    protected InternalMessage decodeInternalMessage(XMLStreamReader reader, MessageInfo messageInfo) {
        InternalMessage response = new InternalMessage();

        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, getEnvelopeTag());
        XMLStreamReaderUtil.nextElementContent(reader);
        decodeHeader(reader, messageInfo, response);
        decodeBody(reader, response, messageInfo);
        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, getEnvelopeTag());
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader, END_DOCUMENT);

        return response;
    }

    @Override
    public InternalMessage toInternalMessage(SOAPMessage soapMessage, MessageInfo messageInfo) {
        // TODO handle exceptions, attachments
        XMLStreamReader reader = null;
        try {
            InternalMessage response = new InternalMessage();
            processAttachments(messageInfo, response, soapMessage);
            Source source = soapMessage.getSOAPPart().getContent();
            reader = SourceReaderFactory.createSourceReader(source, true, getSOAPMessageCharsetEncoding(soapMessage));
            XMLStreamReaderUtil.nextElementContent(reader);
            decodeEnvelope(reader, response, false, messageInfo);
            return response;
        } catch (DeserializationException e) {
            //e.printStackTrace();
            throw new WebServiceException(e.getCause());
        } catch (Exception e) {
            //e.printStackTrace();
            throw new WebServiceException(e);
        } finally {
            if (reader != null) {
                XMLStreamReaderUtil.close(reader);
            }
        }
    }

    @Override
    public InternalMessage toInternalMessage(SOAPMessage soapMessage,
                                             InternalMessage response, MessageInfo messageInfo) {
        // TODO handle exceptions, attachments
        XMLStreamReader reader = null;
        try {
            processAttachments(messageInfo, response, soapMessage);
            Source source = soapMessage.getSOAPPart().getContent();
            reader = SourceReaderFactory.createSourceReader(source, true, getSOAPMessageCharsetEncoding(soapMessage));
            XMLStreamReaderUtil.nextElementContent(reader);
            decodeEnvelope(reader, response, !isDispatch(messageInfo), messageInfo);

            if (!isDispatch(messageInfo))
                convertBodyBlock(response, messageInfo);

        } catch (DeserializationException e) {
            //e.printStackTrace();
            throw new WebServiceException(e.getCause());
        } catch (Exception e) {
            //e.printStackTrace();
            throw new WebServiceException(e);
        } finally {
            if (reader != null) {
                XMLStreamReaderUtil.close(reader);
            }
        }
        return response;

    }

    /**
     * @return Returns the soap binding - SOAP 1.1 namespace.
     */
    public String getSOAPBindingId() {
        return SOAPConstants.NS_WSDL_SOAP;
    }

    @Override
    protected SOAPFaultInfo decodeFault(XMLStreamReader reader, InternalMessage internalMessage,
                                        MessageInfo messageInfo) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);

        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT);
        Method methodName = messageInfo.getMethod();

        // faultcode
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT_CODE);
        XMLStreamReaderUtil.nextContent(reader);
        QName faultcode;
        String tokens = reader.getText();
        String uri = "";
        tokens = EncoderUtils.collapseWhitespace(tokens);
        String prefix = XmlUtil.getPrefix(tokens);
        if (prefix != null) {
            uri = reader.getNamespaceURI(prefix);
            if (uri == null) {
                throw new DeserializationException("xsd.unknownPrefix", prefix);
            }
        }
        String localPart = XmlUtil.getLocalPart(tokens);
        faultcode = new QName(uri, localPart);
        XMLStreamReaderUtil.next(reader);
        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT_CODE);

        // faultstring
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT_STRING);
        XMLStreamReaderUtil.nextContent(reader);
        String faultstring = "";
        //reader may give more than one char events so coalesc them all.
        if (reader.getEventType() == CHARACTERS) {
            do  {
               faultstring += reader.getText();
               XMLStreamReaderUtil.next(reader);
            }while(reader.getEventType() == CHARACTERS);
        }
        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT_STRING);

        String faultactor = null;
        Object faultdetail = null;
        QName faultName;
        if (XMLStreamReaderUtil.nextElementContent(reader) == START_ELEMENT) {
            QName elementName = reader.getName();
            // faultactor
            if (elementName.equals(SOAPConstants.QNAME_SOAP_FAULT_ACTOR)) {
                XMLStreamReaderUtil.nextContent(reader);
                // faultactor may be empty
                if (reader.getEventType() == CHARACTERS) {
                    faultactor = reader.getText();
                    XMLStreamReaderUtil.next(reader);
                }
                XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
                XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT_ACTOR);
                XMLStreamReaderUtil.nextElementContent(reader);
                elementName = reader.getName();
            }

            // faultdetail
            if (elementName.equals(SOAPConstants.QNAME_SOAP_FAULT_DETAIL)) {
                //skip <detail/>
                if(XMLStreamReaderUtil.nextContent(reader) == START_ELEMENT){
                    if (messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT) == null)
                        faultdetail = readFaultDetail(reader, messageInfo);
                    else {
                        XMLStreamReaderUtil.skipElement(reader);
                        XMLStreamReaderUtil.next(reader);
                    }
                }
                // move from </detail> to </Fault>.
                XMLStreamReaderUtil.nextContent(reader);
            } else {
                if (internalMessage.getHeaders() != null) {
                    boolean isHeaderFault = false;
                    // could be a header fault or a protocol exception with no detail
                    for (HeaderBlock headerBlock : internalMessage.getHeaders())
                    {
                        if (rtCtxt.getModel().isKnownFault(headerBlock.getName(), methodName))
                        {
                            isHeaderFault = true;
                            faultdetail = headerBlock.getValue();
                        }
                    }

                    // if not a header fault, then it is a protocol exception with no detail
                    if (!isHeaderFault) {
                        faultdetail = null;
                    }
                    XMLStreamReaderUtil.next(reader);
                }
            }
        } else {
            // a header fault (with no faultactor)
            if (internalMessage.getHeaders() != null) {
                for (HeaderBlock headerBlock : internalMessage.getHeaders()) {
                    if (rtCtxt.getModel().isKnownFault(headerBlock.getName(), methodName))
                    {
                        faultdetail = headerBlock.getValue();
                    }
                }
            }
        }


        SOAPFaultInfo soapFaultInfo = new SOAPFaultInfo(faultstring, faultcode, faultactor, faultdetail, getBindingId());

        // reader could be left on CHARS token rather than </fault>
        if (reader.getEventType() == CHARACTERS && reader.isWhiteSpace()) {
            XMLStreamReaderUtil.nextContent(reader);
        }

        XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
        XMLStreamReaderUtil.verifyTag(reader, SOAPConstants.QNAME_SOAP_FAULT);
        XMLStreamReaderUtil.nextElementContent(reader);

        return soapFaultInfo;
    }

    /**
     * Decodes the fault &lt;detail> into a {@link Detail} object or a JAXB object.
     * <p/>
     * Upon entry the cursor must be at the start tag of the first child element of &lt;detail>.
     * Upon a successful completion, the cursor is left at &lt;/detail>.
     */
    protected final Object readFaultDetail(XMLStreamReader reader, MessageInfo mi) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(mi);
        if (rtCtxt == null){
            XMLStreamReaderUtil.skipElement(reader);
            XMLStreamReaderUtil.nextElementContent(reader);
            return null;
        }
        QName faultName = reader.getName();
        if (rtCtxt.getModel().isKnownFault(faultName, mi.getMethod())) {
            Object decoderInfo = rtCtxt.getDecoderInfo(faultName);
            if (decoderInfo != null && decoderInfo instanceof JAXBBridgeInfo) {
                JAXBBridgeInfo bridgeInfo = (JAXBBridgeInfo) decoderInfo;
                // JAXB leaves on </env:Header> or <nextHeaderElement>
                bridgeInfo.deserialize(reader, rtCtxt.getBridgeContext());

                // skip other detail entries
                int event = reader.getEventType();
                if (!(event == START_ELEMENT || event == END_ELEMENT)) {
                    XMLStreamReaderUtil.nextElementContent(reader);
                }
                while(reader.getEventType() == START_ELEMENT) {
                    XMLStreamReaderUtil.skipElement(reader);
                    XMLStreamReaderUtil.nextElementContent(reader);
                }

                XMLStreamReaderUtil.verifyReaderState(reader, END_ELEMENT);
                XMLStreamReaderUtil.verifyTag(reader, getFaultDetailTag());
                return bridgeInfo;
            }
        }

        return decodeFaultDetail(reader);
    }

    /**
     * Decodes the fault &lt;detail> into a {@link Detail} object.
     * <p/>
     * Upon entry the cursor must be at the start tag of the first child element of &lt;detail>.
     * Upon a successful completion, the cursor is left at &lt;/detail>.
     */
    protected Detail decodeFaultDetail(XMLStreamReader reader) {
        try {
            // parse the current element that the reader is pointing to
            Transformer trans = XmlUtil.newTransformer();

            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPMessage soapMessage = messageFactory.createMessage();
            Detail detail = soapMessage.getSOAPBody().addFault().addDetail();

            // repeatedly copy all the child elements of <detail>.
            do {
                trans.transform(new StAXSource(reader, true), new DOMResult(detail));
            } while (XMLStreamReaderUtil.nextContent(reader) == START_ELEMENT);

            // now we should be at </detail>.
            return detail;
        } catch (SOAPException e) {
            throw new WebServiceException("sender.response.cannotDecodeFaultDetail", e);
        } catch (TransformerException e) {
            throw new WebServiceException("sender.response.cannotDecodeFaultDetail", e);
        } catch (TransformerFactoryConfigurationError e) {
            throw new WebServiceException("sender.response.cannotDecodeFaultDetail", e);
        }
    }

   /* protected static String getCurrentPrefix(XMLStreamReader reader, String tokens, String namespaceURI) {
        tokens = reader.getText();
        String uri = "";
        tokens = EncoderUtils.collapseWhitespace(tokens);
        String prefix = XmlUtil.getPrefix(tokens);
        if (prefix != null) {
            uri = reader.getNamespaceURI(prefix);
            if (uri == null) {
                throw new DeserializationException("xsd.unknownPrefix", prefix);
            }
        }
        String localPart = XmlUtil.getLocalPart(tokens);
        return prefix;
    }
    */

    protected JAXBContext getJAXBContext(MessageInfo messageInfo) {
        JAXBContext jc = null;

        RequestContext requestContext = (RequestContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        if (requestContext != null)
            // TODO: does this really need to be copied?
            jc = (JAXBContext) requestContext.copy().get(BindingProviderProperties.JAXB_CONTEXT_PROPERTY);
        return jc;
    }

    protected String getBindingId(MessageInfo messageInfo) {
        RequestContext requestContext = (RequestContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        if (requestContext != null) {
            String bindingId = (String) requestContext.get(BindingProviderProperties.BINDING_ID_PROPERTY);
            if (bindingId != null)
                return bindingId;
        }
        return getBindingId();
    }


    @Override
    public String getBindingId() {
        return SOAPBinding.SOAP11HTTP_BINDING;
    }

    @Override
    protected QName getSenderFaultCode() {
        return SOAPConstants.FAULT_CODE_SERVER;
    }

    @Override
    protected QName getReceiverFaultCode() {
        return SOAPConstants.FAULT_CODE_CLIENT;
    }

    @Override
    protected QName getVersionMismatchFaultCode() {
        return SOAPConstants.FAULT_CODE_VERSION_MISMATCH;
    }

}
