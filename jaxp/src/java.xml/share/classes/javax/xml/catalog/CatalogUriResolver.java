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

import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;

/**
 * A JAXP URIResolver that uses catalogs to resolve references.
 *
 * @since 9
 */
public interface CatalogUriResolver extends URIResolver {

    /**
     * The method searches through the catalog entries in the main and
     * alternative catalogs to attempt to find a match with the specified URI.
     *
     * @param href an href attribute, which may be relative or absolute
     * @param base The base URI against which the href attribute will be made
     * absolute if the absolute URI is required
     *
     * @return a {@link javax.xml.transform.Source} object if a mapping is found.
     * If no mapping is found, returns an empty {@link javax.xml.transform.Source}
     * object if the {@code javax.xml.catalog.resolve} property is set to
     * {@code ignore};
     * returns a {@link javax.xml.transform.Source} object with the original URI
     * (href, or href resolved with base if base is not null) if the
     * {@code javax.xml.catalog.resolve} property is set to {@code continue}.
     *
     * @throws CatalogException if no mapping is found and
     * {@code javax.xml.catalog.resolve} is specified as strict
     */
    @Override
    public Source resolve(String href, String base);
}
