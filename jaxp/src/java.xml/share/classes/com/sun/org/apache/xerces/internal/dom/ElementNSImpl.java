/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

package com.sun.org.apache.xerces.internal.dom;

import com.sun.org.apache.xerces.internal.xs.XSSimpleTypeDefinition;
import com.sun.org.apache.xerces.internal.xs.XSTypeDefinition;
import com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl;
import com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl;
import com.sun.org.apache.xerces.internal.util.URI;
import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;



/**
 * ElementNSImpl inherits from ElementImpl and adds namespace support.
 * <P>
 * The qualified name is the node name, and we store localName which is also
 * used in all queries. On the other hand we recompute the prefix when
 * necessary.
 *
 * @xerces.internal
 *
 * @author Elena litani, IBM
 * @author Neeraj Bajaj, Sun Microsystems
 */
public class ElementNSImpl
    extends ElementImpl {

    //
    // Constants
    //

    /** Serialization version. */
    static final long serialVersionUID = -9142310625494392642L;
    static final String xmlURI = "http://www.w3.org/XML/1998/namespace";

    //
    // Data
    //

    /** DOM2: Namespace URI. */
    protected String namespaceURI;

    /** DOM2: localName. */
    protected String localName;

    /** DOM3: type information */
    // REVISIT: we are losing the type information in DOM during serialization
    transient XSTypeDefinition type;

    protected ElementNSImpl() {
        super();
    }
    /**
     * DOM2: Constructor for Namespace implementation.
     */
    protected ElementNSImpl(CoreDocumentImpl ownerDocument,
                            String namespaceURI,
                            String qualifiedName)
        throws DOMException
    {
        super(ownerDocument, qualifiedName);
        setName(namespaceURI, qualifiedName);
    }

        private void setName(String namespaceURI, String qname) {

                String prefix;
                // DOM Level 3: namespace URI is never empty string.
                this.namespaceURI = namespaceURI;
                if (namespaceURI != null) {
            //convert the empty string to 'null'
                        this.namespaceURI =     (namespaceURI.length() == 0) ? null : namespaceURI;
                }

        int colon1, colon2 ;

        //NAMESPACE_ERR:
        //1. if the qualified name is 'null' it is malformed.
        //2. or if the qualifiedName is null and the namespaceURI is different from null,
        // We dont need to check for namespaceURI != null, if qualified name is null throw DOMException.
        if(qname == null){
                                String msg =
                                        DOMMessageFormatter.formatMessage(
                                                DOMMessageFormatter.DOM_DOMAIN,
                                                "NAMESPACE_ERR",
                                                null);
                                throw new DOMException(DOMException.NAMESPACE_ERR, msg);
        }
        else{
                    colon1 = qname.indexOf(':');
                    colon2 = qname.lastIndexOf(':');
        }

                ownerDocument.checkNamespaceWF(qname, colon1, colon2);
                if (colon1 < 0) {
                        // there is no prefix
                        localName = qname;
                        if (ownerDocument.errorChecking) {
                            ownerDocument.checkQName(null, localName);
                            if (qname.equals("xmlns")
                                && (namespaceURI == null
                                || !namespaceURI.equals(NamespaceContext.XMLNS_URI))
                                || (namespaceURI!=null && namespaceURI.equals(NamespaceContext.XMLNS_URI)
                                && !qname.equals("xmlns"))) {
                                String msg =
                                    DOMMessageFormatter.formatMessage(
                                            DOMMessageFormatter.DOM_DOMAIN,
                                            "NAMESPACE_ERR",
                                            null);
                                throw new DOMException(DOMException.NAMESPACE_ERR, msg);
                            }
                        }
                }//there is a prefix
                else {
                    prefix = qname.substring(0, colon1);
                    localName = qname.substring(colon2 + 1);

                    //NAMESPACE_ERR:
                    //1. if the qualifiedName has a prefix and the namespaceURI is null,

                    //2. or if the qualifiedName has a prefix that is "xml" and the namespaceURI
                    //is different from " http://www.w3.org/XML/1998/namespace"

                    if (ownerDocument.errorChecking) {
                        if( namespaceURI == null || ( prefix.equals("xml") && !namespaceURI.equals(NamespaceContext.XML_URI) )){
                            String msg =
                                DOMMessageFormatter.formatMessage(
                                        DOMMessageFormatter.DOM_DOMAIN,
                                        "NAMESPACE_ERR",
                                        null);
                            throw new DOMException(DOMException.NAMESPACE_ERR, msg);
                        }

                        ownerDocument.checkQName(prefix, localName);
                        ownerDocument.checkDOMNSErr(prefix, namespaceURI);
                    }
                }
        }

    // when local name is known
    protected ElementNSImpl(CoreDocumentImpl ownerDocument,
                            String namespaceURI, String qualifiedName,
                            String localName)
        throws DOMException
    {
        super(ownerDocument, qualifiedName);

        this.localName = localName;
        this.namespaceURI = namespaceURI;
    }

    // for DeferredElementImpl
    protected ElementNSImpl(CoreDocumentImpl ownerDocument,
                            String value) {
        super(ownerDocument, value);
    }

    // Support for DOM Level 3 renameNode method.
    // Note: This only deals with part of the pb. CoreDocumentImpl
    // does all the work.
    void rename(String namespaceURI, String qualifiedName)
    {
        if (needsSyncData()) {
            synchronizeData();
        }
                this.name = qualifiedName;
        setName(namespaceURI, qualifiedName);
        reconcileDefaultAttributes();
    }

    /**
     * NON-DOM: resets this node and sets specified values for the node
     *
     * @param ownerDocument
     * @param namespaceURI
     * @param qualifiedName
     * @param localName
     */
    protected void setValues (CoreDocumentImpl ownerDocument,
                            String namespaceURI, String qualifiedName,
                            String localName){

        // remove children first
        firstChild = null;
        previousSibling = null;
        nextSibling = null;
        fNodeListCache = null;

        // set owner document
        attributes = null;
        super.flags = 0;
        setOwnerDocument(ownerDocument);

        // synchronizeData will initialize attributes
        needsSyncData(true);
        super.name = qualifiedName;
        this.localName = localName;
        this.namespaceURI = namespaceURI;

    }

    //
    // Node methods
    //



    //
    //DOM2: Namespace methods.
    //

    /**
     * Introduced in DOM Level 2. <p>
     *
     * The namespace URI of this node, or null if it is unspecified.<p>
     *
     * This is not a computed value that is the result of a namespace lookup based on
     * an examination of the namespace declarations in scope. It is merely the
     * namespace URI given at creation time.<p>
     *
     * For nodes created with a DOM Level 1 method, such as createElement
     * from the Document interface, this is null.
     * @since WD-DOM-Level-2-19990923
     */
    public String getNamespaceURI()
    {
        if (needsSyncData()) {
            synchronizeData();
        }
        return namespaceURI;
    }

    /**
     * Introduced in DOM Level 2. <p>
     *
     * The namespace prefix of this node, or null if it is unspecified. <p>
     *
     * For nodes created with a DOM Level 1 method, such as createElement
     * from the Document interface, this is null. <p>
     *
     * @since WD-DOM-Level-2-19990923
     */
    public String getPrefix()
    {

        if (needsSyncData()) {
            synchronizeData();
        }
        int index = name.indexOf(':');
        return index < 0 ? null : name.substring(0, index);
    }

    /**
     * Introduced in DOM Level 2. <p>
     *
     * Note that setting this attribute changes the nodeName attribute, which holds the
     * qualified name, as well as the tagName and name attributes of the Element
     * and Attr interfaces, when applicable.<p>
     *
     * @param prefix The namespace prefix of this node, or null(empty string) if it is unspecified.
     *
     * @exception INVALID_CHARACTER_ERR
     *                   Raised if the specified
     *                   prefix contains an invalid character.
     * @exception DOMException
     * @since WD-DOM-Level-2-19990923
     */
    public void setPrefix(String prefix)
        throws DOMException
    {
        if (needsSyncData()) {
            synchronizeData();
        }
        if (ownerDocument.errorChecking) {
            if (isReadOnly()) {
                String msg = DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NO_MODIFICATION_ALLOWED_ERR", null);
                throw new DOMException(
                                     DOMException.NO_MODIFICATION_ALLOWED_ERR,
                                     msg);
            }
            if (prefix != null && prefix.length() != 0) {
                if (!CoreDocumentImpl.isXMLName(prefix,ownerDocument.isXML11Version())) {
                    String msg = DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "INVALID_CHARACTER_ERR", null);
                    throw new DOMException(DOMException.INVALID_CHARACTER_ERR, msg);
                }
                if (namespaceURI == null || prefix.indexOf(':') >=0) {
                    String msg = DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NAMESPACE_ERR", null);
                    throw new DOMException(DOMException.NAMESPACE_ERR, msg);
                } else if (prefix.equals("xml")) {
                     if (!namespaceURI.equals(xmlURI)) {
                         String msg = DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN, "NAMESPACE_ERR", null);
                         throw new DOMException(DOMException.NAMESPACE_ERR, msg);
                     }
                }
            }

        }
        // update node name with new qualifiedName
        if (prefix !=null && prefix.length() != 0) {
            name = prefix + ":" + localName;
        }
        else {
            name = localName;
        }
    }

    /**
     * Introduced in DOM Level 2. <p>
     *
     * Returns the local part of the qualified name of this node.
     * @since WD-DOM-Level-2-19990923
     */
    public String getLocalName()
    {
        if (needsSyncData()) {
            synchronizeData();
        }
        return localName;
    }


   /**
     * DOM Level 3 WD - Experimental.
     * Retrieve baseURI
     */
    public String getBaseURI() {

        if (needsSyncData()) {
            synchronizeData();
        }
        // Absolute base URI is computed according to XML Base (http://www.w3.org/TR/xmlbase/#granularity)

        // 1.  the base URI specified by an xml:base attribute on the element, if one exists

        if (attributes != null) {
            Attr attrNode = (Attr)attributes.getNamedItemNS("http://www.w3.org/XML/1998/namespace", "base");
            if (attrNode != null) {
                String uri =  attrNode.getNodeValue();
                if (uri.length() != 0 ) {// attribute value is always empty string
                    try {
                        uri = new URI(uri).toString();
                    }
                    catch (com.sun.org.apache.xerces.internal.util.URI.MalformedURIException e) {
                        // This may be a relative URI.

                        // Start from the base URI of the parent, or if this node has no parent, the owner node.
                        NodeImpl parentOrOwner = (parentNode() != null) ? parentNode() : ownerNode;

                        // Make any parentURI into a URI object to use with the URI(URI, String) constructor.
                        String parentBaseURI = (parentOrOwner != null) ? parentOrOwner.getBaseURI() : null;

                        if (parentBaseURI != null) {
                            try {
                                uri = new URI(new URI(parentBaseURI), uri).toString();
                            }
                            catch (com.sun.org.apache.xerces.internal.util.URI.MalformedURIException ex){
                                // This should never happen: parent should have checked the URI and returned null if invalid.
                                return null;
                            }
                            return uri;
                        }
                        // REVISIT: what should happen in this case?
                        return null;
                    }
                    return uri;
                }
            }
        }

        //2.the base URI of the element's parent element within the document or external entity,
        //if one exists
        String parentElementBaseURI = (this.parentNode() != null) ? this.parentNode().getBaseURI() : null ;
        //base URI of parent element is not null
        if(parentElementBaseURI != null){
            try {
                //return valid absolute base URI
               return new URI(parentElementBaseURI).toString();
            }
            catch (com.sun.org.apache.xerces.internal.util.URI.MalformedURIException e){
                // REVISIT: what should happen in this case?
                return null;
            }
        }
        //3. the base URI of the document entity or external entity containing the element

        String baseURI = (this.ownerNode != null) ? this.ownerNode.getBaseURI() : null ;

        if(baseURI != null){
            try {
                //return valid absolute base URI
               return new URI(baseURI).toString();
            }
            catch (com.sun.org.apache.xerces.internal.util.URI.MalformedURIException e){
                // REVISIT: what should happen in this case?
                return null;
            }
        }

        return null;

    }


    /**
     * @see org.w3c.dom.TypeInfo#getTypeName()
     */
    public String getTypeName() {
        if (type !=null){
            if (type instanceof XSSimpleTypeDecl) {
                return ((XSSimpleTypeDecl) type).getTypeName();
            } else if (type instanceof XSComplexTypeDecl) {
                return ((XSComplexTypeDecl) type).getTypeName();
            }
        }
        return null;
    }

    /**
     * @see org.w3c.dom.TypeInfo#getTypeNamespace()
     */
    public String getTypeNamespace() {
        if (type !=null){
            return type.getNamespace();
        }
        return null;
    }

    /**
     * Introduced in DOM Level 2. <p>
     * Checks if a type is derived from another by restriction. See:
     * http://www.w3.org/TR/DOM-Level-3-Core/core.html#TypeInfo-isDerivedFrom
     *
     * @param ancestorNS
     *        The namspace of the ancestor type declaration
     * @param ancestorName
     *        The name of the ancestor type declaration
     * @param type
     *        The reference type definition
     *
     * @return boolean True if the type is derived by restriciton for the
     *         reference type
     */
    public boolean isDerivedFrom(String typeNamespaceArg, String typeNameArg,
            int derivationMethod) {
        if(needsSyncData()) {
            synchronizeData();
        }
        if (type != null) {
            if (type instanceof XSSimpleTypeDecl) {
                return ((XSSimpleTypeDecl) type).isDOMDerivedFrom(
                        typeNamespaceArg, typeNameArg, derivationMethod);
            } else if (type instanceof XSComplexTypeDecl) {
                return ((XSComplexTypeDecl) type).isDOMDerivedFrom(
                        typeNamespaceArg, typeNameArg, derivationMethod);
            }
        }
        return false;
    }

    /**
     * NON-DOM: setting type used by the DOM parser
     * @see NodeImpl#setReadOnly
     */
    public void setType(XSTypeDefinition type) {
        this.type = type;
    }
}
