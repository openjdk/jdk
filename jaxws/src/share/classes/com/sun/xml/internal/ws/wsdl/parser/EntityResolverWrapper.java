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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.streaming.TidyXMLStreamReader;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Wraps {@link EntityResolver} into {@link com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver}.
 *
 * @author Kohsuke Kawaguchi
 */
final class EntityResolverWrapper implements XMLEntityResolver {
    private final EntityResolver core;

    public EntityResolverWrapper(EntityResolver core) {
        this.core = core;
    }

    public Parser resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        InputSource source = core.resolveEntity(publicId,systemId);
        if(source==null)
            return null;    // default

        // ideally entity resolvers should be giving us the system ID for the resource
        // (or otherwise we won't be able to resolve references within this imported WSDL correctly),
        // but if none is given, the system ID before the entity resolution is better than nothing.
        if(source.getSystemId()!=null)
            systemId = source.getSystemId();

        URL url = new URL(systemId);
        InputStream stream = url.openStream();
        return new Parser(url,
                new TidyXMLStreamReader(XMLStreamReaderFactory.create(url.toExternalForm(), stream, true), stream));
    }
}
