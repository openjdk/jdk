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
package com.sun.tools.internal.xjc.reader.internalizer;

import java.io.IOException;

import com.sun.xml.internal.xsom.parser.XMLParser;

import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/**
 * {@link XMLParser} implementation that
 * parses XML from a DOM forest instead of parsing it from
 * its original location.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class DOMForestParser implements XMLParser {

    /** DOM forest to be "parsed". */
    private final DOMForest forest;

    /** Scanner object will do the actual SAX events generation. */
    private final DOMForestScanner scanner;

    private final XMLParser fallbackParser;

    /**
     * @param fallbackParser
     *      This parser will be used when DOMForestParser needs to parse
     *      documents that are not in the forest.
     */
    DOMForestParser( DOMForest forest, XMLParser fallbackParser ) {
        this.forest = forest;
        this.scanner = new DOMForestScanner(forest);
        this.fallbackParser = fallbackParser;
    }

    public void parse(
        InputSource source,
        ContentHandler contentHandler,
        ErrorHandler errorHandler,
        EntityResolver entityResolver )
        throws SAXException, IOException {

        String systemId = source.getSystemId();
        Document dom = forest.get(systemId);

        if(dom==null) {
            // if no DOM tree is built for it,
            // let the fall back parser parse the original document.
            //
            // for example, XSOM parses datatypes.xsd (XML Schema part 2)
            // but this will never be built into the forest.
            fallbackParser.parse( source, contentHandler, errorHandler, entityResolver );
            return;
        }

        scanner.scan( dom, contentHandler );
    }
}
