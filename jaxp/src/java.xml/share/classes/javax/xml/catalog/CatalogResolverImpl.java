/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package javax.xml.catalog;

import java.io.StringReader;
import java.util.Iterator;
import org.xml.sax.InputSource;

/**
 * A SAX EntityResolver/JAXP URIResolver that uses catalogs.
 *
 * <p>
 * This class implements both a SAX EntityResolver and a JAXP URIResolver.
 *
 *
 * @since 9
 */
final class CatalogResolverImpl implements CatalogResolver {
    Catalog catalog;

    /**
     * Construct an instance of the CatalogResolver from a Catalog.
     *
     * @param catalog A Catalog.
     */
    public CatalogResolverImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        //Normalize publicId and systemId
        systemId = Normalizer.normalizeURI(Util.getNotNullOrEmpty(systemId));
        publicId = Normalizer.normalizePublicId(Normalizer.decodeURN(Util.getNotNullOrEmpty(publicId)));

        //check whether systemId is an urn
        if (systemId != null && systemId.startsWith(Util.URN)) {
            systemId = Normalizer.decodeURN(systemId);
            if (publicId != null && !publicId.equals(systemId)) {
                systemId = null;
            } else {
                publicId = systemId;
                systemId = null;
            }
        }

        CatalogImpl c = (CatalogImpl)catalog;
        String resolvedSystemId = Util.resolve(c, publicId, systemId);

        if (resolvedSystemId != null) {
            return new InputSource(resolvedSystemId);
        }

        GroupEntry.ResolveType resolveType = ((CatalogImpl) catalog).getResolve();
        switch (resolveType) {
            case IGNORE:
                return new InputSource(new StringReader(""));
            case STRICT:
                CatalogMessages.reportError(CatalogMessages.ERR_NO_MATCH,
                        new Object[]{publicId, systemId});
        }

        //no action, allow the parser to continue
        return null;
    }

}
