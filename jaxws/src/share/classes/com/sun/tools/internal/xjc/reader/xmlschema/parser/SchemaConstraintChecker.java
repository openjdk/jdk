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
package com.sun.tools.internal.xjc.reader.xmlschema.parser;

import java.io.File;
import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;

import com.sun.tools.internal.xjc.ConsoleErrorReporter;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.util.ErrorReceiverFilter;

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
                                ErrorReceiver errorHandler, final EntityResolver entityResolver) {

        ErrorReceiverFilter errorFilter = new ErrorReceiverFilter(errorHandler);
        boolean hadErrors = false;

        SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
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
            sf.newSchema(getSchemaSource(schemas));
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
    private static Source[] getSchemaSource(InputSource[] schemas) {
        SAXSource[] sources = new SAXSource[schemas.length];
        for (int i = 0; i < schemas.length; i++)
            sources[i] = new SAXSource(schemas[i]);
        return sources;
    }

    // quick test
    public static void main(String[] args) throws IOException {
        InputSource[] sources = new InputSource[args.length];
        for (int i = 0; i < args.length; i++)
            sources[i] = new InputSource(new File(args[i]).toURL().toExternalForm());

        check(sources, new ConsoleErrorReporter(), null);
    }
}
