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

import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity resolver that works on MEX Metadata
 *
 * @author Vivek Pandey
 */
public final class MexEntityResolver implements XMLEntityResolver {
    private final Map<String, SDDocumentSource> wsdls = new HashMap<String, SDDocumentSource>();

    public MexEntityResolver(List<? extends Source> wsdls) throws IOException {
        Transformer transformer = XmlUtil.newTransformer();
        for (Source source : wsdls) {
            XMLStreamBufferResult xsbr = new XMLStreamBufferResult();
            try {
                transformer.transform(source, xsbr);
            } catch (TransformerException e) {
                throw new WebServiceException(e);
            }
            String systemId = source.getSystemId();

            //TODO: can we do anything if the given mex Source has no systemId?
            if(systemId != null){
                SDDocumentSource doc = SDDocumentSource.create(JAXWSUtils.getFileOrURL(systemId), xsbr.getXMLStreamBuffer());
                this.wsdls.put(systemId, doc);
            }
        }
    }

    public Parser resolveEntity(String publicId, String systemId) throws SAXException, IOException, XMLStreamException {
        if (systemId != null) {
            SDDocumentSource src = wsdls.get(systemId);
            if (src != null)
                return new Parser(src);
        }
        return null;
    }
}
