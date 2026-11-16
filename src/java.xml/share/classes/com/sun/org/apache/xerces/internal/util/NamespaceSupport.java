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

package com.sun.org.apache.xerces.internal.util;

import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Namespace support for XML document handlers. This class doesn't
 * perform any error checking and assumes that all strings passed
 * as arguments to methods are unique symbols. The SymbolTable class
 * can be used for this purpose.
 *
 * @author Andy Clark, IBM
 *
 * @LastModified: June 2026
 */
public class NamespaceSupport implements NamespaceContext {

    //
    // Constants
    //

    /** No previous active namespace declaration exists for the prefix. */
    protected static final int NO_PREVIOUS_DECLARATION = -1;

    //
    // Data
    //

    /**
     * Namespace binding information. This array is composed of a
     * series of tuples containing the namespace binding information:
     * &lt;prefix, uri&gt;. The default size can be set to anything
     * as long as it is a power of 2 greater than 1.
     *
     * @see #fNamespaceSize
     * @see #fContext
     */
    protected String[] fNamespace = new String[16 * 2];

    /** The top of the namespace information array. */
    protected int fNamespaceSize;

    /**
     * Previous active declaration for the prefix at the same tuple index in
     * {@link #fNamespace}, or {@link #NO_PREVIOUS_DECLARATION} if none exists.
     * This allows {@link #popContext()} to restore {@link #fNamespaceTable}
     * to the previous active binding without searching ancestor contexts.
     */
    protected int[] fNamespacePrev = new int[16];

    /** Active namespace declaration indexes, keyed by prefix identity. */
    protected IdentityHashMap<String, Integer> fNamespaceTable = new IdentityHashMap<>();

    // NOTE: The constructor depends on the initial context size
    //       being at least 1. -Ac

    /**
     * Context indexes. This array contains indexes into the namespace
     * information array. The index at the current context is the start
     * index of declared namespace bindings and runs to the size of the
     * namespace information array.
     *
     * @see #fNamespaceSize
     */
    protected int[] fContext = new int[8];

    /** The current context. */
    protected int fCurrentContext;

    protected String[] fPrefixes = new String[16];

    //
    // Constructors
    //

    /** Default constructor. */
    public NamespaceSupport() {
    } // <init>()

    /**
     * Constructs a namespace context object and initializes it with
     * the prefixes declared in the specified context.
     */
    public NamespaceSupport(NamespaceContext context) {
        pushContext();
        // copy declaration in the context
        Enumeration<String> prefixes = context.getAllPrefixes();
        while (prefixes.hasMoreElements()){
            String prefix = prefixes.nextElement();
            String uri = context.getURI(prefix);
            declarePrefix(prefix, uri);
        }
    } // <init>(NamespaceContext)


