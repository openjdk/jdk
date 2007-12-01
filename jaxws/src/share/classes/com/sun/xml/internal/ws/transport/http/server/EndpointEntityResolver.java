/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.http.server;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import com.sun.xml.internal.ws.server.DocInfo;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author WS Developement Team
 */
public class EndpointEntityResolver implements EntityResolver {

    private EntityResolver catalogResolver;
    private Map<String, DocInfo> metadata;

    /*
     * Assumes Source objects can be reused multiple times
     */
    public EndpointEntityResolver(Map<String, DocInfo> metadata) {
        this.metadata = metadata;
        catalogResolver = XmlUtil.createDefaultCatalogResolver();
    }

    /*
     * It resolves the systemId in the metadata first. If it is not found, then
     * it tries to resolve in catalog
     */
    public InputSource resolveEntity (String publicId, String systemId)
        throws SAXException, IOException {
        if (systemId != null) {
            DocInfo docInfo = metadata.get(systemId);
            if (docInfo != null) {
                InputStream in = docInfo.getDoc();
                InputSource is = new InputSource(in);
                is.setSystemId(systemId);
                return is;
            }
        }
        return catalogResolver.resolveEntity(publicId, systemId);
    }

}
