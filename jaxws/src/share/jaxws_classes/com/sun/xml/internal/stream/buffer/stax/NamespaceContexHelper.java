/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.stream.buffer.stax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.sun.xml.internal.org.jvnet.staxex.NamespaceContextEx;

/**
 * A helper class for managing the declaration of namespaces.
 * <p>
 * A namespace is declared on a namespace context.
 * Namespace contexts are pushed on and popped off the namespace context stack.
 * <p>
 * A declared namespace will be in scope iff the context that it was declared on
 * has not been popped off the stack.
 * <p>
 * When instantiated the namespace stack consists of the root namespace context,
 * which contains, by default, the "xml" and "xmlns" declarations.
 * Namespaces may be declarations may be declared on the root context.
 * The root context cannot be popped but can be reset to contain just the
 * "xml" and "xmlns" declarations.
 * <p>
 * Implementation note: determining the prefix from a namespace URI
 * (or vice versa) is efficient when there are few namespace
 * declarations i.e. what is considered to be the case for namespace
 * declarations in 'average' XML documents. The look up of a namespace URI
 * given a prefix is performed in O(n) time. The look up of a prefix given
 * a namespace URI is performed in O(2n) time.
 * <p>
 * The implementation does not scale when there are many namespace
 * declarations. TODO: Use a hash map when there are many namespace
 * declarations.
 *
 * @author Paul.Sandoz@Sun.Com
 */
final public class NamespaceContexHelper implements NamespaceContextEx {
    private static int DEFAULT_SIZE = 8;

    // The prefixes of the namespace declarations
    private String[] prefixes = new String[DEFAULT_SIZE];
    // The URIs of the namespace declarations
    private String[] namespaceURIs = new String[DEFAULT_SIZE];
    // Current position to store the next namespace declaration
    private int namespacePosition;

    // The namespace contexts
    private int[] contexts = new int[DEFAULT_SIZE];
    // Current position to store the next namespace context
    private int contextPosition;

    /**
     * Create a new NamespaceContexHelper.
     *
     */
    public NamespaceContexHelper() {
        // The default namespace declarations that are always in scope
        prefixes[0] = "xml";
        namespaceURIs[0] = "http://www.w3.org/XML/1998/namespace";
        prefixes[1] = "xmlns";
        namespaceURIs[1] = "http://www.w3.org/2000/xmlns/";

        namespacePosition = 2;
    }


    // NamespaceContext interface

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

        for (int i = namespacePosition - 1; i >= 0; i--) {
            final String declaredNamespaceURI = namespaceURIs[i];
            if (declaredNamespaceURI == namespaceURI || declaredNamespaceURI.equals(namespaceURI)) {
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

        List<String> l = new ArrayList<String>();

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

    // NamespaceContextEx interface

    public Iterator<NamespaceContextEx.Binding> iterator() {
        if (namespacePosition == 2)
            return Collections.EMPTY_LIST.iterator();

        final List<NamespaceContextEx.Binding> namespaces =
                new ArrayList<NamespaceContextEx.Binding>(namespacePosition);

        NAMESPACE_LOOP: for (int i = namespacePosition - 1; i >= 2; i--) {
            final String declaredPrefix = prefixes[i];

            // Check if prefix is out of scope
            for (int j = i + 1; j < namespacePosition; j++) {
                if (declaredPrefix == prefixes[j])
                    continue NAMESPACE_LOOP;

                namespaces.add(new NamespaceBindingImpl(i));
            }
        }

        return namespaces.iterator();
    }

    final private class NamespaceBindingImpl implements NamespaceContextEx.Binding {
        int index;

        NamespaceBindingImpl(int index) {
            this.index = index;
        }

        public String getPrefix() {
            return prefixes[index];
        }

        public String getNamespaceURI() {
            return namespaceURIs[index];
        }
    }

    /**
     * Declare a default namespace.
     * <p>
     * @param namespaceURI the namespace URI to declare, may be null.
     */
    public void declareDefaultNamespace(String namespaceURI) {
        declareNamespace("", namespaceURI);
    }

    /**
     * Declare a namespace.
     * <p>
     * The namespace will be declared on the current namespace context.
     * <p>
     * The namespace can be removed by popping the current namespace
     * context, or, if the declaration occured in the root context, by
     * reseting the namespace context.
     * <p>
     * A default namespace can be declared by passing <code>""</code> as
     * the value of the prefix parameter.
     * A namespace may be undeclared by passing <code>null</code> as the
     * value of the namespaceURI parameter.
     * <p>
     * @param prefix the namespace prefix to declare, may not be null.
     * @param namespaceURI the namespace URI to declare, may be null.
     * @throws IllegalArgumentException, if the prefix is null.
     */
    public void declareNamespace(String prefix, String namespaceURI) {
        if (prefix == null) throw new IllegalArgumentException();

        prefix = prefix.intern();
        // Ignore the "xml" or "xmlns" declarations
        if (prefix == "xml" || prefix == "xmlns")
            return;

        // Check for undeclaration
        if (namespaceURI != null)
            namespaceURI = namespaceURI.intern();

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

    /**
     * Push a namespace context on the stack.
     */
    public void pushContext() {
        if (contextPosition == contexts.length)
            resizeContexts();

        contexts[contextPosition++] = namespacePosition;
    }

    private void resizeContexts() {
        int[] newContexts = new int[contexts.length * 3 / 2 + 1];
        System.arraycopy(contexts, 0, newContexts, 0, contexts.length);
        contexts = newContexts;
    }

    /**
     * Pop the namespace context off the stack.
     * <p>
     * Namespaces declared within the context (to be popped)
     * will be removed and no longer be in scope.
     */
    public void popContext() {
        if (contextPosition > 0) {
            namespacePosition = contexts[--contextPosition];
        }
    }

    /**
     * Reset namespace contexts.
     * <p>
     * Pop all namespace contexts and reset the root context.
     */
    public void resetContexts() {
        namespacePosition = 2;
    }
}
