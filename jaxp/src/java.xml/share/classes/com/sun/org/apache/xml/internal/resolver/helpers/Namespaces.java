/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.resolver.helpers;

import org.w3c.dom.*;

/**
 * Static Namespace query methods.
 *
 * <p>This class defines a set of static methods that can be called
 * to analyze the namespace properties of DOM nodes.</p>
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class Namespaces {
    /**
     * Returns the "prefix" part of a QName or the empty string (not
     * null) if the name has no prefix.
     *
     * @param element The QName of an element.
     * @return The prefix part of the element name.
     */
    public static String getPrefix(Element element) {
        String name = element.getTagName();
        String prefix = "";

        final int indexOfColon = name.indexOf(':');
        if (indexOfColon > 0) {
            prefix = name.substring(0, indexOfColon);
        }

        return prefix;
    }

    /**
     * Returns the "localname" part of a QName, which is the whole
     * name if it has no prefix.
     *
     * @param element The QName of an element.
     * @return The local part of a QName.
     */
    public static String getLocalName(Element element) {
        String name = element.getTagName();

        final int indexOfColon = name.indexOf(':');
        if (indexOfColon > 0) {
            name = name.substring(indexOfColon + 1);
        }

        return name;
    }

    /**
     * Returns the namespace URI for the specified prefix at the
     * specified context node.
     *
     * @param node The context node.
     * @param prefix The prefix.
     * @return The namespace URI associated with the prefix, or
     * null if no namespace declaration exists for the prefix.
     */
    public static String getNamespaceURI(Node node, String prefix) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        if (prefix.length() == 0) {
            if (((Element) node).hasAttribute("xmlns")) {
                return ((Element) node).getAttribute("xmlns");
            }
        } else {
            String nsattr = "xmlns:" + prefix;
            if (((Element) node).hasAttribute(nsattr)) {
                return ((Element) node).getAttribute(nsattr);
            }
        }

        return getNamespaceURI(node.getParentNode(), prefix);
    }

    /**
     * Returns the namespace URI for the namespace to which the
     * element belongs.
     *
     * @param element The element.
     * @return The namespace URI associated with the namespace of the
     * element, or null if no namespace declaration exists for it.
     */
    public static String getNamespaceURI(Element element) {
        String prefix = getPrefix(element);
        return getNamespaceURI(element, prefix);
    }
}
