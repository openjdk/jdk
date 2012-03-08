/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.NamespaceContext2;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * Keeps track of in-scope namespace bindings for the marshaller.
 *
 * <p>
 * This class is also used to keep track of tag names for each element
 * for the marshaller (for the performance reason.)
 *
 * @author Kohsuke Kawaguchi
 */
public final class NamespaceContextImpl implements NamespaceContext2 {
    private final XMLSerializer owner;

    private String[] prefixes = new String[4];
    private String[] nsUris = new String[4];
//    /**
//     * True if the correponding namespace declaration is an authentic one that should be printed.
//     *
//     * False if it's a re-discovered in-scope namespace binding available at the ancestor elements
//     * outside this marhsalling. The false value is used to incorporate the in-scope namespace binding
//     * information from {@link #inscopeNamespaceContext}. When false, such a declaration does not need
//     * to be printed, as it's already available in ancestors.
//     */
//    private boolean[] visible = new boolean[4];
//
//    /**
//     * {@link NamespaceContext} that informs this {@link XMLSerializer} about the
//     * in-scope namespace bindings of the ancestor elements outside this marshalling.
//     *
//     * <p>
//     * This is used when the marshaller is marshalling into a subtree that has ancestor
//     * elements created outside the JAXB marshaller.
//     *
//     * Its {@link NamespaceContext#getPrefix(String)} is used to discover in-scope namespace
//     * binding,
//     */
//    private final NamespaceContext inscopeNamespaceContext;

    /**
     * Number of URIs declared. Identifies the valid portion of
     * the {@link #prefixes} and {@link #nsUris} arrays.
     */
    private int size;

    private Element current;

    /**
     * This is the {@link Element} whose prev==null.
     * This element is used to hold the contextual namespace bindings
     * that are assumed to be outside of the document we are marshalling.
     * Specifically the xml prefix and any other user-specified bindings.
     *
     * @see NamespacePrefixMapper#getPreDeclaredNamespaceUris()
     */
    private final Element top;

    /**
     * Never null.
     */
    private NamespacePrefixMapper prefixMapper = defaultNamespacePrefixMapper;

    /**
     * True to allow new URIs to be declared. False otherwise.
     */
    public boolean collectionMode;


    public NamespaceContextImpl(XMLSerializer owner) {
        this.owner = owner;

        current = top = new Element(this,null);
        // register namespace URIs that are implicitly bound
        put(XMLConstants.XML_NS_URI,XMLConstants.XML_NS_PREFIX);
    }

    public void setPrefixMapper( NamespacePrefixMapper mapper ) {
        if(mapper==null)
            mapper = defaultNamespacePrefixMapper;
        this.prefixMapper = mapper;
    }

    public NamespacePrefixMapper getPrefixMapper() {
        return prefixMapper;
    }

    public void reset() {
        current = top;
        size = 1;
        collectionMode = false;
    }

    /**
     * Returns the prefix index to the specified URI.
     * This method allocates a new URI if necessary.
     */
    public int declareNsUri( String uri, String preferedPrefix, boolean requirePrefix ) {
        preferedPrefix = prefixMapper.getPreferredPrefix(uri,preferedPrefix,requirePrefix);

        if(uri.length()==0) {
            for( int i=size-1; i>=0; i-- ) {
                if(nsUris[i].length()==0)
                    return i; // already declared
                if(prefixes[i].length()==0) {
                    // the default prefix is already taken.
                    // move that URI to another prefix, then assign "" to the default prefix.
                    assert current.defaultPrefixIndex==-1 && current.oldDefaultNamespaceUriIndex==-1;

                    String oldUri = nsUris[i];
                    String[] knownURIs = owner.nameList.namespaceURIs;

                    if(current.baseIndex<=i) {
                        // this default prefix is declared in this context. just reassign it

                        nsUris[i] = "";

                        int subst = put(oldUri,null);

                        // update uri->prefix table if necessary
                        for( int j=knownURIs.length-1; j>=0; j-- ) {
                            if(knownURIs[j].equals(oldUri)) {
                                owner.knownUri2prefixIndexMap[j] = subst;
                                break;
                            }
                        }
                        if (current.elementLocalName != null) {
                            current.setTagName(subst, current.elementLocalName, current.getOuterPeer());
                        }
                        return i;
                    } else {
                        // first, if the previous URI assigned to "" is
                        // a "known URI", remember what we've reallocated
                        // so that we can fix it when this context pops.
                        for( int j=knownURIs.length-1; j>=0; j-- ) {
                            if(knownURIs[j].equals(oldUri)) {
                                current.defaultPrefixIndex = i;
                                current.oldDefaultNamespaceUriIndex = j;
                                // assert commented out; too strict/not valid any more
                                // assert owner.knownUri2prefixIndexMap[j]==current.defaultPrefixIndex;
                                // update the table to point to the prefix we'll declare
                                owner.knownUri2prefixIndexMap[j] = size;
                                break;
                            }
                        }
                        if (current.elementLocalName!=null) {
                                                current.setTagName(size, current.elementLocalName, current.getOuterPeer());
                        }

                        put(nsUris[i],null);
                        return put("", "");
                    }
                }
            }

            // "" isn't in use
            return put("", "");
        } else {
            // check for the existing binding
            for( int i=size-1; i>=0; i-- ) {
                String p = prefixes[i];
                if(nsUris[i].equals(uri)) {
                    if (!requirePrefix || p.length()>0)
                        return i;
                    // declared but this URI is bound to empty. Look further
                }
                if(p.equals(preferedPrefix)) {
                    // the suggested prefix is already taken. can't use it
                    preferedPrefix = null;
                }
            }

            if(preferedPrefix==null && requirePrefix)
                // we know we can't bind to "", but we don't have any possible name at hand.
                // generate it here to avoid this namespace to be bound to "".
                preferedPrefix = makeUniquePrefix();

            // haven't been declared. allocate a new one
            // if the preferred prefix is already in use, it should have been set to null by this time
            return put(uri, preferedPrefix);
        }
    }

