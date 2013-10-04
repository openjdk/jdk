/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.xmlschema.parser;

import java.io.File;
import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;

import com.sun.tools.internal.xjc.ConsoleErrorReporter;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;

import com.sun.xml.internal.bind.v2.util.XmlFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

/**
 * Checks XML Schema XML representation constraints and
 * schema component constraints by using JAXP 1.3 validation framework.
 * <p/>
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @author Ryan Shoemaker (ryan.shoemaker@sun.com)
 */
public class SchemaConstraintChecker {

    /**
     * @param schemas      Schema files to be checked.
     * @param errorHandler detected errors will be reported to this handler.
     * @return true if there was no error, false if there were errors.
     */
    public static boolean check(InputSource[] schemas,
                                ErrorReceiver errorHandler,
                                final EntityResolver entityResolver,
                                boolean disableXmlSecurity) {

        ErrorReceiverFilter errorFilter = new ErrorReceiverFilter(errorHandler);
        boolean hadErrors = false;

        SchemaFactory sf = XmlFactory.createSchemaFactory(W3C_XML_SCHEMA_NS_URI, disableXmlSecurity);
        XmlFactory.allowExternalAccess(sf, "all", disableXmlSecurity);
        sf.setErrorHandler(errorFilter);
        if( entityResolver != null ) {
            sf.setResourceResolver(new LSResourceResolver() {
                public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
                    try {
                        // XSOM passes the namespace URI to the publicID parameter.
                        // we do the same here .
                        InputSource is = entityResolver.resolveEntity(namespaceURI, systemId);
                        if(is==null)    return null;
                        return new LSInputSAXWrapper(is);
                    } catch (SAXException e) {
                        // TODO: is this sufficient?
                        return null;
                    } catch (IOException e) {
                        // TODO: is this sufficient?
                        return null;
                    }
                }
            });
        }

        try {
            XmlFactory.allowExternalDTDAccess(sf, "all", disableXmlSecurity);
            sf.newSchema(getSchemaSource(schemas, entityResolver));
        } catch (SAXException e) {
            // TODO: we haven't thrown exceptions from here before. should we just trap them and return false?
            hadErrors = true;
        } catch( OutOfMemoryError e) {
            errorHandler.warning(null,Messages.format(Messages.WARN_UNABLE_TO_CHECK_CORRECTNESS));
        }

        return !(hadErrors || errorFilter.hadError());
    }

    /**
     * convert an array of {@link InputSource InputSource} into an
     * array of {@link Source Source}
     *
     * @param schemas array of {@link InputSource InputSource}
     * @return array of {@link Source Source}
     */
    private static Source[] getSchemaSource(InputSource[] schemas, EntityResolver entityResolver) throws SAXException {
        SAXSource[] sources = new SAXSource[schemas.length];
        for (int i = 0; i < schemas.length; i++) {
            sources[i] = new SAXSource(schemas[i]);
//            sources[i].getXMLReader().setEntityResolver(entityResolver);
        }
        return sources;
    }

    // quick test
    public static void main(String[] args) throws IOException {
        InputSource[] sources = new InputSource[args.length];
        for (int i = 0; i < args.length; i++)
            sources[i] = new InputSource(new File(args[i]).toURL().toExternalForm());

        check(sources, new ConsoleErrorReporter(), null, true);
    }
}
