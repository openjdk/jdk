/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;

import java.util.Iterator;

import javax.xml.namespace.QName;

/**
 * An object representing an element of a SOAP message that is allowed but not
 * specifically prescribed by a SOAP specification. This interface serves as the
 * base interface for those objects that are specifically prescribed by a SOAP
 * specification.
 * <p>
 * Methods in this interface that are required to return SAAJ specific objects
 * may "silently" replace nodes in the tree as required to successfully return
 * objects of the correct type. See {@link #getChildElements()} and
 * {@link <a HREF="package-summary.html#package_description">javax.xml.soap<a>}
 * for details.
 */
public interface SOAPElement extends Node, org.w3c.dom.Element {

    /**
     * Creates a new <code>SOAPElement</code> object initialized with the
     * given <code>Name</code> object and adds the new element to this
     * <code>SOAPElement</code> object.
     * <P>
     * This method may be deprecated in a future release of SAAJ in favor of
     * addChildElement(javax.xml.namespace.QName)
     *
     * @param name a <code>Name</code> object with the XML name for the
     *        new element
     *
     * @return the new <code>SOAPElement</code> object that was created
     * @exception SOAPException if there is an error in creating the
     *                          <code>SOAPElement</code> object
     * @see SOAPElement#addChildElement(javax.xml.namespace.QName)
     */
    public SOAPElement addChildElement(Name name) throws SOAPException;

    /**
     * Creates a new <code>SOAPElement</code> object initialized with the given
     * <code>QName</code> object and adds the new element to this <code>SOAPElement</code>
     *  object. The  <i>namespace</i>, <i>localname</i> and <i>prefix</i> of the new
     * <code>SOAPElement</code> are all taken  from the <code>qname</code> argument.
     *
     * @param qname a <code>QName</code> object with the XML name for the
     *        new element
     *
     * @return the new <code>SOAPElement</code> object that was created
     * @exception SOAPException if there is an error in creating the
     *                          <code>SOAPElement</code> object
     * @see SOAPElement#addChildElement(Name)
     * @since SAAJ 1.3
     */
    public SOAPElement addChildElement(QName qname) throws SOAPException;

    /**
     * Creates a new <code>SOAPElement</code> object initialized with the
     * specified local name and adds the new element to this
     * <code>SOAPElement</code> object.
     * The new  <code>SOAPElement</code> inherits any in-scope default namespace.
     *
     * @param localName a <code>String</code> giving the local name for
     *          the element
     * @return the new <code>SOAPElement</code> object that was created
     * @exception SOAPException if there is an error in creating the
     *                          <code>SOAPElement</code> object
     */
    public SOAPElement addChildElement(String localName) throws SOAPException;

    /**
     * Creates a new <code>SOAPElement</code> object initialized with the
     * specified local name and prefix and adds the new element to this
     * <code>SOAPElement</code> object.
     *
     * @param localName a <code>String</code> giving the local name for
     *        the new element
     * @param prefix a <code>String</code> giving the namespace prefix for
     *        the new element
     *
     * @return the new <code>SOAPElement</code> object that was created
     * @exception SOAPException if the <code>prefix</code> is not valid in the
     *         context of this <code>SOAPElement</code> or  if there is an error in creating the
     *                          <code>SOAPElement</code> object
     */
    public SOAPElement addChildElement(String localName, String prefix)
        throws SOAPException;

    /**
     * Creates a new <code>SOAPElement</code> object initialized with the
     * specified local name, prefix, and URI and adds the new element to this
     * <code>SOAPElement</code> object.
     *
     * @param localName a <code>String</code> giving the local name for
     *        the new element
     * @param prefix a <code>String</code> giving the namespace prefix for
     *        the new element
     * @param uri a <code>String</code> giving the URI of the namespace
     *        to which the new element belongs
     *
     * @return the new <code>SOAPElement</code> object that was created
     * @exception SOAPException if there is an error in creating the
     *                          <code>SOAPElement</code> object
     */
    public SOAPElement addChildElement(String localName, String prefix,
                                       String uri)
        throws SOAPException;