    //
    // Public methods
    //

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#reset()
     */
    public void reset() {

        // reset namespace and context info
        fNamespaceSize = 0;
        fCurrentContext = 0;
        fNamespaceTable.clear();


        // bind "xml" prefix to the XML uri
        fNamespacePrev[0] = NO_PREVIOUS_DECLARATION;
        fNamespace[fNamespaceSize++] = XMLSymbols.PREFIX_XML;
        fNamespace[fNamespaceSize++] = NamespaceContext.XML_URI;
        fNamespaceTable.put(XMLSymbols.PREFIX_XML, 0);
        // bind "xmlns" prefix to the XMLNS uri
        fNamespacePrev[1] = NO_PREVIOUS_DECLARATION;
        fNamespace[fNamespaceSize++] = XMLSymbols.PREFIX_XMLNS;
        fNamespace[fNamespaceSize++] = NamespaceContext.XMLNS_URI;
        fNamespaceTable.put(XMLSymbols.PREFIX_XMLNS, 2);

        fContext[fCurrentContext] = fNamespaceSize;
        //++fCurrentContext;

    } // reset(SymbolTable)


    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#pushContext()
     */
    public void pushContext() {

        // extend the array, if necessary
        if (fCurrentContext + 1 == fContext.length) {
            int[] contextarray = new int[fContext.length * 2];
            System.arraycopy(fContext, 0, contextarray, 0, fContext.length);
            fContext = contextarray;
        }

        // push context
        fContext[++fCurrentContext] = fNamespaceSize;
        //System.out.println("calling push context, current context = " + fCurrentContext);
    } // pushContext()


    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#popContext()
     */
    public void popContext() {
        final int context = fContext[fCurrentContext];
        for (int prefixIndex = fNamespaceSize - 2; prefixIndex >= context; prefixIndex -= 2) {
            final String prefix = fNamespace[prefixIndex];
            final int prev = fNamespacePrev[prefixIndex >> 1];
            if (prev == NO_PREVIOUS_DECLARATION) {
                fNamespaceTable.remove(prefix);
            }
            else {
                fNamespaceTable.put(prefix, prev);
            }
        }
        fNamespaceSize = context;
        fCurrentContext--;
    } // popContext()

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#declarePrefix(String, String)
     */
    public boolean declarePrefix(String prefix, String uri) {
        // ignore "xml" and "xmlns" prefixes
        if (prefix == XMLSymbols.PREFIX_XML || prefix == XMLSymbols.PREFIX_XMLNS) {
            return false;
        }

        final int activeIndex = fNamespaceTable.getOrDefault(prefix, NO_PREVIOUS_DECLARATION);

        // see if prefix already exists in current context
        if (activeIndex >= fContext[fCurrentContext]) {
            // A duplicate declaration in the current context overwrites the
            // existing binding in place, preserving the active table entry.
            fNamespace[activeIndex + 1] = uri;
            return true;
        }

        // resize array, if needed
        if (fNamespaceSize == fNamespace.length) {
            String[] namespacearray = new String[fNamespaceSize * 2];
            System.arraycopy(fNamespace, 0, namespacearray, 0, fNamespaceSize);
            fNamespace = namespacearray;

            int[] namespacePrev = new int[namespacearray.length >> 1];
            System.arraycopy(fNamespacePrev, 0, namespacePrev, 0, fNamespacePrev.length);
            fNamespacePrev = namespacePrev;
        }

        // bind prefix to uri in current context
        fNamespacePrev[fNamespaceSize >> 1] = activeIndex;
        fNamespaceTable.put(prefix, fNamespaceSize);
        fNamespace[fNamespaceSize++] = prefix;
        fNamespace[fNamespaceSize++] = uri;

        return true;

    } // declarePrefix(String,String):boolean

