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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.PropertyException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.bind.helpers.AbstractUnmarshallerImpl;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import com.sun.xml.internal.bind.IDResolver;
import com.sun.xml.internal.bind.api.ClassResolver;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.bind.unmarshaller.InfosetScanner;
import com.sun.xml.internal.bind.unmarshaller.Messages;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.runtime.AssociationMap;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.util.XmlFactory;

import java.io.Closeable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Default Unmarshaller implementation.
 *
 * <p>
 * This class can be extended by the generated code to provide
 * type-safe unmarshall methods.
 *
 * @author
 *  <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
 public final class UnmarshallerImpl extends AbstractUnmarshallerImpl implements ValidationEventHandler, Closeable
{
    /** Owning {@link JAXBContext} */
    protected final JAXBContextImpl context;

    /**
     * schema which will be used to validate during calls to unmarshal
     */
    private Schema schema;

    public final UnmarshallingContext coordinator;

    /** Unmarshaller.Listener */
    private Listener externalListener;

    /**
     * The attachment unmarshaller used to support MTOM and swaRef.
     */
    private AttachmentUnmarshaller attachmentUnmarshaller;
    private IDResolver idResolver = new DefaultIDResolver();

    public UnmarshallerImpl( JAXBContextImpl context, AssociationMap assoc ) {
        this.context = context;
        this.coordinator = new UnmarshallingContext( this, assoc );

        try {
            setEventHandler(this);
        } catch (JAXBException e) {
            throw new AssertionError(e);    // impossible
        }
    }

    public UnmarshallerHandler getUnmarshallerHandler() {
        return getUnmarshallerHandler(true,null);
    }

    private XMLReader reader = null;

    /**
     * Obtains a configured XMLReader.
     *
     * This method is used when the client-specified
     * {@link SAXSource} object doesn't have XMLReader.
     *
     * {@link Unmarshaller} is not re-entrant, so we will
     * only use one instance of XMLReader.
     *
     * Overriden in order to fix potential security issue.
     */
     @Override
    protected XMLReader getXMLReader() throws JAXBException {
         if (reader == null) {
             try {
                 SAXParserFactory parserFactory = XmlFactory.createParserFactory(context.disableSecurityProcessing);
                 // there is no point in asking a validation because
                 // there is no guarantee that the document will come with
                 // a proper schemaLocation.
                 parserFactory.setValidating(false);
                 reader = parserFactory.newSAXParser().getXMLReader();
             } catch (ParserConfigurationException e) {
                 throw new JAXBException(e);
             } catch (SAXException e) {
                 throw new JAXBException(e);
             }
         }
         return reader;
     }

    private SAXConnector getUnmarshallerHandler( boolean intern, JaxBeanInfo expectedType ) {
        XmlVisitor h = createUnmarshallerHandler(null, false, expectedType);
        if (intern) {
            h = new InterningXmlVisitor(h);
        }
        return new SAXConnector(h,null);
    }

    /**
     * Creates and configures a new unmarshalling pipe line.
     * Depending on the setting, we put a validator as a filter.
     *
     * @return
     *      A component that implements both {@link UnmarshallerHandler}
     *      and {@link ValidationEventHandler}. All the parsing errors
     *      should be reported to this error handler for the unmarshalling
     *      process to work correctly.
     *
     *      Also, returned handler expects all the XML names to be interned.
     *
     */
    public final XmlVisitor createUnmarshallerHandler(InfosetScanner scanner, boolean inplace, JaxBeanInfo expectedType ) {

        coordinator.reset(scanner,inplace,expectedType,idResolver);
        XmlVisitor unmarshaller = coordinator;

        // delegate to JAXP 1.3 for validation if the client provided a schema
        if (schema != null) {
            unmarshaller = new ValidatingUnmarshaller(schema,unmarshaller);
        }

        if(attachmentUnmarshaller!=null && attachmentUnmarshaller.isXOPPackage()) {
            unmarshaller = new MTOMDecorator(this,unmarshaller,attachmentUnmarshaller);
        }

        return unmarshaller;
    }

    private static final DefaultHandler dummyHandler = new DefaultHandler();

