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

package com.sun.xml.internal.ws.util.xml;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.SAXParseException2;
import com.sun.istack.internal.XMLStreamReaderToContentHandler;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.sax.SAXSource;

/**
 * A JAXP {@link javax.xml.transform.Source} implementation that wraps
 * the specified {@link javax.xml.stream.XMLStreamReader} or
 * {@link javax.xml.stream.XMLEventReader} for use by applications that
 * expect a {@link javax.xml.transform.Source}.
 *
 * <p>
 * The fact that StAXSource derives from SAXSource is an implementation
 * detail. Thus in general applications are strongly discouraged from
 * accessing methods defined on SAXSource. In particular:
 *
 * <ul>
 *   <li> The setXMLReader and setInputSource methods shall never be called.</li>
 *   <li> The XMLReader object obtained by the getXMLReader method shall
 *        be used only for parsing the InputSource object returned by
 *        the getInputSource method.</li>
 *   <li> The InputSource object obtained by the getInputSource method shall
 *        be used only for being parsed by the XMLReader object returned by
 *        the getXMLReader method.</li>
 * </ul>
 *
 * <p>
 * Example:
 *
 * <pre>
    // create a StAXSource
    XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileReader(args[0]));
    Source staxSource = new StAXSource(reader);

    // create a StreamResult
    Result streamResult = new StreamResult(System.out);

    // run the transform
    TransformerFactory.newInstance().newTransformer().transform(staxSource, streamResult);
 * </pre>
 *
 * @author Ryan.Shoemaker@Sun.COM
 * @version 1.0
 */
public class StAXSource extends SAXSource {

    // StAX to SAX converter that will read from StAX and produce SAX
    // this object will be wrapped by the XMLReader exposed to the client
    private final XMLStreamReaderToContentHandler reader;

    private final XMLStreamReader staxReader;

    // SAX allows ContentHandler to be changed during the parsing,
    // but JAXB doesn't. So this repeater will sit between those
    // two components.
    private final XMLFilterImpl repeater = new XMLFilterImpl();

    // this object will pretend as an XMLReader.
    // no matter what parameter is specified to the parse method,
    // it will just read from the StAX reader.
    private final XMLReader pseudoParser = new XMLReader() {

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException {
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException {
            // Should support these two features according to XMLReader javadoc.
            if (name.equals("http://xml.org/sax/features/namespaces") && value) {
                // Ignore for now
            } else if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !value) {
                // Ignore for now
            } else {
                throw new SAXNotRecognizedException(name);
            }
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException {
            if( "http://xml.org/sax/properties/lexical-handler".equals(name) ) {
                return lexicalHandler;
            }
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException {
            if( "http://xml.org/sax/properties/lexical-handler".equals(name) ) {
                this.lexicalHandler = (LexicalHandler)value;
                return;
            }
            throw new SAXNotRecognizedException(name);
        }

        private LexicalHandler lexicalHandler;

        // we will store this value but never use it by ourselves.
        private EntityResolver entityResolver;

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            this.entityResolver = resolver;
        }

        @Override
        public EntityResolver getEntityResolver() {
            return entityResolver;
        }

        private DTDHandler dtdHandler;

        @Override
        public void setDTDHandler(DTDHandler handler) {
            this.dtdHandler = handler;
        }
        @Override
        public DTDHandler getDTDHandler() {
            return dtdHandler;
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            repeater.setContentHandler(handler);
        }

        @Override
        public ContentHandler getContentHandler() {
            return repeater.getContentHandler();
        }

        private ErrorHandler errorHandler;

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
        }
        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void parse(InputSource input) throws SAXException {
            parse();
        }

        @Override
        public void parse(String systemId) throws SAXException {
            parse();
        }

        public void parse() throws SAXException {
            // parses from a StAX reader and generates SAX events which
            // go through the repeater and are forwarded to the appropriate
            // component
            try {
                reader.bridge();
            } catch( XMLStreamException e ) {
                // wrap it in a SAXException
                SAXParseException se =
                    new SAXParseException2(
                        e.getMessage(),
                        null,
                        null,
                        e.getLocation() == null ? -1 : e.getLocation().getLineNumber(),
                        e.getLocation() == null ? -1 : e.getLocation().getColumnNumber(),
                        e);

                // if the consumer sets an error handler, it is our responsibility
                // to notify it.
                if(errorHandler!=null)
                    errorHandler.fatalError(se);

                // this is a fatal error. Even if the error handler
                // returns, we will abort anyway.
                throw se;

            } finally {
                try {
                    staxReader.close();
                } catch(XMLStreamException xe) {
                    //falls through. Not much can be done.
                }
            }
        }
    };