    /**
     * Rebuilds the active namespace table from {@link #fNamespace}. Subclasses
     * that directly replace or truncate namespace tuples must call this method.
     */
    protected void rebuildNamespaceTable() {
        if (fNamespacePrev.length < (fNamespace.length >> 1)) {
            fNamespacePrev = new int[fNamespace.length >> 1];
        }
        fNamespaceTable.clear();
        for (int i = 0; i < fNamespaceSize; i += 2) {
            final String prefix = fNamespace[i];
            fNamespacePrev[i >> 1] = fNamespaceTable.getOrDefault(prefix, NO_PREVIOUS_DECLARATION);
            fNamespaceTable.put(prefix, i);
        }
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getURI(String)
     */
    public String getURI(String prefix) {

        // find prefix in current context
        final int active = fNamespaceTable.getOrDefault(prefix, NO_PREVIOUS_DECLARATION);
        return active != NO_PREVIOUS_DECLARATION ? fNamespace[active + 1] : null;

    } // getURI(String):String


    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getPrefix(String)
     */
    public String getPrefix(String uri) {

        // find uri in current context
        for (int i = fNamespaceSize; i > 0; i -= 2) {
            if (fNamespace[i - 1] == uri) {
                if (getURI(fNamespace[i - 2]) == uri)
                    return fNamespace[i - 2];
            }
        }

        // uri not found
        return null;

    } // getPrefix(String):String

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getDeclaredPrefixCount()
     */
    public int getDeclaredPrefixCount() {
        return (fNamespaceSize - fContext[fCurrentContext]) / 2;
    } // getDeclaredPrefixCount():int

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getDeclaredPrefixAt(int)
     */
    public String getDeclaredPrefixAt(int index) {
        return fNamespace[fContext[fCurrentContext] + index * 2];
    } // getDeclaredPrefixAt(int):String

    public Iterator<String> getPrefixes(){
        int count = collectPrefixes(4, fNamespaceSize);
        return new IteratorPrefixes(fPrefixes, count);
    }//getPrefixes

    /**
     * @see com.sun.org.apache.xerces.internal.xni.NamespaceContext#getAllPrefixes()
     */
    public Enumeration<String> getAllPrefixes() {
        int count = collectPrefixes(4, fNamespaceSize);
        return new Prefixes(fPrefixes, count);
    }

    public List<String> getPrefixes(String uri){
        List<String> prefixList = new ArrayList<>();
        for (int i = fNamespaceSize; i > 0; i -= 2) {
            final int prefixIndex = i - 2;
            if (fNamespace[prefixIndex + 1] == uri) {
                final String prefix = fNamespace[prefixIndex];
                if (fNamespaceTable.getOrDefault(prefix, NO_PREVIOUS_DECLARATION) == prefixIndex) {
                    prefixList.add(prefix);
                }
            }
        }
        return prefixList;
    }

    protected int collectPrefixes(int start, int end) {
        if (fPrefixes.length < (fNamespace.length / 2)) {
            // resize prefix array
            fPrefixes = new String[fNamespace.length / 2];
        }
        int count = 0;
        for (int i = start; i < end; i += 2) {
            if (fNamespacePrev[i >> 1] < start) {
                fPrefixes[count++] = fNamespace[i];
            }
        }
        return count;
    }

    /*
     * non-NamespaceContext methods
     */

    /**
     * Checks whether a binding or unbinding for
     * the given prefix exists in the context.
     *
     * @param prefix The prefix to look up.
     *
     * @return true if the given prefix exists in the context
     */
    public boolean containsPrefix(String prefix) {

        // find prefix in context
        return fNamespaceTable.containsKey(prefix);
    }

    /**
     * Checks whether a binding or unbinding for
     * the given prefix exists in the current context.
     *
     * @param prefix The prefix to look up.
     *
     * @return true if the given prefix exists in the current context
     */
    public boolean containsPrefixInCurrentContext(String prefix) {
        // find prefix in current context
        return fNamespaceTable.getOrDefault(prefix, NO_PREVIOUS_DECLARATION) >= fContext[fCurrentContext];
    }

    protected final class IteratorPrefixes implements Iterator<String>  {
        private String[] prefixes;
        private int counter = 0;
        private int size = 0;

        /**
         * Constructor for Prefixes.
         */
        public IteratorPrefixes(String [] prefixes, int size) {
            this.prefixes = prefixes;
            this.size = size;
        }

        /**
         * @see java.util.Enumeration#hasMoreElements()
         */
        public boolean hasNext() {
            return (counter < size);
        }

        /**
         * @see java.util.Enumeration#nextElement()
         */
        public String next() {
            if (counter< size){
                return fPrefixes[counter++];
            }
            throw new NoSuchElementException("Illegal access to Namespace prefixes enumeration.");
        }

        public String toString(){
            StringBuilder buf = new StringBuilder();
            for (int i=0;i<size;i++){
                buf.append(prefixes[i]);
                buf.append(" ");
            }

            return buf.toString();
        }

        public void remove(){
            throw new UnsupportedOperationException();
        }
    }


    protected final class Prefixes implements Enumeration<String> {
        private String[] prefixes;
        private int counter = 0;
        private int size = 0;

        /**
         * Constructor for Prefixes.
         */
        public Prefixes(String [] prefixes, int size) {
            this.prefixes = prefixes;
            this.size = size;
        }

        /**
         * @see java.util.Enumeration#hasMoreElements()
         */
        public boolean hasMoreElements() {
            return (counter< size);
        }

        /**
         * @see java.util.Enumeration#nextElement()
         */
        public String nextElement() {
            if (counter< size){
                return fPrefixes[counter++];
            }
            throw new NoSuchElementException("Illegal access to Namespace prefixes enumeration.");
        }

        public String toString(){
            StringBuilder buf = new StringBuilder();
            for (int i=0;i<size;i++){
                buf.append(prefixes[i]);
                buf.append(" ");
            }

            return buf.toString();
        }


    }

} // class NamespaceSupport