    public static boolean needsInterning( XMLReader reader ) {
        // attempt to set it to true, which could fail
        try {
            reader.setFeature("http://xml.org/sax/features/string-interning",true);
        } catch (SAXException e) {
            // if it fails that's fine. we'll work around on our side
        }

        try {
            if (reader.getFeature("http://xml.org/sax/features/string-interning")) {
                return false;   // no need for intern
            }
        } catch (SAXException e) {
            // unrecognized/unsupported
        }
        // otherwise we need intern
        return true;
    }

    protected Object unmarshal( XMLReader reader, InputSource source ) throws JAXBException {
        return unmarshal0(reader,source,null);
    }

    protected <T> JAXBElement<T> unmarshal( XMLReader reader, InputSource source, Class<T> expectedType ) throws JAXBException {
        if(expectedType==null) {
            throw new IllegalArgumentException();
        }
        return (JAXBElement)unmarshal0(reader,source,getBeanInfo(expectedType));
    }

    private Object unmarshal0( XMLReader reader, InputSource source, JaxBeanInfo expectedType ) throws JAXBException {

        SAXConnector connector = getUnmarshallerHandler(needsInterning(reader),expectedType);

        reader.setContentHandler(connector);
        // saxErrorHandler will be set by the getUnmarshallerHandler method.
        // configure XMLReader so that the error will be sent to it.
        // This is essential for the UnmarshallerHandler to be able to abort
        // unmarshalling when an error is found.
        //
        // Note that when this XMLReader is provided by the client code,
        // it might be already configured to call a client error handler.
        // This will clobber such handler, if any.
        //
        // Ryan noted that we might want to report errors to such a client
        // error handler as well.
        reader.setErrorHandler(coordinator);

        try {
            reader.parse(source);
        } catch( IOException e ) {
            coordinator.clearStates();
            throw new UnmarshalException(e);
        } catch( SAXException e ) {
            coordinator.clearStates();
            throw createUnmarshalException(e);
        }

        Object result = connector.getResult();

        // avoid keeping unnecessary references too long to let the GC
        // reclaim more memory.
        // setting null upsets some parsers, so use a dummy instance instead.
        reader.setContentHandler(dummyHandler);
        reader.setErrorHandler(dummyHandler);

        return result;
    }

    @Override
    public <T> JAXBElement<T> unmarshal( Source source, Class<T> expectedType ) throws JAXBException {
        if (source instanceof SAXSource) {
            SAXSource ss = (SAXSource) source;

            XMLReader locReader = ss.getXMLReader();
            if (locReader == null) {
                locReader = getXMLReader();
            }

            return unmarshal(locReader, ss.getInputSource(), expectedType);
        }
        if (source instanceof StreamSource) {
            return unmarshal(getXMLReader(), streamSourceToInputSource((StreamSource) source), expectedType);
        }
        if (source instanceof DOMSource) {
            return unmarshal(((DOMSource) source).getNode(), expectedType);
        }

        // we don't handle other types of Source
        throw new IllegalArgumentException();
    }

    public Object unmarshal0( Source source, JaxBeanInfo expectedType ) throws JAXBException {
        if (source instanceof SAXSource) {
            SAXSource ss = (SAXSource) source;

            XMLReader locReader = ss.getXMLReader();
            if (locReader == null) {
                locReader = getXMLReader();
            }

            return unmarshal0(locReader, ss.getInputSource(), expectedType);
        }
        if (source instanceof StreamSource) {
            return unmarshal0(getXMLReader(), streamSourceToInputSource((StreamSource) source), expectedType);
        }
        if (source instanceof DOMSource) {
            return unmarshal0(((DOMSource) source).getNode(), expectedType);
        }

        // we don't handle other types of Source
        throw new IllegalArgumentException();
    }


    @Override
    public final ValidationEventHandler getEventHandler() {
        try {
            return super.getEventHandler();
        } catch (JAXBException e) {
            // impossible
            throw new AssertionError();
        }
    }

    /**
     * Returns true if an event handler is installed.
     * <p>
     * The default handler ignores any errors, and for that this method returns false.
     */
    public final boolean hasEventHandler() {
        return getEventHandler()!=this;
    }

