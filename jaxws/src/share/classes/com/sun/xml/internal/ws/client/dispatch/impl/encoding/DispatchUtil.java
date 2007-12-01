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

package com.sun.xml.internal.ws.client.dispatch.impl.encoding;

import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.namespace.QName;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class DispatchUtil {

    private Map<String, String> namespacePrefixMap;

    public void clearNPMap() {
        namespacePrefixMap.clear();
    }

    public void populatePrefixes(XMLStreamWriter writer) {
        if (!namespacePrefixMap.isEmpty()) {
            Set<Map.Entry<String, String>> entrys = namespacePrefixMap.entrySet();
            for (Map.Entry<String, String> entry : entrys) {
                try {
                    writer.setPrefix(entry.getValue(), entry.getKey());
                } catch (XMLStreamException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void collectPrefixes(XMLStreamReader reader) {
        if (namespacePrefixMap == null)
            namespacePrefixMap = new HashMap<String, String>();

        int i = reader.getNamespaceCount();
        for (int j = 0; j < i; j++){
            String prefix = reader.getNamespacePrefix(j);
            String namespace = reader.getNamespaceURI(j);
            if (prefix.length() > 0 && namespace != null) {
                namespacePrefixMap.put(namespace, prefix);
            }
        }
    }
}
