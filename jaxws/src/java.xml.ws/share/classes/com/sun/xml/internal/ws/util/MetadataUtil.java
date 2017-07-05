/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.wsdl.SDDocumentResolver;

import java.util.*;

/**
 * WSDL, schema document metadata utility class.
 *
 * @author Jitendra Kotamraju
 */
public class MetadataUtil {

    /**
     * Gets closure of all the referenced documents from the primary document(typically
     * the service WSDL). It traverses the WSDL and schema imports and builds a closure
     * set of documents.
     *
     * @param systemId primary wsdl or the any root document
     * @param resolver used to get SDDocumentImpl for a document
     * @param onlyTopLevelSchemas if true, the imported schemas from a schema would be ignored
     * @return all the documents
     */
    public static Map<String, SDDocument> getMetadataClosure(@NotNull String systemId,
            @NotNull SDDocumentResolver resolver, boolean onlyTopLevelSchemas) {
        Map <String, SDDocument> closureDocs = new HashMap<String, SDDocument>();
        Set<String> remaining = new HashSet<String>();
        remaining.add(systemId);

        while(!remaining.isEmpty()) {
            Iterator<String> it = remaining.iterator();
            String current = it.next();
            remaining.remove(current);

            SDDocument currentDoc = resolver.resolve(current);
            SDDocument old = closureDocs.put(currentDoc.getURL().toExternalForm(), currentDoc);
            assert old == null;

            Set<String> imports =  currentDoc.getImports();
            if (!currentDoc.isSchema() || !onlyTopLevelSchemas) {
                for(String importedDoc : imports) {
                    if (closureDocs.get(importedDoc) == null) {
                        remaining.add(importedDoc);
                    }
                }
            }
        }

        return closureDocs;
    }

}