    @Override
    public <T> JAXBElement<T> unmarshal(Node node, Class<T> expectedType) throws JAXBException {
        if (expectedType == null) {
            throw new IllegalArgumentException();
        }
        return (JAXBElement)unmarshal0(node,getBeanInfo(expectedType));
    }

    public final Object unmarshal( Node node ) throws JAXBException {
        return unmarshal0(node,null);
    }

    // just to make the the test harness happy by making this method accessible
    @Deprecated
    public final Object unmarshal( SAXSource source ) throws JAXBException {
        return super.unmarshal(source);
    }

    public final Object unmarshal0( Node node, JaxBeanInfo expectedType ) throws JAXBException {
        try {
            final DOMScanner scanner = new DOMScanner();

            InterningXmlVisitor handler = new InterningXmlVisitor(createUnmarshallerHandler(null,false,expectedType));
            scanner.setContentHandler(new SAXConnector(handler,scanner));

            if(node.getNodeType() == Node.ELEMENT_NODE) {
                scanner.scan((Element)node);
            } else if(node.getNodeType() == Node.DOCUMENT_NODE) {
                scanner.scan((Document)node);
            } else {
                throw new IllegalArgumentException("Unexpected node type: "+node);
            }

            Object retVal = handler.getContext().getResult();
            handler.getContext().clearResult();
            return retVal;
        } catch( SAXException e ) {
            throw createUnmarshalException(e);
        }
    }

    @Override
    public Object unmarshal(XMLStreamReader reader) throws JAXBException {
        return unmarshal0(reader,null);
    }

    @Override
    public <T> JAXBElement<T> unmarshal(XMLStreamReader reader, Class<T> expectedType) throws JAXBException {
        if (expectedType==null) {
            throw new IllegalArgumentException();
        }
        return (JAXBElement)unmarshal0(reader,getBeanInfo(expectedType));
    }

    public Object unmarshal0(XMLStreamReader reader, JaxBeanInfo expectedType) throws JAXBException {
        if (reader == null) {
            throw new IllegalArgumentException(
                Messages.format(Messages.NULL_READER));
        }

        int eventType = reader.getEventType();
        if (eventType != XMLStreamConstants.START_ELEMENT
            && eventType != XMLStreamConstants.START_DOCUMENT) {
            // TODO: convert eventType into event name
            throw new IllegalStateException(
                Messages.format(Messages.ILLEGAL_READER_STATE,eventType));
        }

        XmlVisitor h = createUnmarshallerHandler(null,false,expectedType);
        StAXConnector connector=StAXStreamConnector.create(reader,h);

        try {
            connector.bridge();
        } catch (XMLStreamException e) {
            throw handleStreamException(e);
        }

        Object retVal = h.getContext().getResult();
        h.getContext().clearResult();
        return retVal;
    }

    @Override
    public <T> JAXBElement<T> unmarshal(XMLEventReader reader, Class<T> expectedType) throws JAXBException {
        if(expectedType==null) {
            throw new IllegalArgumentException();
        }
        return (JAXBElement)unmarshal0(reader,getBeanInfo(expectedType));
    }

    @Override
    public Object unmarshal(XMLEventReader reader) throws JAXBException {
        return unmarshal0(reader,null);
    }

    private Object unmarshal0(XMLEventReader reader,JaxBeanInfo expectedType) throws JAXBException {
        if (reader == null) {
            throw new IllegalArgumentException(
                    Messages.format(Messages.NULL_READER));
        }

        try {
            XMLEvent event = reader.peek();

            if (!event.isStartElement() && !event.isStartDocument()) {
                // TODO: convert event into event name
                throw new IllegalStateException(
                    Messages.format(
                        Messages.ILLEGAL_READER_STATE,event.getEventType()));
            }

            // Quick hack until SJSXP fixes 6270116
            boolean isZephyr = reader.getClass().getName().equals("com.sun.xml.internal.stream.XMLReaderImpl");
            XmlVisitor h = createUnmarshallerHandler(null,false,expectedType);
            if(!isZephyr) {
                h = new InterningXmlVisitor(h);
            }
            new StAXEventConnector(reader,h).bridge();
            return h.getContext().getResult();
        } catch (XMLStreamException e) {
            throw handleStreamException(e);
        }
    }

