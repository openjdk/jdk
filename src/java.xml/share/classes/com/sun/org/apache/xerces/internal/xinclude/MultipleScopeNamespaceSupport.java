/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
 */
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

package com.sun.org.apache.xerces.internal.xinclude;

import com.sun.org.apache.xerces.internal.util.NamespaceSupport;
import com.sun.org.apache.xerces.internal.util.XMLSymbols;
import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import java.util.Enumeration;

/**
 * This implementation of NamespaceContext has the ability to maintain multiple
 * scopes of namespace/prefix bindings.  This is useful in situations when it is
 * not always appropriate for elements to inherit the namespace bindings of their
 * ancestors (such as included elements in XInclude).
 *
 * When searching for a URI to match a prefix, or a prefix to match a URI, it is
 * searched for in the current context, then the ancestors of the current context,
 * up to the beginning of the current scope.  Other scopes are not searched.
 *
 * @author Peter McCracken, IBM
 *
 * @LastModified: June 2026
 */
public class MultipleScopeNamespaceSupport extends NamespaceSupport {

    protected int[] fScope = new int[8];
    protected int fCurrentScope;

    /**
     *
     */
    public MultipleScopeNamespaceSupport() {
        super();
        fCurrentScope = 0;
        fScope[0] = 0;
    }

    /**
     * @param context
     */
    public MultipleScopeNamespaceSupport(NamespaceContext context) {
        super(context);
        fCurrentScope = 0;
        fScope[0] = 0;
    }

    /* (non-Javadoc)
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getAllPrefixes()
     */
    public Enumeration<String> getAllPrefixes() {
        int count = collectPrefixes(fContext[fScope[fCurrentScope]], fNamespaceSize);
        return new Prefixes(fPrefixes, count);
    }

    public int getScopeForContext(int context) {
        int scope = fCurrentScope;
        while (context < fScope[scope]) {
            scope--;
        }
        return scope;
    }

    /* (non-Javadoc)
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getPrefix(java.lang.String)
     */
    public String getPrefix(String uri) {
        return getPrefix(uri, fNamespaceSize, fContext[fScope[fCurrentScope]]);
    }

    /* (non-Javadoc)
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getURI(java.lang.String)
     */
    public String getURI(String prefix) {
        return getURI(prefix, fNamespaceSize, fContext[fScope[fCurrentScope]]);
    }

    public String getPrefix(String uri, int context) {
        return getPrefix(uri, fContext[context+1], fContext[fScope[getScopeForContext(context)]]);
    }

    public String getURI(String prefix, int context) {
        return getURI(prefix, fContext[context+1], fContext[fScope[getScopeForContext(context)]]);
    }

    public String getPrefix(String uri, int start, int end) {
        // this saves us from having a copy of each of these in fNamespace for each scope
        if (uri == NamespaceContext.XML_URI) {
            return XMLSymbols.PREFIX_XML;
        }
        if (uri == NamespaceContext.XMLNS_URI) {
            return XMLSymbols.PREFIX_XMLNS;
        }

        // find uri in the requested context range within the current scope
        for (int i = start; i > end; i -= 2) {
            if (fNamespace[i - 1] == uri) {
                if (getURI(fNamespace[i - 2], start, end) == uri)
                    return fNamespace[i - 2];
            }
        }

        // uri not found
        return null;
    }

    public String getURI(String prefix, int start, int end) {
        // this saves us from having a copy of each of these in fNamespace for each scope
        if (prefix == XMLSymbols.PREFIX_XML) {
            return NamespaceContext.XML_URI;
        }
        if (prefix == XMLSymbols.PREFIX_XMLNS) {
            return NamespaceContext.XMLNS_URI;
        }

        int prefixIndex = getPrefixIndex(prefix, start, end);
        return prefixIndex != NO_PREVIOUS_DECLARATION
                ? fNamespace[prefixIndex + 1] : null;
    }

    private int getPrefixIndex(String prefix, int start, int end) {
        final Integer active = fNamespaceTable.get(prefix);
        int prefixIndex = active != null ? active : NO_PREVIOUS_DECLARATION;
        while (prefixIndex >= start) {
            prefixIndex = fNamespacePrev[prefixIndex >> 1];
        }
        return prefixIndex >= end ? prefixIndex : NO_PREVIOUS_DECLARATION;
    }

    /**
     * Only resets the current scope -- all namespaces defined in lower scopes
     * remain valid after a call to reset.
     */
    public void reset() {
        fCurrentContext = fScope[fCurrentScope];
        fNamespaceSize = fContext[fCurrentContext];
        rebuildNamespaceTable();
    }

    /**
     * Begins a new scope.  None of the previous namespace bindings will be used,
     * until the new scope is popped with popScope()
     */
    public void pushScope() {
        if (fCurrentScope + 1 == fScope.length) {
            int[] contextarray = new int[fScope.length * 2];
            System.arraycopy(fScope, 0, contextarray, 0, fScope.length);
            fScope = contextarray;
        }
        pushContext();
        fScope[++fCurrentScope] = fCurrentContext;
    }

    /**
     * Pops the current scope.  The namespace bindings from the new current scope
     * are then used for searching for namespaces and prefixes.
     */
    public void popScope() {
        fCurrentContext = fScope[fCurrentScope--];
        popContext();
    }
}