    public int force(@NotNull String uri, @NotNull String prefix) {
        // check for the existing binding

        for( int i=size-1; i>=0; i-- ) {
            if(prefixes[i].equals(prefix)) {
                if(nsUris[i].equals(uri))
                    return i;   // found duplicate
                else
                    // the prefix is used for another namespace. we need to declare it
                    break;
            }
        }

        return put(uri, prefix);
    }

    /**
     * Puts this new binding into the declared prefixes list
     * without doing any duplicate check.
     *
     * This can be used to forcibly set namespace declarations.
     *
     * <p>
     * Most of the time {@link #declareNamespace(String, String, boolean)} shall be used.
     *
     * @return
     *      the index of this new binding.
     */
    public int put(@NotNull String uri, @Nullable String prefix) {
        if(size==nsUris.length) {
            // reallocate
            String[] u = new String[nsUris.length*2];
            String[] p = new String[prefixes.length*2];
            System.arraycopy(nsUris,0,u,0,nsUris.length);
            System.arraycopy(prefixes,0,p,0,prefixes.length);
            nsUris = u;
            prefixes = p;
        }
        if(prefix==null) {
            if(size==1)
                prefix = "";    // if this is the first user namespace URI we see, use "".
            else {
                // otherwise make up an unique name
                prefix = makeUniquePrefix();
            }
        }
        nsUris[size] = uri;
        prefixes[size] = prefix;

        return size++;
    }

    private String makeUniquePrefix() {
        String prefix;
        prefix = new StringBuilder(5).append("ns").append(size).toString();
        while(getNamespaceURI(prefix)!=null) {
            prefix += '_';  // under a rare circumstance there might be existing 'nsNNN', so rename them
        }
        return prefix;
    }


    public Element getCurrent() {
        return current;
    }

    /**
     * Returns the prefix index of the specified URI.
     * It is an error if the URI is not declared.
     */
    public int getPrefixIndex( String uri ) {
        for( int i=size-1; i>=0; i-- ) {
                if(nsUris[i].equals(uri))
                    return i;
        }
        throw new IllegalStateException();
    }

    /**
     * Gets the prefix from a prefix index.
     *
     * The behavior is undefined if the index is out of range.
     */
    public String getPrefix(int prefixIndex) {
        return prefixes[prefixIndex];
    }

    public String getNamespaceURI(int prefixIndex) {
        return nsUris[prefixIndex];
    }

    /**
     * Gets the namespace URI that is bound to the specified prefix.
     *
     * @return null
     *      if the prefix is unbound.
     */
    public String getNamespaceURI(String prefix) {
        for( int i=size-1; i>=0; i-- )
            if(prefixes[i].equals(prefix))
                return nsUris[i];
        return null;
    }

    /**
     * Returns the prefix of the specified URI,
     * or null if none exists.
     */
    public String getPrefix( String uri ) {
        if(collectionMode) {
            return declareNamespace(uri,null,false);
        } else {
            for( int i=size-1; i>=0; i-- )
                if(nsUris[i].equals(uri))
                    return prefixes[i];
            return null;
        }
    }

    public Iterator<String> getPrefixes(String uri) {
        String prefix = getPrefix(uri);
        if(prefix==null)
            return Collections.<String>emptySet().iterator();
        else
            return Collections.singleton(uri).iterator();
    }

    public String declareNamespace(String namespaceUri, String preferedPrefix, boolean requirePrefix) {
        int idx = declareNsUri(namespaceUri,preferedPrefix,requirePrefix);
        return getPrefix(idx);
    }