    public Object unmarshal0( InputStream input, JaxBeanInfo expectedType ) throws JAXBException {
        return unmarshal0(getXMLReader(),new InputSource(input),expectedType);
    }

    private static JAXBException handleStreamException(XMLStreamException e) {
        // StAXStreamConnector wraps SAXException to XMLStreamException.
        // XMLStreamException doesn't print its nested stack trace when it prints
        // its stack trace, so if we wrap XMLStreamException in JAXBException,
        // it becomes harder to find out the real problem.
        // So we unwrap them here. But we don't want to unwrap too eagerly, because
        // that could throw away some meaningful exception information.
        Throwable ne = e.getNestedException();
        if(ne instanceof JAXBException) {
            return (JAXBException)ne;
        }
        if(ne instanceof SAXException) {
            return new UnmarshalException(ne);
        }
        return new UnmarshalException(e);
    }

    @Override
    public Object getProperty(String name) throws PropertyException {
        if(name.equals(IDResolver.class.getName())) {
            return idResolver;
        }
        return super.getProperty(name);
    }

    @Override
    public void setProperty(String name, Object value) throws PropertyException {
        if(name.equals(FACTORY)) {
            coordinator.setFactories(value);
            return;
        }
        if(name.equals(IDResolver.class.getName())) {
            idResolver = (IDResolver)value;
            return;
        }
        if(name.equals(ClassResolver.class.getName())) {
            coordinator.classResolver = (ClassResolver)value;
            return;
        }
        if(name.equals(ClassLoader.class.getName())) {
            coordinator.classLoader = (ClassLoader)value;
            return;
        }
        super.setProperty(name, value);
    }

    public static final String FACTORY = "com.sun.xml.internal.bind.ObjectFactory";

    @Override
    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public AttachmentUnmarshaller getAttachmentUnmarshaller() {
        return attachmentUnmarshaller;
    }

    @Override
    public void setAttachmentUnmarshaller(AttachmentUnmarshaller au) {
        this.attachmentUnmarshaller = au;
    }

    /**
     * @deprecated since 2.0
     */
    @Override
    public boolean isValidating() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated since 2.0
     */
    @Override
    public void setValidating(boolean validating) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A extends XmlAdapter> void setAdapter(Class<A> type, A adapter) {
        if (type==null) {
            throw new IllegalArgumentException();
        }
        coordinator.putAdapter(type,adapter);
    }

    @Override
    public <A extends XmlAdapter> A getAdapter(Class<A> type) {
        if(type==null) {
            throw new IllegalArgumentException();
        }
        if(coordinator.containsAdapter(type)) {
            return coordinator.getAdapter(type);
        } else {
            return null;
        }
    }

    // opening up for public use
    @Override
    public UnmarshalException createUnmarshalException( SAXException e ) {
        return super.createUnmarshalException(e);
    }


    /**
     * Default error handling behavior for {@link Unmarshaller}.
     */
    public boolean handleEvent(ValidationEvent event) {
        return event.getSeverity()!=ValidationEvent.FATAL_ERROR;
    }

    private static InputSource streamSourceToInputSource( StreamSource ss ) {
        InputSource is = new InputSource();
        is.setSystemId( ss.getSystemId() );
        is.setByteStream( ss.getInputStream() );
        is.setCharacterStream( ss.getReader() );

        return is;
    }

    public <T> JaxBeanInfo<T> getBeanInfo(Class<T> clazz) throws JAXBException {
        return context.getBeanInfo(clazz,true);
    }

    @Override
    public Listener getListener() {
        return externalListener;
    }

    @Override
    public void setListener(Listener listener) {
        externalListener = listener;
    }

    public UnmarshallingContext getContext() {
        return coordinator;
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        try {
            ClassFactory.cleanCache();
        } finally {
            super.finalize();
        }
    }

    /**
     *  Must be called from same thread which created the UnmarshallerImpl instance.
     * @throws IOException
     */
    public void close() throws IOException {
        ClassFactory.cleanCache();
    }

}
