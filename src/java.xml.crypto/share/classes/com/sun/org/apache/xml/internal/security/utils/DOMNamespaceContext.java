/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 */
public class DOMNamespaceContext implements NamespaceContext {

    private Map<String, String> namespaceMap = new HashMap<>();

    public DOMNamespaceContext(Node contextNode) {
        addNamespaces(contextNode);
    }

    public String getNamespaceURI(String arg0) {
        return namespaceMap.get(arg0);
    }

    public String getPrefix(String arg0) {
        for (Entry<String, String> entry : namespaceMap.entrySet()) {
            if (entry.getValue().equals(arg0)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Iterator<String> getPrefixes(String arg0) {
        return namespaceMap.keySet().iterator();
    }

    private void addNamespaces(Node element) {
        if (element.getParentNode() != null) {
            addNamespaces(element.getParentNode());
        }
        if (element instanceof Element) {
            Element el = (Element)element;
            NamedNodeMap map = el.getAttributes();
            for (int x = 0; x < map.getLength(); x++) {
                Attr attr = (Attr)map.item(x);
                if ("xmlns".equals(attr.getPrefix())) {
                    namespaceMap.put(attr.getLocalName(), attr.getValue());
                }
            }
        }
    }
}
