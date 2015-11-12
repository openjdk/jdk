/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * A SAX EntityResolver that uses catalogs to resolve references.
 *
 * @since 9
 */
public interface CatalogResolver extends EntityResolver {

    /**
     * The method searches through the catalog entries in the main and
     * alternative catalogs to attempt to find a match with the specified publicId
     * or systemId.
     * <p>
     * For resolving external entities, system entries will be matched before
     * the public entries.
     * <p>
     * <b>The {@code prefer} attribute</b>: if the {@code prefer} is public,
     * and there is no match found through the system entries, public entries
     * will be considered. If it is not specified, the {@code prefer} is public
     * by default (Note that by the OASIS standard, system entries will always
     * be considered first when the external system identifier is specified.
     * Prefer public means that public entries will be matched when both system
     * and public identifiers are specified. In general therefore, prefer
     * public is recommended.)
     *
     * @param publicId the public identifier of the external entity being
     * referenced, or null if none was supplied
     *
     * @param systemId the system identifier of the external entity being
     * referenced. A system identifier is required on all external entities. XML
     * requires a system identifier on all external entities, so this value is
     * always specified.
     *
     * @return a {@link org.xml.sax.InputSource} object if a mapping is found. If no mapping is
     * found, returns a {@link org.xml.sax.InputSource} object containing an empty
     * {@link java.io.Reader} if the {@code javax.xml.catalog.resolve} property
     * is set to {@code ignore}; returns null if the
     * {@code javax.xml.catalog.resolve} property is set to {@code continue}.
     *
     * @throws CatalogException if no mapping is found and
     * {@code javax.xml.catalog.resolve} is specified as strict
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId);
}