    /**
     * Add a <code>SOAPElement</code> as a child of this
     * <code>SOAPElement</code> instance. The <code>SOAPElement</code>
     * is expected to be created by a
     * <code>SOAPFactory</code>. Callers should not rely on the
     * element instance being added as is into the XML
     * tree. Implementations could end up copying the content
     * of the <code>SOAPElement</code> passed into an instance of
     * a different <code>SOAPElement</code> implementation. For
     * instance if <code>addChildElement()</code> is called on a
     * <code>SOAPHeader</code>, <code>element</code> will be copied
     * into an instance of a <code>SOAPHeaderElement</code>.
     *
     * <P>The fragment rooted in <code>element</code> is either added
     * as a whole or not at all, if there was an error.
     *
     * <P>The fragment rooted in <code>element</code> cannot contain
     * elements named "Envelope", "Header" or "Body" and in the SOAP
     * namespace. Any namespace prefixes present in the fragment
     * should be fully resolved using appropriate namespace
     * declarations within the fragment itself.
     *
     * @param element the <code>SOAPElement</code> to be added as a
     *                new child
     *
     * @exception SOAPException if there was an error in adding this
     *                          element as a child
     *
     * @return an instance representing the new SOAP element that was
     *         actually added to the tree.
     */
    public SOAPElement addChildElement(SOAPElement element)
        throws SOAPException;

    /**
     * Detaches all children of this <code>SOAPElement</code>.
     * <p>
     * This method is useful for rolling back the construction of partially
     * completed <code>SOAPHeaders</code> and <code>SOAPBodys</code> in
     * preparation for sending a fault when an error condition is detected. It
     * is also useful for recycling portions of a document within a SOAP
     * message.
     *
     * @since SAAJ 1.2
     */
    public abstract void removeContents();

    /**
     * Creates a new <code>Text</code> object initialized with the given
     * <code>String</code> and adds it to this <code>SOAPElement</code> object.
     *
     * @param text a <code>String</code> object with the textual content to be added
     *
     * @return the <code>SOAPElement</code> object into which
     *         the new <code>Text</code> object was inserted
     * @exception SOAPException if there is an error in creating the
     *                    new <code>Text</code> object or if it is not legal to
     *                      attach it as a child to this
     *                      <code>SOAPElement</code>
     */
    public SOAPElement addTextNode(String text) throws SOAPException;

    /**
     * Adds an attribute with the specified name and value to this
     * <code>SOAPElement</code> object.
     *
     * @param name a <code>Name</code> object with the name of the attribute
     * @param value a <code>String</code> giving the value of the attribute
     * @return the <code>SOAPElement</code> object into which the attribute was
     *         inserted
     *
     * @exception SOAPException if there is an error in creating the
     *                          Attribute, or it is invalid to set
                                an attribute with <code>Name</code>
                                 <code>name</code> on this SOAPElement.
     * @see SOAPElement#addAttribute(javax.xml.namespace.QName, String)
     */
    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException;

    /**
     * Adds an attribute with the specified name and value to this
     * <code>SOAPElement</code> object.
     *
     * @param qname a <code>QName</code> object with the name of the attribute
     * @param value a <code>String</code> giving the value of the attribute
     * @return the <code>SOAPElement</code> object into which the attribute was
     *         inserted
     *
     * @exception SOAPException if there is an error in creating the
     *                          Attribute, or it is invalid to set
                                an attribute with <code>QName</code>
                                <code>qname</code> on this SOAPElement.
     * @see SOAPElement#addAttribute(Name, String)
     * @since SAAJ 1.3
     */
    public SOAPElement addAttribute(QName qname, String value)
        throws SOAPException;

    /**
     * Adds a namespace declaration with the specified prefix and URI to this
     * <code>SOAPElement</code> object.
     *
     * @param prefix a <code>String</code> giving the prefix of the namespace
     * @param uri a <code>String</code> giving the uri of the namespace
     * @return the <code>SOAPElement</code> object into which this
     *          namespace declaration was inserted.
     *
     * @exception SOAPException if there is an error in creating the
     *                          namespace
     */
    public SOAPElement addNamespaceDeclaration(String prefix, String uri)
        throws SOAPException;

    /**
     * Returns the value of the attribute with the specified name.
     *
     * @param name a <code>Name</code> object with the name of the attribute
     * @return a <code>String</code> giving the value of the specified
     *         attribute, Null if there is no such attribute
     * @see SOAPElement#getAttributeValue(javax.xml.namespace.QName)
     */
    public String getAttributeValue(Name name);

    /**
     * Returns the value of the attribute with the specified qname.
     *
     * @param qname a <code>QName</code> object with the qname of the attribute
     * @return a <code>String</code> giving the value of the specified
     *         attribute, Null if there is no such attribute
     * @see SOAPElement#getAttributeValue(Name)
     * @since SAAJ 1.3
     */
    public String getAttributeValue(QName qname);

    /**
     * Returns an <code>Iterator</code> over all of the attribute
     * <code>Name</code> objects in this
     * <code>SOAPElement</code> object. The iterator can be used to get
     * the attribute names, which can then be passed to the method
     * <code>getAttributeValue</code> to retrieve the value of each
     * attribute.
     *
     * @see SOAPElement#getAllAttributesAsQNames()
     * @return an iterator over the names of the attributes
     */
    public Iterator getAllAttributes();

