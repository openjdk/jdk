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


package com.sun.xml.internal.xsom.parser;

import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Interface that hides the detail of parsing mechanism.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface XMLParser {
    /**
     * Parses the document identified by the given input source
     * and sends SAX events to the given content handler.
     *
     * <p>
     * This method must be re-entrant.
     *
     * @param errorHandler
     *      Errors found during the parsing must be reported to
     *      this handler so that XSOM can recognize that something went wrong.
     *      Always a non-null valid object
     * @param entityResolver
     *      Entity resolution should be done through this interface.
     *      Can be null.
     *
     * @exception SAXException
     *      If ErrorHandler throws a SAXException, this method
     *      will tunnel it to the caller. All the other errors
     *      must be reported to the error handler.
     */
    void parse( InputSource source, ContentHandler handler,
        ErrorHandler errorHandler, EntityResolver entityResolver )

        throws SAXException, IOException;
}
