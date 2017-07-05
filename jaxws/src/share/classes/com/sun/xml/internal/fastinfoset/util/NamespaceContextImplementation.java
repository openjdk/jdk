/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.namespace.NamespaceContext;

/**
 *
 * @author Paul.Sandoz@Sun.Com
 */
final public class NamespaceContextImplementation implements NamespaceContext {
    private static int DEFAULT_SIZE = 8;

    private String[] prefixes = new String[DEFAULT_SIZE];
    private String[] namespaceURIs = new String[DEFAULT_SIZE];
    private int namespacePosition;

    private int[] contexts = new int[DEFAULT_SIZE];
    private int contextPosition;

    private int currentContext;

    public NamespaceContextImplementation() {
        prefixes[0] = "xml";
        namespaceURIs[0] = "http://www.w3.org/XML/1998/namespace";
        prefixes[1] = "xmlns";
        namespaceURIs[1] = "http://www.w3.org/2000/xmlns/";

        currentContext = namespacePosition = 2;
    }


    public String getNamespaceURI(String prefix) {
        if (prefix == null) throw new IllegalArgumentException();

        prefix = prefix.intern();

        for (int i = namespacePosition - 1; i >= 0; i--) {
            final String declaredPrefix = prefixes[i];
            if (declaredPrefix == prefix) {
                return namespaceURIs[i];
            }
        }

        return "";
    }

    public String getPrefix(String namespaceURI) {
        if (namespaceURI == null) throw new IllegalArgumentException();

        // namespaceURI = namespaceURI.intern();

        for (int i = namespacePosition - 1; i >= 0; i--) {
            final String declaredNamespaceURI = namespaceURIs[i];
            if (declaredNamespaceURI == namespaceURI || declaredNamespaceURI.equals(namespaceURI)) {
                final String declaredPrefix = prefixes[i];

                // Check if prefix is out of scope
                boolean isOutOfScope = false;
                for (int j = i + 1; j < namespacePosition; j++)
                    if (declaredPrefix == prefixes[j]) {
                        isOutOfScope = true;
                        break;
                    }

                if (!isOutOfScope) {
                    return declaredPrefix;
                }
            }
        }

        return null;
    }

    public String getNonDefaultPrefix(String namespaceURI) {
        if (namespaceURI == null) throw new IllegalArgumentException();

        // namespaceURI = namespaceURI.intern();

        for (int i = namespacePosition - 1; i >= 0; i--) {
            final String declaredNamespaceURI = namespaceURIs[i];
            if ((declaredNamespaceURI == namespaceURI || declaredNamespaceURI.equals(namespaceURI)) &&
                prefixes[i].length() > 0){
                final String declaredPrefix = prefixes[i];

                // Check if prefix is out of scope
                for (++i; i < namespacePosition; i++)
                    if (declaredPrefix == prefixes[i])
                        return null;

                return declaredPrefix;
            }
        }

        return null;
    }

    public Iterator getPrefixes(String namespaceURI) {
        if (namespaceURI == null) throw new IllegalArgumentException();

        // namespaceURI = namespaceURI.intern();

        List l = new ArrayList();

        NAMESPACE_LOOP: for (int i = namespacePosition - 1; i >= 0; i--) {
            final String declaredNamespaceURI = namespaceURIs[i];
            if (declaredNamespaceURI == namespaceURI || declaredNamespaceURI.equals(namespaceURI)) {
                final String declaredPrefix = prefixes[i];

                // Check if prefix is out of scope
                for (int j = i + 1; j < namespacePosition; j++)
                    if (declaredPrefix == prefixes[j])
                        continue NAMESPACE_LOOP;

                l.add(declaredPrefix);
            }
        }

        return l.iterator();
    }


    public String getPrefix(int index) {
        return prefixes[index];
    }

    public String getNamespaceURI(int index) {
        return namespaceURIs[index];
    }

    public int getCurrentContextStartIndex() {
        return currentContext;
    }

    public int getCurrentContextEndIndex() {
        return namespacePosition;
    }

    public boolean isCurrentContextEmpty() {
        return currentContext == namespacePosition;
    }

    public void declarePrefix(String prefix, String namespaceURI) {
        prefix = prefix.intern();
        namespaceURI = namespaceURI.intern();

        // Ignore the "xml" or "xmlns" declarations
        if (prefix == "xml" || prefix == "xmlns")
            return;

        // Replace any previous declaration
        for (int i = currentContext; i < namespacePosition; i++) {
            final String declaredPrefix = prefixes[i];
            if (declaredPrefix == prefix) {
                prefixes[i] = prefix;
                namespaceURIs[i] = namespaceURI;
                return;
            }
        }

        if (namespacePosition == namespaceURIs.length)
            resizeNamespaces();

        // Add new declaration
        prefixes[namespacePosition] = prefix;
        namespaceURIs[namespacePosition++] = namespaceURI;
    }

    private void resizeNamespaces() {
        final int newLength = namespaceURIs.length * 3 / 2 + 1;

        String[] newPrefixes = new String[newLength];
        System.arraycopy(prefixes, 0, newPrefixes, 0, prefixes.length);
        prefixes = newPrefixes;

        String[] newNamespaceURIs = new String[newLength];
        System.arraycopy(namespaceURIs, 0, newNamespaceURIs, 0, namespaceURIs.length);
        namespaceURIs = newNamespaceURIs;
    }

    public void pushContext() {
        if (contextPosition == contexts.length)
            resizeContexts();

        contexts[contextPosition++] = currentContext = namespacePosition;
    }

    private void resizeContexts() {
        int[] newContexts = new int[contexts.length * 3 / 2 + 1];
        System.arraycopy(contexts, 0, newContexts, 0, contexts.length);
        contexts = newContexts;
    }

    public void popContext() {
        if (contextPosition > 0) {
            namespacePosition = currentContext = contexts[--contextPosition];
        }
    }

    public void reset() {
        currentContext = namespacePosition = 2;
    }
}