    /**
     * Returns an <code>Iterator</code> over all of the attributes
     * in this <code>SOAPElement</code>  as <code>QName</code> objects.
     * The iterator can be used to get the attribute QName, which can then
     * be passed to the method <code>getAttributeValue</code> to retrieve
     * the value of each attribute.
     *
     * @return an iterator over the QNames of the attributes
     * @see SOAPElement#getAllAttributes()
     * @since SAAJ 1.3
     */
    public Iterator getAllAttributesAsQNames();


    /**
     * Returns the URI of the namespace that has the given prefix.
     *
     * @param prefix a <code>String</code> giving the prefix of the namespace
     *        for which to search
     * @return a <code>String</code> with the uri of the namespace that has
     *        the given prefix
     */
    public String getNamespaceURI(String prefix);

    /**
     * Returns an <code>Iterator</code> over the namespace prefix
     * <code>String</code>s declared by this element. The prefixes returned by
     * this iterator can be passed to the method
     * <code>getNamespaceURI</code> to retrieve the URI of each namespace.
     *
     * @return an iterator over the namespace prefixes in this
     *         <code>SOAPElement</code> object
     */
    public Iterator getNamespacePrefixes();

    /**
     * Returns an <code>Iterator</code> over the namespace prefix
     * <code>String</code>s visible to this element. The prefixes returned by
     * this iterator can be passed to the method
     * <code>getNamespaceURI</code> to retrieve the URI of each namespace.
     *
     * @return an iterator over the namespace prefixes are within scope of this
     *         <code>SOAPElement</code> object
     *
     * @since SAAJ 1.2
     */
    public Iterator getVisibleNamespacePrefixes();

    /**
     * Creates a <code>QName</code> whose namespace URI is the one associated
     * with the parameter, <code>prefix</code>, in the context of this
     * <code>SOAPElement</code>. The remaining elements of the new
     * <code>QName</code> are taken directly from the parameters,
     * <code>localName</code> and <code>prefix</code>.
     *
     * @param localName
     *          a <code>String</code> containing the local part of the name.
     * @param prefix
     *          a <code>String</code> containing the prefix for the name.
     *
     * @return a <code>QName</code> with the specified <code>localName</code>
     *          and <code>prefix</code>, and with a namespace that is associated
     *          with the <code>prefix</code> in the context of this
     *          <code>SOAPElement</code>. This namespace will be the same as
     *          the one that would be returned by
     *          <code>{@link #getNamespaceURI(String)}</code> if it were given
     *          <code>prefix</code> as it's parameter.
     *
     * @exception SOAPException if the <code>QName</code> cannot be created.
     *
     * @since SAAJ 1.3
     */
    public QName createQName(String localName, String prefix)
        throws SOAPException;
    /**
     * Returns the name of this <code>SOAPElement</code> object.
     *
     * @return a <code>Name</code> object with the name of this
     *         <code>SOAPElement</code> object
     */
    public Name getElementName();

    /**
     * Returns the qname of this <code>SOAPElement</code> object.
     *
     * @return a <code>QName</code> object with the qname of this
     *         <code>SOAPElement</code> object
     * @see SOAPElement#getElementName()
     * @since SAAJ 1.3
     */
    public QName getElementQName();

    /**
    * Changes the name of this <code>Element</code> to <code>newName</code> if
    * possible. SOAP Defined elements such as SOAPEnvelope, SOAPHeader, SOAPBody
    * etc. cannot have their names changed using this method. Any attempt to do
    * so will result in a  SOAPException being thrown.
    *<P>
    * Callers should not rely on the element instance being renamed as is.
    * Implementations could end up copying the content of the
    * <code>SOAPElement</code> to a renamed instance.
    *
    * @param newName the new name for the <code>Element</code>.
    *
    * @exception SOAPException if changing the name of this <code>Element</code>
    *                          is not allowed.
    * @return The renamed Node
    *
    * @since SAAJ 1.3
    */
   public SOAPElement setElementQName(QName newName) throws SOAPException;

   /**
     * Removes the attribute with the specified name.
     *
     * @param name the <code>Name</code> object with the name of the
     *        attribute to be removed
     * @return <code>true</code> if the attribute was
     *         removed successfully; <code>false</code> if it was not
     * @see SOAPElement#removeAttribute(javax.xml.namespace.QName)
     */
    public boolean removeAttribute(Name name);

    /**
     * Removes the attribute with the specified qname.
     *
     * @param qname the <code>QName</code> object with the qname of the
     *        attribute to be removed
     * @return <code>true</code> if the attribute was
     *         removed successfully; <code>false</code> if it was not
     * @see SOAPElement#removeAttribute(Name)
     * @since SAAJ 1.3
     */
    public boolean removeAttribute(QName qname);

