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
package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import javax.xml.validation.ValidatorHandler;

import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.xml.internal.xsom.parser.AnnotationContext;
import com.sun.xml.internal.xsom.parser.AnnotationParser;
import com.sun.xml.internal.xsom.parser.AnnotationParserFactory;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Implementation of XSOM {@link AnnotationParserFactory} that
 * parses JAXB customization declarations.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class AnnotationParserFactoryImpl implements AnnotationParserFactory {
    public AnnotationParserFactoryImpl(Options opts) {
        this.options=opts;
    }

    private final Options options;
    /**
     * Lazily created validator, so that the schema for binding won't be
     * prepared unless absolutely necessary.
     */
    private ValidatorHandler validator;

    public AnnotationParser create() {
        return new AnnotationParser() {
            private Unmarshaller u = BindInfo.getJAXBContext().createUnmarshaller();

            private UnmarshallerHandler handler;

            public ContentHandler getContentHandler(
                AnnotationContext context, String parentElementName,
                final ErrorHandler errorHandler, EntityResolver entityResolver ) {

                // return a ContentHandler that validates the customization and also
                // parses them into the internal structure.
                if(handler!=null)
                    // interface contract violation.
                    // this method will be called only once.
                    throw new AssertionError();

                if(options.debugMode)
                    try {
                        u.setEventHandler(new DefaultValidationEventHandler());
                    } catch (JAXBException e) {
                        throw new AssertionError(e);    // ridiculous!
                    }

                handler = u.getUnmarshallerHandler();

                // configure so that the validator will receive events for JAXB islands
                return new ForkingFilter(handler) {
                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                        super.startElement(uri, localName, qName, atts);
                        if((uri.equals(Const.JAXB_NSURI) || uri.equals(Const.XJC_EXTENSION_URI))
                        && getSideHandler()==null) {
                            // set up validator
                            if(validator==null)
                                validator = BindInfo.bindingFileSchema.newValidator();
                            validator.setErrorHandler(errorHandler);
                            startForking(uri,localName,qName,atts,new ValidatorProtecter(validator));
                        }

                        // check for xmime:expectedContentTypes attributes in annotations and report them
                        for( int i=atts.getLength()-1; i>=0; i-- ) {
                            if(atts.getURI(i).equals(WellKnownNamespace.XML_MIME_URI)
                            && atts.getLocalName(i).equals(Const.EXPECTED_CONTENT_TYPES))
                                errorHandler.warning(new SAXParseException(
                                    com.sun.tools.internal.xjc.reader.xmlschema.Messages.format(
                                        com.sun.tools.internal.xjc.reader.xmlschema.Messages.WARN_UNUSED_EXPECTED_CONTENT_TYPES),
                                    getDocumentLocator()
                                ));
                        }
                    }
                };
            }

            public BindInfo getResult( Object existing ) {
                if(handler==null)
                    // interface contract violation.
                    // the getContentHandler method must have been called.
                    throw new AssertionError();

                try {
                    BindInfo result = (BindInfo)handler.getResult();

                    if(existing!=null) {
                        BindInfo bie = (BindInfo)existing;
                        bie.absorb(result);
                        return bie;
                    } else {
                        if(!result.isPointless())
                            return result;   // just annotation. no meaningful customization
                        else
                            return null;
                    }
                } catch (JAXBException e) {
                    throw new AssertionError(e);
                }
            }
        };
    }

    private static final class ValidatorProtecter extends XMLFilterImpl {
        public ValidatorProtecter(ContentHandler h) {
            setContentHandler(h);
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            // work around a bug in the validator implementation in Tiger
            super.startPrefixMapping(prefix.intern(),uri);
        }
    }
}
