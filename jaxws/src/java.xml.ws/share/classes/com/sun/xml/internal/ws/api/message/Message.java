/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.client.dispatch.DispatchImpl;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.StringHeader;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.ws.Dispatch;
import javax.xml.ws.WebServiceException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a SOAP message.
 *
 *
 * <h2>What is a message?</h2>
 * <p>
 * A {@link Message} consists of the following:
 *
 * <ol>
 * <li>
 *    Random-accessible list of headers.
 *    a header is a representation of an element inside
 *    &lt;soap:Header>.
 *    It can be read multiple times,
 *    can be added or removed, but it is not modifiable.
 *    See {@link HeaderList} for more about headers.
 *
 * <li>
 *    The payload of the message, which is a representation
 *    of an element inside &lt;soap:Body>.
 *    the payload is streamed, and therefore it can be
 *    only read once (or can be only written to something once.)
 *    once a payload is used, a message is said to be <b>consumed</b>.
 *    A message {@link #hasPayload() may not have any payload.}
 *
 * <li>
 *    Attachments.
 *    TODO: can attachments be streamed? I suspect so.
 *    does anyone need to read attachment twice?
 *
 * </ol>
 *
 *
 * <h2>How does this abstraction work?</h2>
 * <p>
 * The basic idea behind the {@link Message} is to hide the actual
 * data representation. For example, a {@link Message} might be
 * constructed on top of an {@link InputStream} from the accepted HTTP connection,
 * or it might be constructed on top of a JAXB object as a result
 * of the method invocation through {@link Proxy}. There will be
 * a {@link Message} implementation for each of those cases.
 *
 * <p>
 * This interface provides a lot of methods that access the payload
 * in many different forms, and implementations can implement those
 * methods in the best possible way.
 *
 * <p>
 * A particular attention is paid to make sure that a {@link Message}
 * object can be constructed on a stream that is not fully read yet.
 * We believe this improves the turn-around time on the server side.
 *
 * <p>
 * It is often useful to wrap a {@link Message} into another {@link Message},
 * for example to encrypt the body, or to verify the signature as the body
 * is read.
 *
 * <p>
 * This representation is also used for a REST-ful XML message.
 * In such case we'll construct a {@link Message} with empty
 * attachments and headers, and when serializing all headers
 * and attachments will be ignored.
 *
 *
 *
 * <h2>Message and XOP</h2>
 * <p>
 * XOP is considered as an {@link Codec}, and therefore when you are looking at
 * {@link Message}, you'll never see &lt;xop:Include> or any such elements
 * (instead you'll see the base64 data inlined.) If a consumer of infoset isn't
 * interested in handling XOP by himself, this allows him to work with XOP
 * correctly even without noticing it.
 *
 * <p>
 * For producers and consumers that are interested in accessing the binary data
 * more efficiently, they can use {@link XMLStreamReaderEx} and
 * {@link XMLStreamWriterEx}.
 *
 *
 *
 * <h2>Message lifespan</h2>
 * <p>
 * Often {@link Packet} include information local to a particular
 * invocaion (such as {@code HttpServletRequest}, from this angle, it makes sense
 * to tie a lifespan of a message to one pipeline invocation.
 * <p>
 * On the other hand, if you think about WS-RM, it often needs to hold on to
 * a message longer than a pipeline invocation (you might get an HTTP request,
 * get a message X, get a second HTTP request, get another message Y, and
 * only then you might want to process X.)
 * <p>
 * TODO: what do we do about this?
 *
 *
 * <pre>
 * TODO: can body element have foreign attributes? maybe ID for security?
 *       Yes, when the SOAP body is signed there will be an ID attribute present
 *       But in this case any security based impl may need access
 *       to the concrete representation.
 * TODO: HTTP headers?
 *       Yes. Abstracted as transport-based properties.
 * TODO: who handles SOAP 1.1 and SOAP 1.2 difference?
 *       As separate channel implementations responsible for the creation of the
 *       message?
 * TODO: session?
 * TODO: Do we need to expose SOAPMessage explicitly?
 *       SOAPMessage could be the concrete representation but is it necessary to
 *       transform between different concrete representations?
 *       Perhaps this comes down to how use channels for creation and processing.
 * TODO: Do we need to distinguish better between creation and processing?
 *       Do we really need the requirement that a created message can be resused
 *       for processing. Shall we bifurcate?
 *
 * TODO: SOAP version issue
 *       SOAP version is determined by the context, so message itself doesn't carry it around (?)
 *
 * TODO: wrapping message needs easier. in particular properties and attachments.
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Message {

    // See Packet for doc.
    private boolean isProtocolMessage = false;
    // next two are package protected - should only be used from Packet
    boolean  isProtocolMessage() { return isProtocolMessage; }
    void  setIsProtocolMessage() { isProtocolMessage = true; }

    /**
     * Returns true if headers are present in the message.
     *
     * @return
     *      true if headers are present.
     */
    public abstract boolean hasHeaders();

    /**
     * Gets all the headers of this message.
     *
     * <h3>Implementation Note</h3>
     * <p>
     * {@link Message} implementation is allowed to defer
     * the construction of {@link MessageHeaders} object. So
     * if you only want to check for the existence of any header
     * element, use {@link #hasHeaders()}.
     *
     * @return
     *      always return the same non-null object.
     */
    public abstract @NotNull MessageHeaders getHeaders();

    /**
     * Gets the attachments of this message
     * (attachments live outside a message.)
     */
    public @NotNull AttachmentSet getAttachments() {
        if (attachmentSet == null) {
            attachmentSet = new AttachmentSetImpl();
        }
        return attachmentSet;
    }

    /**
     * Optimization hint for the derived class to check
     * if we may have some attachments.
     */
    protected boolean hasAttachments() {
        return attachmentSet!=null;
    }

    protected AttachmentSet attachmentSet;

    private WSDLBoundOperation operation = null;

    private WSDLOperationMapping wsdlOperationMapping = null;

    private MessageMetadata messageMetadata = null;

    public void setMessageMedadata(MessageMetadata metadata) {
        messageMetadata = metadata;
    }


    /**
     * Returns the operation of which this message is an instance of.
     *
     * <p>
     * This method relies on {@link WSDLBoundPortType#getOperation(String, String)} but
     * it does so in an efficient way.
     *
     * @deprecated  It is not always possible to uniquely identify the WSDL Operation from just the
     * information in the Message. Instead, Use {@link com.sun.xml.internal.ws.api.message.Packet#getWSDLOperation()}
     * to get it correctly.
     *
     * <p>
     * This method works only for a request. A pipe can determine an operation for a request,
     * and then keep it in a local variable to use it with a response, so there should be
     * no need to find out operation from a response (besides, there might not be any response!).
     *
     * @param boundPortType
     *      This represents the port for which this message is used.
     *      Most {@link Pipe}s should get this information when they are created,
     *      since a pippeline always work against a particular type of {@link WSDLPort}.
     *
     * @return
     *      Null if the operation was not found. This is possible, for example when a protocol
     *      message is sent through a pipeline, or when we receive an invalid request on the server,
     *      or when we are on the client and the user appliation sends a random DOM through
     *      {@link Dispatch}, so this error needs to be handled gracefully.
     */
    @Deprecated
    public final @Nullable WSDLBoundOperation getOperation(@NotNull WSDLBoundPortType boundPortType) {
        if (operation == null && messageMetadata != null) {
            if (wsdlOperationMapping == null) wsdlOperationMapping = messageMetadata.getWSDLOperationMapping();
            if (wsdlOperationMapping != null) operation = wsdlOperationMapping.getWSDLBoundOperation();
        }
        if(operation==null)
            operation = boundPortType.getOperation(getPayloadNamespaceURI(),getPayloadLocalPart());
        return operation;
    }

    /**
     * The same as {@link #getOperation(WSDLBoundPortType)} but
     * takes {@link WSDLPort} for convenience.
     *
     * @deprecated  It is not always possible to uniquely identify the WSDL Operation from just the
     * information in the Message. Instead, Use {@link com.sun.xml.internal.ws.api.message.Packet#getWSDLOperation()}
     * to get it correctly.
     */
    @Deprecated
    public final @Nullable WSDLBoundOperation getOperation(@NotNull WSDLPort port) {
        return getOperation(port.getBinding());
    }

    /**
     * Returns the java Method of which this message is an instance of.
     *
     * It is not always possible to uniquely identify the WSDL Operation from just the
     * information in the Message. Instead, Use {@link com.sun.xml.internal.ws.api.message.Packet#getWSDLOperation()}
     * to get the QName of the associated wsdl operation correctly.
     *
     * <p>
     * This method works only for a request. A pipe can determine a {@link Method}
     * for a request, and then keep it in a local variable to use it with a response,
     * so there should be no need to find out operation from a response (besides,
     * there might not be any response!).
     *
     * @param seiModel
     *      This represents the java model for the endpoint
     *      Some server {@link Pipe}s would get this information when they are created.
     *
     * @return
     *      Null if there is no corresponding Method for this message. This is
     *      possible, for example when a protocol message is sent through a
     *      pipeline, or when we receive an invalid request on the server,
     *      or when we are on the client and the user appliation sends a random
     *      DOM through {@link Dispatch}, so this error needs to be handled
     *      gracefully.
     */
    @Deprecated
    public final @Nullable JavaMethod getMethod(@NotNull SEIModel seiModel) {
        if (wsdlOperationMapping == null && messageMetadata != null) {
            wsdlOperationMapping = messageMetadata.getWSDLOperationMapping();
        }
        if (wsdlOperationMapping != null) {
            return wsdlOperationMapping.getJavaMethod();
        }
        //fall back to the original logic which could be incorrect ...
        String localPart = getPayloadLocalPart();
        String nsUri;
        if (localPart == null) {
            localPart = "";
            nsUri = "";
        } else {
            nsUri = getPayloadNamespaceURI();
        }
        QName name = new QName(nsUri, localPart);
        return seiModel.getJavaMethod(name);
    }

    private Boolean isOneWay;

    /**
     * Returns true if this message is a request message for a
     * one way operation according to the given WSDL. False otherwise.
     *
     * <p>
     * This method is functionally equivalent as doing
     * {@code getOperation(port).getOperation().isOneWay()}
     * (with proper null check and all.) But this method
     * can sometimes work faster than that (for example,
     * on the client side when used with SEI.)
     *
     * @param port
     *      {@link Message}s are always created under the context of
     *      one {@link WSDLPort} and they never go outside that context.
     *      Pass in that "governing" {@link WSDLPort} object here.
     *      We chose to receive this as a parameter instead of
     *      keeping {@link WSDLPort} in a message, just to save the storage.
     *
     *      <p>
     *      The implementation of this method involves caching the return
     *      value, so the behavior is undefined if multiple callers provide
     *      different {@link WSDLPort} objects, which is a bug of the caller.
     */
    public boolean isOneWay(@NotNull WSDLPort port) {
        if(isOneWay==null) {
            // we don't know, so compute.
            WSDLBoundOperation op = getOperation(port);
            if(op!=null)
                isOneWay = op.getOperation().isOneWay();
            else
                // the contract is to return true only when it's known to be one way.
                isOneWay = false;
        }
        return isOneWay;
    }

    /**
     * Makes an assertion that this {@link Message} is
     * a request message for an one-way operation according
     * to the context WSDL.
     *
     * <p>
     * This method is really only intended to be invoked from within
     * the JAX-WS runtime, and not by any code building on top of it.
     *
     * <p>
     * This method can be invoked only when the caller "knows" what
     * WSDL says. Also, there's no point in invoking this method if the caller
     * is doing  {@code getOperation(port).getOperation().isOneWay()},
     * or sniffing the payload tag name.
     * In particular, this includes {@link DispatchImpl}.
     *
     * <p>
     * Once called, this allows {@link #isOneWay(WSDLPort)} method
     * to return a value quickly.
     *
     * @see #isOneWay(WSDLPort)
     */
    public final void assertOneWay(boolean value) {
        // if two callers make different assertions, that's a bug.
        // this is an assertion, not a runtime check because
        // nobody outside JAX-WS should be using this.
        assert isOneWay==null || isOneWay==value;

        isOneWay = value;
    }


    /**
     * Gets the local name of the payload element.
     *
     * @return
     *      null if a {@link Message} doesn't have any payload.
     */
    public abstract @Nullable String getPayloadLocalPart();

    /**
     * Gets the namespace URI of the payload element.
     *
     * @return
     *      null if a {@link Message} doesn't have any payload.
     */
    public abstract String getPayloadNamespaceURI();
    // I'm not putting @Nullable on it because doing null check on getPayloadLocalPart() should be suffice

    /**
     * Returns true if a {@link Message} has a payload.
     *
     * <p>
     * A message without a payload is a SOAP message that looks like:
     * <pre>{@code
     * <S:Envelope>
     *   <S:Header>
     *     ...
     *   </S:Header>
     *   <S:Body />
     * </S:Envelope>
     * }</pre>
     */
    public abstract boolean hasPayload();

    /**
     * Returns true if this message is a fault.
     *
     * <p>
     * Just a convenience method built on {@link #getPayloadNamespaceURI()}
     * and {@link #getPayloadLocalPart()}.
     */
    public boolean isFault() {
        // TODO: is SOAP version a property of a Message?
        // or is it defined by external factors?
        // how do I compare?
        String localPart = getPayloadLocalPart();
        if(localPart==null || !localPart.equals("Fault"))
            return false;

        String nsUri = getPayloadNamespaceURI();
        return nsUri.equals(SOAPVersion.SOAP_11.nsUri) || nsUri.equals(SOAPVersion.SOAP_12.nsUri);
    }

    /**
     * It gives S:Envelope/S:Body/S:Fault/detail 's first child's name. Should
     * be called for messages that have SOAP Fault.
     *
     * <p> This implementation is expensive so concrete implementations are
     * expected to override this one.
     *
     * @return first detail entry's name, if there is one
     *         else null
     */
    public @Nullable QName getFirstDetailEntryName() {
        assert isFault();
        Message msg = copy();
        try {
            SOAPFaultBuilder fault = SOAPFaultBuilder.create(msg);
            return fault.getFirstDetailEntryName();
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * Consumes this message including the envelope.
     * returns it as a {@link Source} object.
     */
    public abstract Source readEnvelopeAsSource();


    /**
     * Returns the payload as a {@link Source} object.
     *
     * This consumes the message.
     *
     * @return
     *      if there's no payload, this method returns null.
     */
    public abstract Source readPayloadAsSource();

    /**
     * Creates the equivalent {@link SOAPMessage} from this message.
     *
     * This consumes the message.
     *
     * @throws SOAPException
     *      if there's any error while creating a {@link SOAPMessage}.
     */
    public abstract SOAPMessage readAsSOAPMessage() throws SOAPException;

    /**
     * Creates the equivalent {@link SOAPMessage} from this message. It also uses
     * transport specific headers from Packet during the SOAPMessage construction
     * so that {@link SOAPMessage#getMimeHeaders()} gives meaningful transport
     * headers.
     *
     * This consumes the message.
     *
     * @throws SOAPException
     *      if there's any error while creating a {@link SOAPMessage}.
     */
    public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
        return readAsSOAPMessage();
    }

    public static Map<String, List<String>> getTransportHeaders(Packet packet) {
        return getTransportHeaders(packet, packet.getState().isInbound());
    }

    public static Map<String, List<String>> getTransportHeaders(Packet packet, boolean inbound) {
        Map<String, List<String>> headers = null;
        String key = inbound ? Packet.INBOUND_TRANSPORT_HEADERS : Packet.OUTBOUND_TRANSPORT_HEADERS;
        if (packet.supports(key)) {
            headers = (Map<String, List<String>>)packet.get(key);
        }
        return headers;
    }

    public static void addSOAPMimeHeaders(MimeHeaders mh, Map<String, List<String>> headers) {
        for(Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (!e.getKey().equalsIgnoreCase("Content-Type")) {
                for(String value : e.getValue()) {
                    mh.addHeader(e.getKey(), value);
                }
            }
        }
    }
    /**
     * Reads the payload as a JAXB object by using the given unmarshaller.
     *
     * This consumes the message.
     *
     * @throws JAXBException
     *      If JAXB reports an error during the processing.
     */
    public abstract <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException;

    /**
     * Reads the payload as a JAXB object according to the given {@link Bridge}.
     *
     * This consumes the message.
     *
     * @deprecated
     * @return null
     *      if there's no payload.
     * @throws JAXBException
     *      If JAXB reports an error during the processing.
     */
    public abstract <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException;

    /**
     * Reads the payload as a Data-Bond object
     *
     * This consumes the message.
     *
     * @return null
     *      if there's no payload.
     * @throws JAXBException
     *      If JAXB reports an error during the processing.
     */
    public abstract <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException;

    /**
     * Reads the payload as a {@link XMLStreamReader}
     *
     * This consumes the message. The caller is encouraged to call
     * {@link XMLStreamReaderFactory#recycle(XMLStreamReader)} when finished using
     * the instance.
     *
     * @return
     *      If there's no payload, this method returns null.
     *      Otherwise always non-null valid {@link XMLStreamReader} that points to
     *      the payload tag name.
     */
    public abstract XMLStreamReader readPayload() throws XMLStreamException;

    /**
     * Marks the message as consumed, without actually reading the contents.
     *
     * <p>
     * This method provides an opportunity for implementations to reuse
     * any reusable resources needed for representing the payload.
     *
     * <p>
     * This method may not be called more than once since it may have
     * released the reusable resources.
     */
    public void consume() {}

    /**
     * Writes the payload to StAX.
     *
     * This method writes just the payload of the message to the writer.
     * This consumes the message.
     * The implementation will not write
     * {@link XMLStreamWriter#writeStartDocument()}
     * nor
     * {@link XMLStreamWriter#writeEndDocument()}
     *
     * <p>
     * If there's no payload, this method is no-op.
     *
     * @throws XMLStreamException
     *      If the {@link XMLStreamWriter} reports an error,
     *      or some other errors happen during the processing.
     */
    public abstract void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException;

    /**
     * Writes the whole SOAP message (but not attachments)
     * to the given writer.
     *
     * This consumes the message.
     *
     * @throws XMLStreamException
     *      If the {@link XMLStreamWriter} reports an error,
     *      or some other errors happen during the processing.
     */
    public abstract void writeTo(XMLStreamWriter sw) throws XMLStreamException;

    /**
     * Writes the whole SOAP envelope as SAX events.
     *
     * <p>
     * This consumes the message.
     *
     * @param contentHandler
     *      must not be nulll.
     * @param errorHandler
     *      must not be null.
     *      any error encountered during the SAX event production must be
     *      first reported to this error handler. Fatal errors can be then
     *      thrown as {@link SAXParseException}. {@link SAXException}s thrown
     *      from {@link ErrorHandler} should propagate directly through this method.
     */
    public abstract void writeTo( ContentHandler contentHandler, ErrorHandler errorHandler ) throws SAXException;

    // TODO: do we need a method that reads payload as a fault?
    // do we want a separte streaming representation of fault?
    // or would SOAPFault in SAAJ do?



    /**
     * Creates a copy of a {@link Message}.
     *
     * <p>
     * This method creates a new {@link Message} whose header/payload/attachments/properties
     * are identical to this {@link Message}. Once created, the created {@link Message}
     * and the original {@link Message} behaves independently --- adding header/
     * attachment to one {@link Message} doesn't affect another {@link Message}
     * at all.
     *
     * <p>
     * This method does <b>NOT</b> consume a message.
     *
     * <p>
     * To enable efficient copy operations, there's a few restrictions on
     * how copied message can be used.
     *
     * <ol>
     *  <li>The original and the copy may not be
     *      used concurrently by two threads (this allows two {@link Message}s
     *      to share some internal resources, such as JAXB marshallers.)
     *      Note that it's OK for the original and the copy to be processed
     *      by two threads, as long as they are not concurrent.
     *
     *  <li>The copy has the same 'life scope'
     *      as the original (this allows shallower copy, such as
     *      JAXB beans wrapped in {@link JAXBMessage}.)
     * </ol>
     *
     * <p>
     * A 'life scope' of a message created during a message processing
     * in a pipeline is until a pipeline processes the next message.
     * A message cannot be kept beyond its life scope.
     *
     * (This experimental design is to allow message objects to be reused
     * --- feedback appreciated.)
     *
     *
     *
     * <h3>Design Rationale</h3>
     * <p>
     * Since a {@link Message} body is read-once, sometimes
     * (such as when you do fail-over, or WS-RM) you need to
     * create an idential copy of a {@link Message}.
     *
     * <p>
     * The actual copy operation depends on the layout
     * of the data in memory, hence it's best to be done by
     * the {@link Message} implementation itself.
     *
     * <p>
     * The restrictions placed on the use of copied {@link Message} can be
     * relaxed if necessary, but it will make the copy method more expensive.
     *
     * <h3>IMPORTANT</h3>
     * <p> WHEN YOU IMPLEMENT OR CHANGE A {@link .copy()} METHOD, YOU MUST
     * USE THE {@link copyFrom(Message)} METHOD IN THE IMPLEMENTATION.
     */
    // TODO: update the class javadoc with 'lifescope'
    // and move the discussion about life scope there.
    public abstract Message copy();

    /**
     * The {@link Message#copy()} method is used as a shorthand
     * throughout the codecase in place of calling a copy constructor.
     * However, that shorthand make it difficult to have a concrete
     * method here in the base to do common work.
     *
     * <p> Rather than have each {@code copy} method duplicate code, the
     * following method is used in each {@code copy} implementation.
     * It MUST be called.
     *
     * @return The Message that calls {@code copyFrom} inside the
     * {@code copy} method after the copy constructor
     */
    public final Message copyFrom(Message m) {
        isProtocolMessage = m.isProtocolMessage;
        return this;
    }

    /**
     * Retuns a unique id for the message. The id can be used for various things,
     * like debug assistance, logging, and MIME encoding(say for boundary).
     *
     * <p>
     * This method will check the existence of the addressing <MessageID> header,
     * and if present uses that value. Otherwise it generates one from UUID.random(),
     * and return it without adding a new header. But it doesn't add a <MessageID>
     * to the header list since we expect them to be added before calling this
     * method.
     *
     * <p>
     * Addressing tube will go do a separate verification on inbound
     * headers to make sure that <MessageID> header is present when it's
     * supposed to be.
     *
     * @param binding object created by {@link BindingID#createBinding()}
     *
     * @return unique id for the message
     * @deprecated
     */
    public @NotNull String getID(@NotNull WSBinding binding) {
        return getID(binding.getAddressingVersion(), binding.getSOAPVersion());
    }

    /**
     * Retuns a unique id for the message.
     * <p><p>
     * @see {@link #getID(com.sun.xml.internal.ws.api.WSBinding)} for detailed description.
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @return unique id for the message
     * @deprecated
     */
    public @NotNull String getID(AddressingVersion av, SOAPVersion sv) {
        String uuid = null;
        if (av != null) {
            uuid = AddressingUtils.getMessageID(getHeaders(), av, sv);
        }
        if (uuid == null) {
            uuid = generateMessageID();
            getHeaders().add(new StringHeader(av.messageIDTag, uuid));
        }
        return uuid;
    }

    /**
     * Generates a UUID suitable for use as a MessageID value
     * @return generated UUID
     */
    public static String generateMessageID() {
        return "uuid:" + UUID.randomUUID().toString();
    }

    public SOAPVersion getSOAPVersion() {
        return null;
    }
}
