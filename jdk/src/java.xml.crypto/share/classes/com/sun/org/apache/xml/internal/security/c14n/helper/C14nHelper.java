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
package com.sun.org.apache.xml.internal.security.c14n.helper;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * Temporary swapped static functions from the normalizer Section
 *
 * @author Christian Geuer-Pollmann
 */
public class C14nHelper {

    /**
     * Constructor C14nHelper
     *
     */
    private C14nHelper() {
        // don't allow instantiation
    }

    /**
     * Method namespaceIsRelative
     *
     * @param namespace
     * @return true if the given namespace is relative.
     */
    public static boolean namespaceIsRelative(Attr namespace) {
        return !namespaceIsAbsolute(namespace);
    }

    /**
     * Method namespaceIsRelative
     *
     * @param namespaceValue
     * @return true if the given namespace is relative.
     */
    public static boolean namespaceIsRelative(String namespaceValue) {
        return !namespaceIsAbsolute(namespaceValue);
    }

    /**
     * Method namespaceIsAbsolute
     *
     * @param namespace
     * @return true if the given namespace is absolute.
     */
    public static boolean namespaceIsAbsolute(Attr namespace) {
        return namespaceIsAbsolute(namespace.getValue());
    }

    /**
     * Method namespaceIsAbsolute
     *
     * @param namespaceValue
     * @return true if the given namespace is absolute.
     */
    public static boolean namespaceIsAbsolute(String namespaceValue) {
        // assume empty namespaces are absolute
        if (namespaceValue.length() == 0) {
            return true;
        }
        return namespaceValue.indexOf(':') > 0;
    }

    /**
     * This method throws an exception if the Attribute value contains
     * a relative URI.
     *
     * @param attr
     * @throws CanonicalizationException
     */
    public static void assertNotRelativeNS(Attr attr) throws CanonicalizationException {
        if (attr == null) {
            return;
        }

        String nodeAttrName = attr.getNodeName();
        boolean definesDefaultNS = nodeAttrName.equals("xmlns");
        boolean definesNonDefaultNS = nodeAttrName.startsWith("xmlns:");

        if ((definesDefaultNS || definesNonDefaultNS) && namespaceIsRelative(attr)) {
            String parentName = attr.getOwnerElement().getTagName();
            String attrValue = attr.getValue();
            Object exArgs[] = { parentName, nodeAttrName, attrValue };

            throw new CanonicalizationException(
                "c14n.Canonicalizer.RelativeNamespace", exArgs
            );
        }
    }

    /**
     * This method throws a CanonicalizationException if the supplied Document
     * is not able to be traversed using a TreeWalker.
     *
     * @param document
     * @throws CanonicalizationException
     */
    public static void checkTraversability(Document document)
        throws CanonicalizationException {
        if (!document.isSupported("Traversal", "2.0")) {
            Object exArgs[] = {document.getImplementation().getClass().getName() };

            throw new CanonicalizationException(
                "c14n.Canonicalizer.TraversalNotSupported", exArgs
            );
        }
    }

    /**
     * This method throws a CanonicalizationException if the supplied Element
     * contains any relative namespaces.
     *
     * @param ctxNode
     * @throws CanonicalizationException
     * @see C14nHelper#assertNotRelativeNS(Attr)
     */
    public static void checkForRelativeNamespace(Element ctxNode)
        throws CanonicalizationException {
        if (ctxNode != null) {
            NamedNodeMap attributes = ctxNode.getAttributes();

            for (int i = 0; i < attributes.getLength(); i++) {
                C14nHelper.assertNotRelativeNS((Attr) attributes.item(i));
            }
        } else {
            throw new CanonicalizationException("Called checkForRelativeNamespace() on null");
        }
    }
}
