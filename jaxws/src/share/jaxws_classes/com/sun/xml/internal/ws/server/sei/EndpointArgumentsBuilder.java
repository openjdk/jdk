/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.server.sei;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.spi.db.RepeatedElementBridge;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.spi.db.DatabindingException;
import com.sun.xml.internal.ws.spi.db.PropertyAccessor;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.encoding.StringDataContentHandler;
import com.sun.xml.internal.ws.encoding.DataHandlerDataSource;

import javax.activation.DataHandler;
import javax.imageio.ImageIO;
import javax.jws.WebParam.Mode;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads a request {@link Message}, disassembles it, and moves obtained Java values
 * to the expected places.
 *
 * @author Jitendra Kotamraju
 */
public abstract class EndpointArgumentsBuilder {
    /**
     * Reads a request {@link Message}, disassembles it, and moves obtained
     * Java values to the expected places.
     *
     * @param request
     *      The request {@link Message} to be de-composed.
     * @param args
     *      The Java arguments given to the SEI method invocation.
     *      Some parts of the reply message may be set to {@link Holder}s in the arguments.
     * @throws JAXBException
     *      if there's an error during unmarshalling the request message.
     * @throws XMLStreamException
     *      if there's an error during unmarshalling the request message.
     */
    public abstract void readRequest(Message request, Object[] args)
        throws JAXBException, XMLStreamException;

    static final class None extends EndpointArgumentsBuilder {
        private None(){
        }
        @Override
        public void readRequest(Message msg, Object[] args) {
            msg.consume();
        }
    }

    /**
     * The singleton instance that produces null return value.
     * Used for operations that doesn't have any output.
     */
    public final static EndpointArgumentsBuilder NONE = new None();

    /**
     * Returns the 'uninitialized' value for the given type.
     *
     * <p>
     * For primitive types, it's '0', and for reference types, it's null.
     */
    @SuppressWarnings("element-type-mismatch")
    public static Object getVMUninitializedValue(Type type) {
        // if this map returns null, that means the 'type' is a reference type,
        // in which case 'null' is the correct null value, so this code is correct.
        return primitiveUninitializedValues.get(type);
    }

    private static final Map<Class,Object> primitiveUninitializedValues = new HashMap<Class, Object>();

    static {
        Map<Class, Object> m = primitiveUninitializedValues;
        m.put(int.class,(int)0);
        m.put(char.class,(char)0);
        m.put(byte.class,(byte)0);
        m.put(short.class,(short)0);
        m.put(long.class,(long)0);
        m.put(float.class,(float)0);
        m.put(double.class,(double)0);
    }

    protected QName wrapperName;

    static final class WrappedPartBuilder {
        private final XMLBridge bridge;
        private final EndpointValueSetter setter;

        /**
         * @param bridge
         *      specifies how the part is unmarshalled.
         * @param setter
         *      specifies how the obtained value is returned to the endpoint.
         */
        public WrappedPartBuilder(XMLBridge bridge, EndpointValueSetter setter) {
            this.bridge = bridge;
            this.setter = setter;
        }

        void readRequest( Object[] args, XMLStreamReader r, AttachmentSet att) throws JAXBException {
            Object obj = null;
            AttachmentUnmarshallerImpl au = (att != null)?new AttachmentUnmarshallerImpl(att):null;
            if (bridge instanceof RepeatedElementBridge) {
                RepeatedElementBridge rbridge = (RepeatedElementBridge)bridge;
                ArrayList list = new ArrayList();
                QName name = r.getName();
                while (r.getEventType()==XMLStreamReader.START_ELEMENT && name.equals(r.getName())) {
                    list.add(rbridge.unmarshal(r, au));
                    XMLStreamReaderUtil.toNextTag(r, name);
                }
                obj = rbridge.collectionHandler().convert(list);
            } else {
                obj = bridge.unmarshal(r, au);
            }
            setter.put(obj,args);
        }
    }