    /**
     * Number of total bindings declared.
     */
    public int count() {
        return size;
    }


    /**
     * This model of namespace declarations maintain the following invariants.
     *
     * <ul>
     *  <li>If a non-empty prefix is declared, it will never be reassigned to different namespace URIs.
     *  <li>
     */
    public final class Element {

        public final NamespaceContextImpl context;

        /**
         * {@link Element}s form a doubly-linked list.
         */
        private final Element prev;
        private Element next;

        private int oldDefaultNamespaceUriIndex;
        private int defaultPrefixIndex;


        /**
         * The numbe of prefixes declared by ancestor {@link Element}s.
         */
        private int baseIndex;

        /**
         * The depth of the {@link Element}.
         *
         * This value is equivalent as the result of the following computation.
         *
         * <pre>
         * int depth() {
         *   int i=-1;
         *   for(Element e=this; e!=null;e=e.prev)
         *     i++;
         *   return i;
         * }
         * </pre>
         */
        private final int depth;



        private int elementNamePrefix;
        private String elementLocalName;

        /**
         * Tag name of this element.
         * Either this field is used or the {@link #elementNamePrefix} and {@link #elementLocalName} pair.
         */
        private Name elementName;

        /**
         * Used for the binder. The JAXB object that corresponds to this element.
         */
        private Object outerPeer;
        private Object innerPeer;


        private Element(NamespaceContextImpl context,Element prev) {
            this.context = context;
            this.prev = prev;
            this.depth = (prev==null) ? 0 : prev.depth+1;
        }

        /**
         * Returns true if this {@link Element} represents the root element that
         * we are marshalling.
         */
        public boolean isRootElement() {
            return depth==1;
        }

        public Element push() {
            if(next==null)
                next = new Element(context,this);
            next.onPushed();
            return next;
        }

        public Element pop() {
            if(oldDefaultNamespaceUriIndex>=0) {
                // restore the old default namespace URI binding
                context.owner.knownUri2prefixIndexMap[oldDefaultNamespaceUriIndex] = defaultPrefixIndex;
            }
            context.size = baseIndex;
            context.current = prev;
            // release references to user objects
            outerPeer = innerPeer = null;
            return prev;
        }

        private void onPushed() {
            oldDefaultNamespaceUriIndex = defaultPrefixIndex = -1;
            baseIndex = context.size;
            context.current = this;
        }

        public void setTagName( int prefix, String localName, Object outerPeer ) {
            assert localName!=null;
            this.elementNamePrefix = prefix;
            this.elementLocalName = localName;
            this.elementName = null;
            this.outerPeer = outerPeer;
        }

        public void setTagName( Name tagName, Object outerPeer ) {
            assert tagName!=null;
            this.elementName = tagName;
            this.outerPeer = outerPeer;
        }

        public void startElement(XmlOutput out, Object innerPeer) throws IOException, XMLStreamException {
            this.innerPeer = innerPeer;
            if(elementName!=null) {
                out.beginStartTag(elementName);
            } else {
                out.beginStartTag(elementNamePrefix,elementLocalName);
            }
        }

        public void endElement(XmlOutput out) throws IOException, SAXException, XMLStreamException {
            if(elementName!=null) {
                out.endTag(elementName);
                elementName = null;
            } else {
                out.endTag(elementNamePrefix,elementLocalName);
            }
        }

        /**
         * Gets the number of bindings declared on this element.
         */
        public final int count() {
            return context.size-baseIndex;
        }

        /**
         * Gets the prefix declared in this context.
         *
         * @param idx
         *      between 0 and {@link #count()}
         */
        public final String getPrefix(int idx) {
            return context.prefixes[baseIndex+idx];
        }

        /**
         * Gets the namespace URI declared in this context.
         *
         * @param idx
         *      between 0 and {@link #count()}
         */
        public final String getNsUri(int idx) {
            return context.nsUris[baseIndex+idx];
        }

        public int getBase() {
            return baseIndex;
        }

        public Object getOuterPeer() {
            return outerPeer;
        }

        public Object getInnerPeer() {
            return innerPeer;
        }

        /**
         * Gets the parent {@link Element}.
         */
        public Element getParent() {
            return prev;
        }
    }


    /**
     * Default {@link NamespacePrefixMapper} implementation used when
     * it is not specified by the user.
     */
    private static final NamespacePrefixMapper defaultNamespacePrefixMapper = new NamespacePrefixMapper() {
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if( namespaceUri.equals(WellKnownNamespace.XML_SCHEMA_INSTANCE) )
                return "xsi";
            if( namespaceUri.equals(WellKnownNamespace.XML_SCHEMA) )
                return "xs";
            if( namespaceUri.equals(WellKnownNamespace.XML_MIME_URI) )
                return "xmime";
            return suggestion;
        }
    };
}