    /**
     * Removes the namespace declaration corresponding to the given prefix.
     *
     * @param prefix a <code>String</code> giving the prefix for which
     *        to search
     * @return <code>true</code> if the namespace declaration was
     *         removed successfully; <code>false</code> if it was not
     */
    public boolean removeNamespaceDeclaration(String prefix);

    /**
     * Returns an <code>Iterator</code> over all the immediate child
     * {@link Node}s of this element. This includes <code>javax.xml.soap.Text</code>
     * objects as well as <code>SOAPElement</code> objects.
     * <p>
     * Calling this method may cause child <code>Element</code>,
     * <code>SOAPElement</code> and <code>org.w3c.dom.Text</code> nodes to be
     * replaced by <code>SOAPElement</code>, <code>SOAPHeaderElement</code>,
     * <code>SOAPBodyElement</code> or <code>javax.xml.soap.Text</code> nodes as
     * appropriate for the type of this parent node. As a result the calling
     * application must treat any existing references to these child nodes that
     * have been obtained through DOM APIs as invalid and either discard them or
     * refresh them with the values returned by this <code>Iterator</code>. This
     * behavior can be avoided by calling the equivalent DOM APIs. See
     * {@link <a HREF="package-summary.html#package_description">javax.xml.soap<a>}
     * for more details.
     *
     * @return an iterator with the content of this <code>SOAPElement</code>
     *         object
     */
    public Iterator getChildElements();

    /**
     * Returns an <code>Iterator</code> over all the immediate child
     * {@link Node}s of this element with the specified name. All of these
     * children will be <code>SOAPElement</code> nodes.
     * <p>
     * Calling this method may cause child <code>Element</code>,
     * <code>SOAPElement</code> and <code>org.w3c.dom.Text</code> nodes to be
     * replaced by <code>SOAPElement</code>, <code>SOAPHeaderElement</code>,
     * <code>SOAPBodyElement</code> or <code>javax.xml.soap.Text</code> nodes as
     * appropriate for the type of this parent node. As a result the calling
     * application must treat any existing references to these child nodes that
     * have been obtained through DOM APIs as invalid and either discard them or
     * refresh them with the values returned by this <code>Iterator</code>. This
     * behavior can be avoided by calling the equivalent DOM APIs. See
     * {@link <a HREF="package-summary.html#package_description">javax.xml.soap<a>}
     * for more details.
     *
     * @param name a <code>Name</code> object with the name of the child
     *        elements to be returned
     *
     * @return an <code>Iterator</code> object over all the elements
     *         in this <code>SOAPElement</code> object with the
     *         specified name
     * @see SOAPElement#getChildElements(javax.xml.namespace.QName)
     */
    public Iterator getChildElements(Name name);

    /**
     * Returns an <code>Iterator</code> over all the immediate child
     * {@link Node}s of this element with the specified qname. All of these
     * children will be <code>SOAPElement</code> nodes.
     * <p>
     * Calling this method may cause child <code>Element</code>,
     * <code>SOAPElement</code> and <code>org.w3c.dom.Text</code> nodes to be
     * replaced by <code>SOAPElement</code>, <code>SOAPHeaderElement</code>,
     * <code>SOAPBodyElement</code> or <code>javax.xml.soap.Text</code> nodes as
     * appropriate for the type of this parent node. As a result the calling
     * application must treat any existing references to these child nodes that
     * have been obtained through DOM APIs as invalid and either discard them or
     * refresh them with the values returned by this <code>Iterator</code>. This
     * behavior can be avoided by calling the equivalent DOM APIs. See
     * {@link <a HREF="package-summary.html#package_description">javax.xml.soap<a>}
     * for more details.
     *
     * @param qname a <code>QName</code> object with the qname of the child
     *        elements to be returned
     *
     * @return an <code>Iterator</code> object over all the elements
     *         in this <code>SOAPElement</code> object with the
     *         specified qname
     * @see SOAPElement#getChildElements(Name)
     * @since SAAJ 1.3
     */
    public Iterator getChildElements(QName qname);

    /**
     * Sets the encoding style for this <code>SOAPElement</code> object
     * to one specified.
     *
     * @param encodingStyle a <code>String</code> giving the encoding style
     *
     * @exception IllegalArgumentException if there was a problem in the
     *            encoding style being set.
     * @exception SOAPException if setting the encodingStyle is invalid for this SOAPElement.
     * @see #getEncodingStyle
     */
    public void setEncodingStyle(String encodingStyle)
        throws SOAPException;
    /**
     * Returns the encoding style for this <code>SOAPElement</code> object.
     *
     * @return a <code>String</code> giving the encoding style
     *
     * @see #setEncodingStyle
     */
    public String getEncodingStyle();
}