    protected Map<QName,WrappedPartBuilder> wrappedParts = null;

    protected void readWrappedRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
        if (!msg.hasPayload()) {
            throw new WebServiceException("No payload. Expecting payload with "+wrapperName+" element");
        }
        XMLStreamReader reader = msg.readPayload();
        XMLStreamReaderUtil.verifyTag(reader,wrapperName);
        reader.nextTag();
        while(reader.getEventType()==XMLStreamReader.START_ELEMENT) {
            // TODO: QName has a performance issue
            QName name = reader.getName();
            WrappedPartBuilder part = wrappedParts.get(name);
            if(part==null) {
                // no corresponding part found. ignore
                XMLStreamReaderUtil.skipElement(reader);
                reader.nextTag();
            } else {
                part.readRequest(args,reader, msg.getAttachments());
            }
            XMLStreamReaderUtil.toNextTag(reader, name);
        }

        // we are done with the body
        reader.close();
        XMLStreamReaderFactory.recycle(reader);
    }

    /**
     * {@link EndpointArgumentsBuilder} that sets the VM uninitialized value to the type.
     */
    public static final class NullSetter extends EndpointArgumentsBuilder {
        private final EndpointValueSetter setter;
        private final Object nullValue;

        public NullSetter(EndpointValueSetter setter, Object nullValue){
            assert setter!=null;
            this.nullValue = nullValue;
            this.setter = setter;
        }
        public void readRequest(Message msg, Object[] args) {
            setter.put(nullValue, args);
        }
    }

    /**
     * {@link EndpointArgumentsBuilder} that is a composition of multiple
     * {@link EndpointArgumentsBuilder}s.
     *
     * <p>
     * Sometimes we need to look at multiple parts of the reply message
     * (say, two header params, one body param, and three attachments, etc.)
     * and that's when this object is used to combine multiple {@link EndpointArgumentsBuilder}s
     * (that each responsible for handling one part).
     *
     * <p>
     * The model guarantees that only at most one {@link EndpointArgumentsBuilder} will
     * return a value as a return value (and everything else has to go to
     * {@link Holder}s.)
     */
    public static final class Composite extends EndpointArgumentsBuilder {
        private final EndpointArgumentsBuilder[] builders;

        public Composite(EndpointArgumentsBuilder... builders) {
            this.builders = builders;
        }

        public Composite(Collection<? extends EndpointArgumentsBuilder> builders) {
            this(builders.toArray(new EndpointArgumentsBuilder[builders.size()]));
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            for (EndpointArgumentsBuilder builder : builders) {
                builder.readRequest(msg,args);
            }
        }
    }


    /**
     * Reads an Attachment into a Java parameter.
     */
    public static abstract class AttachmentBuilder extends EndpointArgumentsBuilder {
        protected final EndpointValueSetter setter;
        protected final ParameterImpl param;
        protected final String pname;
        protected final String pname1;

        AttachmentBuilder(ParameterImpl param, EndpointValueSetter setter) {
            this.setter = setter;
            this.param = param;
            this.pname = param.getPartName();
            this.pname1 = "<"+pname;
        }

        /**
         * Creates an AttachmentBuilder based on the parameter type
         *
         * @param param
         *      runtime Parameter that abstracts the annotated java parameter
         * @param setter
         *      specifies how the obtained value is set into the argument. Takes
         *      care of Holder arguments.
         */
        public static EndpointArgumentsBuilder createAttachmentBuilder(ParameterImpl param, EndpointValueSetter setter) {
            Class type = (Class)param.getTypeInfo().type;
            if (DataHandler.class.isAssignableFrom(type)) {
                return new DataHandlerBuilder(param, setter);
            } else if (byte[].class==type) {
                return new ByteArrayBuilder(param, setter);
            } else if(Source.class.isAssignableFrom(type)) {
                return new SourceBuilder(param, setter);
            } else if(Image.class.isAssignableFrom(type)) {
                return new ImageBuilder(param, setter);
            } else if(InputStream.class==type) {
                return new InputStreamBuilder(param, setter);
            } else if(isXMLMimeType(param.getBinding().getMimeType())) {
                return new JAXBBuilder(param, setter);
            } else if(String.class.isAssignableFrom(type)) {
                return new StringBuilder(param, setter);
            } else {
                throw new UnsupportedOperationException("Unknown Type="+type+" Attachment is not mapped.");
            }
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            boolean foundAttachment = false;
            // TODO not to loop
            for (Attachment att : msg.getAttachments()) {
                String part = getWSDLPartName(att);
                if (part == null) {
                    continue;
                }
                if(part.equals(pname) || part.equals(pname1)){
                    foundAttachment = true;
                    mapAttachment(att, args);
                    break;
                }
            }
            if (!foundAttachment) {
                throw new WebServiceException("Missing Attachment for "+pname);
            }
        }

        abstract void mapAttachment(Attachment att, Object[] args) throws JAXBException;
    }

    private static final class DataHandlerBuilder extends AttachmentBuilder {
        DataHandlerBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            setter.put(att.asDataHandler(), args);
        }
    }

    private static final class ByteArrayBuilder extends AttachmentBuilder {
        ByteArrayBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            setter.put(att.asByteArray(), args);
        }
    }

    private static final class SourceBuilder extends AttachmentBuilder {
        SourceBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            setter.put(att.asSource(), args);
        }
    }

    private static final class ImageBuilder extends AttachmentBuilder {
        ImageBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            Image image;
            InputStream is = null;
            try {
                is = att.asInputStream();
                image = ImageIO.read(is);
            } catch(IOException ioe) {
                throw new WebServiceException(ioe);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch(IOException ioe) {
                        throw new WebServiceException(ioe);
                    }
                }
            }
            setter.put(image, args);
        }
    }

    private static final class InputStreamBuilder extends AttachmentBuilder {
        InputStreamBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            setter.put(att.asInputStream(), args);
        }
    }

    private static final class JAXBBuilder extends AttachmentBuilder {
        JAXBBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) throws JAXBException {
            Object obj = param.getXMLBridge().unmarshal(att.asInputStream());
            setter.put(obj, args);
        }
    }

    private static final class StringBuilder extends AttachmentBuilder {
        StringBuilder(ParameterImpl param, EndpointValueSetter setter) {
            super(param, setter);
        }

        void mapAttachment(Attachment att, Object[] args) {
            att.getContentType();
            StringDataContentHandler sdh = new StringDataContentHandler();
            try {
                String str = (String)sdh.getContent(new DataHandlerDataSource(att.asDataHandler()));
                setter.put(str, args);
            } catch(Exception e) {
                throw new WebServiceException(e);
            }
        }
    }

    /**
     * Gets the WSDL part name of this attachment.
     *
     * <p>
     * According to WSI AP 1.0
     * <PRE>
     * 3.8 Value-space of Content-Id Header
     *   Definition: content-id part encoding
     *   The "content-id part encoding" consists of the concatenation of:
     * The value of the name attribute of the wsdl:part element referenced by the mime:content, in which characters disallowed in content-id headers (non-ASCII characters as represented by code points above 0x7F) are escaped as follows:
     *     o Each disallowed character is converted to UTF-8 as one or more bytes.
     *     o Any bytes corresponding to a disallowed character are escaped with the URI escaping mechanism (that is, converted to %HH, where HH is the hexadecimal notation of the byte value).
     *     o The original character is replaced by the resulting character sequence.
     * The character '=' (0x3D).
     * A globally unique value such as a UUID.
     * The character '@' (0x40).
     * A valid domain name under the authority of the entity constructing the message.
     * </PRE>
     *
     * So a wsdl:part fooPart will be encoded as:
     *      <fooPart=somereallybignumberlikeauuid@example.com>
     *
     * @return null
     *      if the parsing fails.
     */
    public static final String getWSDLPartName(com.sun.xml.internal.ws.api.message.Attachment att){
        String cId = att.getContentId();

        int index = cId.lastIndexOf('@', cId.length());
        if(index == -1){
            return null;
        }
        String localPart = cId.substring(0, index);
        index = localPart.lastIndexOf('=', localPart.length());
        if(index == -1){
            return null;
        }
        try {
            return java.net.URLDecoder.decode(localPart.substring(0, index), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new WebServiceException(e);
        }
    }




    /**
     * Reads a header into a JAXB object.
     */
    public static final class Header extends EndpointArgumentsBuilder {
        private final XMLBridge<?> bridge;
        private final EndpointValueSetter setter;
        private final QName headerName;
        private final SOAPVersion soapVersion;

        /**
         * @param name
         *      The name of the header element.
         * @param bridge
         *      specifies how to unmarshal a header into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Header(SOAPVersion soapVersion, QName name, XMLBridge<?> bridge, EndpointValueSetter setter) {
            this.soapVersion = soapVersion;
            this.headerName = name;
            this.bridge = bridge;
            this.setter = setter;
        }

        public Header(SOAPVersion soapVersion, ParameterImpl param, EndpointValueSetter setter) {
            this(
                soapVersion,
                param.getTypeInfo().tagName,
                param.getXMLBridge(),
                setter);
            assert param.getOutBinding()== ParameterBinding.HEADER;
        }

        private SOAPFaultException createDuplicateHeaderException() {
            try {
                SOAPFault fault = soapVersion.getSOAPFactory().createFault();
                fault.setFaultCode(soapVersion.faultCodeClient);
                fault.setFaultString(ServerMessages.DUPLICATE_PORT_KNOWN_HEADER(headerName));
                return new SOAPFaultException(fault);
            } catch(SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException {
            com.sun.xml.internal.ws.api.message.Header header = null;
            Iterator<com.sun.xml.internal.ws.api.message.Header> it =
                msg.getHeaders().getHeaders(headerName,true);
            if (it.hasNext()) {
                header = it.next();
                if (it.hasNext()) {
                    throw createDuplicateHeaderException();
                }
            }

            if(header!=null) {
                setter.put( header.readAsJAXB(bridge), args );
            } else {
                // header not found.
            }
        }
    }

    /**
     * Reads the whole payload into a single JAXB bean.
     */
    public static final class Body extends EndpointArgumentsBuilder {
        private final XMLBridge<?> bridge;
        private final EndpointValueSetter setter;

        /**
         * @param bridge
         *      specifies how to unmarshal the payload into a JAXB object.
         * @param setter
         *      specifies how the obtained value is returned to the client.
         */
        public Body(XMLBridge<?> bridge, EndpointValueSetter setter) {
            this.bridge = bridge;
            this.setter = setter;
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException {
            setter.put( msg.readPayloadAsJAXB(bridge), args );
        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    public static final class DocLit extends EndpointArgumentsBuilder {
        /**
         * {@link PartBuilder} keyed by the element name (inside the wrapper element.)
         */
        private final PartBuilder[] parts;

        private final XMLBridge wrapper;
        private boolean dynamicWrapper;

        public DocLit(WrapperParameter wp, Mode skipMode) {
            wrapperName = wp.getName();
            wrapper = wp.getXMLBridge();
            Class wrapperType = (Class) wrapper.getTypeInfo().type;
            dynamicWrapper = WrapperComposite.class.equals(wrapperType);
            List<PartBuilder> parts = new ArrayList<PartBuilder>();
            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                if (p.getMode() == skipMode) {
                    continue;
                }
                /*
                if(p.isIN())
                    continue;
                 */
                QName name = p.getName();
                try {
                    if (dynamicWrapper) {
                        if (wrappedParts == null) wrappedParts = new HashMap<QName,WrappedPartBuilder>();
                        XMLBridge xmlBridge = p.getInlinedRepeatedElementBridge();
                        if (xmlBridge == null) xmlBridge = p.getXMLBridge();
                        wrappedParts.put( p.getName(), new WrappedPartBuilder(xmlBridge, EndpointValueSetter.get(p)));
                    } else {
                        parts.add( new PartBuilder(
                                wp.getOwner().getBindingContext().getElementPropertyAccessor(
                                    wrapperType,
                                    name.getNamespaceURI(),
                                    p.getName().getLocalPart()),
                                EndpointValueSetter.get(p)
                            ) );
                    // wrapper parameter itself always bind to body, and
                    // so do all its children
                        assert p.getBinding()== ParameterBinding.BODY;
                    }
                } catch (JAXBException e) {
                    throw new WebServiceException(  // TODO: i18n
                        wrapperType+" do not have a property of the name "+name,e);
                }
            }

            this.parts = parts.toArray(new PartBuilder[parts.size()]);
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            if (dynamicWrapper) {
                readWrappedRequest(msg, args);
            } else {
                if (parts.length>0) {
                    if (!msg.hasPayload()) {
                        throw new WebServiceException("No payload. Expecting payload with "+wrapperName+" element");
                    }
                    XMLStreamReader reader = msg.readPayload();
                    XMLStreamReaderUtil.verifyTag(reader, wrapperName);
                    Object wrapperBean = wrapper.unmarshal(reader, (msg.getAttachments() != null) ?
                            new AttachmentUnmarshallerImpl(msg.getAttachments()): null);

                    try {
                        for (PartBuilder part : parts) {
                            part.readRequest(args,wrapperBean);
                        }
                    } catch (DatabindingException e) {
                        // this can happen when the set method throw a checked exception or something like that
                        throw new WebServiceException(e);    // TODO:i18n
                    }

                    // we are done with the body
                    reader.close();
                    XMLStreamReaderFactory.recycle(reader);
                } else {
                    msg.consume();
                }
            }
        }

        /**
         * Unmarshals each wrapped part into a JAXB object and moves it
         * to the expected place.
         */
        static final class PartBuilder {
            private final PropertyAccessor accessor;
            private final EndpointValueSetter setter;

            /**
             * @param accessor
             *      specifies which portion of the wrapper bean to obtain the value from.
             * @param setter
             *      specifies how the obtained value is returned to the client.
             */
            public PartBuilder(PropertyAccessor accessor, EndpointValueSetter setter) {
                this.accessor = accessor;
                this.setter = setter;
                assert accessor!=null && setter!=null;
            }

            final void readRequest( Object[] args, Object wrapperBean ) {
                Object obj = accessor.get(wrapperBean);
                setter.put(obj,args);
            }


        }
    }

    /**
     * Treats a payload as multiple parts wrapped into one element,
     * and processes all such wrapped parts.
     */
    public static final class RpcLit extends EndpointArgumentsBuilder {
        public RpcLit(WrapperParameter wp) {
            assert wp.getTypeInfo().type== WrapperComposite.class;

            wrapperName = wp.getName();
            wrappedParts = new HashMap<QName,WrappedPartBuilder>();
            List<ParameterImpl> children = wp.getWrapperChildren();
            for (ParameterImpl p : children) {
                wrappedParts.put( p.getName(), new WrappedPartBuilder(
                    p.getXMLBridge(), EndpointValueSetter.get(p)
                ));
                // wrapper parameter itself always bind to body, and
                // so do all its children
                assert p.getBinding()== ParameterBinding.BODY;
            }
        }

        public void readRequest(Message msg, Object[] args) throws JAXBException, XMLStreamException {
            readWrappedRequest(msg, args);
        }
    }

    private static boolean isXMLMimeType(String mimeType){
        return mimeType.equals("text/xml") || mimeType.equals("application/xml");
    }
}
