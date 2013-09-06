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

package com.sun.xml.internal.xsom.parser;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import com.sun.xml.internal.xsom.impl.parser.Messages;

/**
 * Standard XMLParser implemented by using JAXP.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class JAXPParser implements XMLParser {

    // not in older JDK, so must be duplicated here, otherwise javax.xml.XMLConstants should be used
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";

    private static final Logger LOGGER = Logger.getLogger(JAXPParser.class.getName());

    private final SAXParserFactory factory;

    public JAXPParser( SAXParserFactory factory ) {
        factory.setNamespaceAware(true);    // just in case
        this.factory = factory;
    }

    /**
     * @deprecated Unsafe, use JAXPParser(factory) instead with
     * security features initialized by setting
     * XMLConstants.FEATURE_SECURE_PROCESSING feature.
     */
    public JAXPParser() {
        this( SAXParserFactory.newInstance());
    }

    public void parse( InputSource source, ContentHandler handler,
        ErrorHandler errorHandler, EntityResolver entityResolver )

        throws SAXException, IOException {

        try {
            SAXParser saxParser = allowFileAccess(factory.newSAXParser(), false);
            XMLReader reader = new XMLReaderEx(saxParser.getXMLReader());

            reader.setContentHandler(handler);
            if(errorHandler!=null)
                reader.setErrorHandler(errorHandler);
            if(entityResolver!=null)
                reader.setEntityResolver(entityResolver);
            reader.parse(source);
        } catch( ParserConfigurationException e ) {
            // in practice this won't happen
            SAXParseException spe = new SAXParseException(e.getMessage(),null,e);
            errorHandler.fatalError(spe);
            throw spe;
        }
    }

    private static SAXParser allowFileAccess(SAXParser saxParser, boolean disableSecureProcessing) throws SAXException {

        // if feature secure processing enabled, nothing to do, file is allowed,
        // or user is able to control access by standard JAXP mechanisms
        if (disableSecureProcessing) {
            return saxParser;
        }

        try {
            saxParser.setProperty(ACCESS_EXTERNAL_SCHEMA, "file");
            LOGGER.log(Level.FINE, Messages.format(Messages.JAXP_SUPPORTED_PROPERTY, ACCESS_EXTERNAL_SCHEMA));
        } catch (SAXException ignored) {
            // nothing to do; support depends on version JDK or SAX implementation
            LOGGER.log(Level.CONFIG, Messages.format(Messages.JAXP_UNSUPPORTED_PROPERTY, ACCESS_EXTERNAL_SCHEMA), ignored);
        }
        return saxParser;
    }

    /**
     * XMLReader with improved error message for entity resolution failure.
     *
     * TODO: this class is completely stand-alone, so it shouldn't be
     * an inner class.
     */
    private static class XMLReaderEx extends XMLFilterImpl {

        private Locator locator;

        XMLReaderEx( XMLReader parent ) {
            this.setParent(parent);
        }

        /**
         * Resolves entities and reports user-friendly error messages.
         *
         * <p>
         * Some XML parser (at least Xerces) does not report much information
         * when it fails to resolve an entity, which is often quite
         * frustrating. For example, if you are behind a firewall and the
         * schema contains a reference to www.w3.org, and there is no
         * entity resolver, the parser will just throw an IOException
         * that doesn't contain any information about where that reference
         * occurs nor what it is accessing.
         *
         * <p>
         * By implementing an EntityResolver and resolving the reference
         * by ourselves, we can report an error message with all the
         * necessary information to fix the problem.
         *
         * <p>
         * Note that we still need to the client-specified entity resolver
         * to let the application handle entity resolution. Here we just catch
         * an IOException and add more information.
         */
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            try {
                InputSource is=null;

                // ask the client-specified entity resolver first
                if( this.getEntityResolver()!=null)
                    is = this.getEntityResolver().resolveEntity(publicId,systemId);
                if( is!=null )  return is;  // if that succeeds, fine.

                // rather than returning null, resolve it now
                // so that we can detect errors.
                is = new InputSource( new URL(systemId).openStream() );
                is.setSystemId(systemId);
                is.setPublicId(publicId);
                return is;
            } catch( IOException e ) {
                // catch this error and provide a nice error message, rather than
                // just throwing this IOException.
                SAXParseException spe = new SAXParseException(
                    Messages.format(Messages.ERR_ENTITY_RESOLUTION_FAILURE,
                        systemId, e.toString()),    // use the toString method to get the class name
                    locator, e );
                if(this.getErrorHandler()!=null)
                    this.getErrorHandler().fatalError(spe);
                throw spe;
            }
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            super.setDocumentLocator(locator);
            this.locator = locator;
        }
    }
}
