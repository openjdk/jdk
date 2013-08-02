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

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**@deprecated*/
@Deprecated
public abstract class ElementCheckerImpl implements ElementChecker {

    public boolean isNamespaceElement(Node el, String type, String ns) {
        if ((el == null) ||
            ns != el.getNamespaceURI() || !el.getLocalName().equals(type)){
            return false;
        }

        return true;
    }

    /** A checker for DOM that interns NS */
    public static class InternedNsChecker extends ElementCheckerImpl {
        public void guaranteeThatElementInCorrectSpace(
            ElementProxy expected, Element actual
        ) throws XMLSecurityException {

            String expectedLocalname = expected.getBaseLocalName();
            String expectedNamespace = expected.getBaseNamespace();

            String localnameIS = actual.getLocalName();
            String namespaceIS = actual.getNamespaceURI();
            if ((expectedNamespace != namespaceIS) ||
                !expectedLocalname.equals(localnameIS)) {
                Object exArgs[] = { namespaceIS + ":" + localnameIS,
                                    expectedNamespace + ":" + expectedLocalname};
                throw new XMLSecurityException("xml.WrongElement", exArgs);
            }
        }
    }

    /** A checker for DOM that interns NS */
    public static class FullChecker extends ElementCheckerImpl {

        public void guaranteeThatElementInCorrectSpace(
            ElementProxy expected, Element actual
        ) throws XMLSecurityException {
            String expectedLocalname = expected.getBaseLocalName();
            String expectedNamespace = expected.getBaseNamespace();

            String localnameIS = actual.getLocalName();
            String namespaceIS = actual.getNamespaceURI();
            if ((!expectedNamespace.equals(namespaceIS)) ||
                !expectedLocalname.equals(localnameIS) ) {
                Object exArgs[] = { namespaceIS + ":" + localnameIS,
                                    expectedNamespace + ":" + expectedLocalname};
                throw new XMLSecurityException("xml.WrongElement", exArgs);
            }
        }
    }

    /** An empty checker if schema checking is used */
    public static class EmptyChecker extends ElementCheckerImpl {
        public void guaranteeThatElementInCorrectSpace(
            ElementProxy expected, Element actual
        ) throws XMLSecurityException {
            // empty
        }
    }
}
