/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogFeatures.Feature;
import javax.xml.catalog.CatalogManager;
import org.xml.sax.EntityResolver;

/**
 *
 * @author lukas
 */
final class CatalogUtil {

    // Cache CatalogFeatures instance for future usages.
    // Resolve feature is set to "continue" value for backward compatibility.
    private static final CatalogFeatures CATALOG_FEATURES = CatalogFeatures.builder()
                                                    .with(Feature.RESOLVE, "continue")
                                                    .build();

    static EntityResolver getCatalog(EntityResolver entityResolver, File catalogFile, ArrayList<URI> catalogUrls) throws IOException {
        return CatalogManager.catalogResolver(
                CATALOG_FEATURES, catalogUrls.stream().toArray(URI[]::new));
    }
}