    /**
     * Creates a new {@link javax.xml.transform.Source} for the given
     * {@link XMLStreamReader}.
     *
     * @param reader XMLStreamReader that will be exposed as a Source
     * @param eagerQuit if true, when the conversion is completed, leave the cursor to the last
     *                  event that was fired (such as end element)
     * @see #StAXSource(XMLStreamReader, boolean, String[])
     */
    public StAXSource(XMLStreamReader reader, boolean eagerQuit) {
        this(reader, eagerQuit, new String[0]);
    }

    /**
     * Creates a new {@link javax.xml.transform.Source} for the given
     * {@link XMLStreamReader}.
     *
     * The XMLStreamReader must be pointing at either a
     * {@link javax.xml.stream.XMLStreamConstants#START_DOCUMENT} or
     * {@link javax.xml.stream.XMLStreamConstants#START_ELEMENT} event.
     *
     * @param reader XMLStreamReader that will be exposed as a Source
     * @param eagerQuit if true, when the conversion is completed, leave the cursor to the last
     *                  event that was fired (such as end element)
     * @param inscope inscope Namespaces
     *                array of the even length of the form { prefix0, uri0, prefix1, uri1, ... }
     * @throws IllegalArgumentException iff the reader is null
     * @throws IllegalStateException iff the reader is not pointing at either a
     * START_DOCUMENT or START_ELEMENT event
     */
    public StAXSource(XMLStreamReader reader, boolean eagerQuit, @NotNull String[] inscope) {
        if( reader == null )
            throw new IllegalArgumentException();
        this.staxReader = reader;

        int eventType = reader.getEventType();
        if (!(eventType == XMLStreamConstants.START_DOCUMENT)
            && !(eventType == XMLStreamConstants.START_ELEMENT)) {
            throw new IllegalStateException();
        }

        this.reader = new XMLStreamReaderToContentHandler(reader,repeater,eagerQuit,false,inscope);

        super.setXMLReader(pseudoParser);
        // pass a dummy InputSource. We don't care
        super.setInputSource(new InputSource());
    }

//    /**
//     * Creates a new {@link javax.xml.transform.Source} for the given
//     * {@link XMLEventReader}.
//     *
//     * The XMLEventReader must be pointing at either a
//     * {@link javax.xml.stream.XMLStreamConstants#START_DOCUMENT} or
//     * {@link javax.xml.stream.XMLStreamConstants#START_ELEMENT} event.
//     *
//     * @param reader XMLEventReader that will be exposed as a Source
//     * @throws IllegalArgumentException iff the reader is null
//     * @throws IllegalStateException iff the reader is not pointing at either a
//     * START_DOCUEMENT or START_ELEMENT event
//     */
//    public StAXSource(XMLEventReader reader) {
//        if( reader == null )
//            throw new IllegalArgumentException();
//
//        // TODO: detect IllegalStateException for START_ELEMENT|DOCUMENT
//        // bugid 5046340 - peek not implemented
//        // XMLEvent event = staxEventReader.peek();
//
//        this.reader =
//            new XMLEventReaderToContentHandler(
//                reader,
//                repeater);
//
//        super.setXMLReader(pseudoParser);
//        // pass a dummy InputSource. We don't care
//        super.setInputSource(new InputSource());
//    }

}
