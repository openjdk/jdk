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
 */
/*
 * $Id: SOAPHeader.java,v 1.15 2006/03/30 00:59:41 ofung Exp $
 * $Revision: 1.15 $
 * $Date: 2006/03/30 00:59:41 $
 */


package javax.xml.soap;

import java.util.Iterator;

import javax.xml.namespace.QName;

/**
 * A representation of the SOAP header
 * element. A SOAP header element consists of XML data that affects
 * the way the application-specific content is processed by the message
 * provider. For example, transaction semantics, authentication information,
 * and so on, can be specified as the content of a <code>SOAPHeader</code>
 * object.
 * <P>
 * A <code>SOAPEnvelope</code> object contains an empty
 * <code>SOAPHeader</code> object by default. If the <code>SOAPHeader</code>
 * object, which is optional, is not needed, it can be retrieved and deleted
 * with the following line of code. The variable <i>se</i> is a
 * <code>SOAPEnvelope</code> object.
 * <PRE>
 *      se.getHeader().detachNode();
 * </PRE>
 *
 * A <code>SOAPHeader</code> object is created with the <code>SOAPEnvelope</code>
 * method <code>addHeader</code>. This method, which creates a new header and adds it
 * to the envelope, may be called only after the existing header has been removed.
 *
 * <PRE>
 *      se.getHeader().detachNode();
 *      SOAPHeader sh = se.addHeader();
 * </PRE>
 * <P>
 * A <code>SOAPHeader</code> object can have only <code>SOAPHeaderElement</code>
 * objects as its immediate children. The method <code>addHeaderElement</code>
 * creates a new <code>HeaderElement</code> object and adds it to the
 * <code>SOAPHeader</code> object. In the following line of code, the
 * argument to the method <code>addHeaderElement</code> is a <code>Name</code>
 * object that is the name for the new <code>HeaderElement</code> object.
 * <PRE>
 *      SOAPHeaderElement shElement = sh.addHeaderElement(name);
 * </PRE>
 *
 * @see SOAPHeaderElement
 */
public interface SOAPHeader extends SOAPElement {
    /**
     * Creates a new <code>SOAPHeaderElement</code> object initialized with the
     * specified name and adds it to this <code>SOAPHeader</code> object.
     *
     * @param name a <code>Name</code> object with the name of the new
     *        <code>SOAPHeaderElement</code> object
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs
     * @see SOAPHeader#addHeaderElement(javax.xml.namespace.QName)
     */
    public SOAPHeaderElement addHeaderElement(Name name)
        throws SOAPException;

    /**
     * Creates a new <code>SOAPHeaderElement</code> object initialized with the
     * specified qname and adds it to this <code>SOAPHeader</code> object.
     *
     * @param qname a <code>QName</code> object with the qname of the new
     *        <code>SOAPHeaderElement</code> object
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs
     * @see SOAPHeader#addHeaderElement(Name)
     * @since SAAJ 1.3
     */
    public SOAPHeaderElement addHeaderElement(QName qname)
        throws SOAPException;

    /**
     * Returns an <code>Iterator</code> over all the <code>SOAPHeaderElement</code> objects
     * in this <code>SOAPHeader</code> object
     * that have the specified <i>actor</i> and that have a MustUnderstand attribute
     * whose value is equivalent to <code>true</code>.
     * <p>
     * In SOAP 1.2 the <i>env:actor</i> attribute is replaced by the <i>env:role</i>
     * attribute, but with essentially the same semantics.
     *
     * @param actor a <code>String</code> giving the URI of the <code>actor</code> / <code>role</code>
     *        for which to search
     * @return an <code>Iterator</code> object over all the
     *         <code>SOAPHeaderElement</code> objects that contain the specified
     *          <code>actor</code> / <code>role</code> and are marked as MustUnderstand
     * @see #examineHeaderElements
     * @see #extractHeaderElements
     * @see SOAPConstants#URI_SOAP_ACTOR_NEXT
     *
     * @since SAAJ 1.2
     */
    public Iterator examineMustUnderstandHeaderElements(String actor);

    /**
     * Returns an <code>Iterator</code> over all the <code>SOAPHeaderElement</code> objects
     * in this <code>SOAPHeader</code> object
     * that have the specified <i>actor</i>.
     *
     * An <i>actor</i> is a global attribute that indicates the intermediate
     * parties that should process a message before it reaches its ultimate
     * receiver. An actor receives the message and processes it before sending
     * it on to the next actor. The default actor is the ultimate intended
     * recipient for the message, so if no actor attribute is included in a
     * <code>SOAPHeader</code> object, it is sent to the ultimate receiver
     * along with the message body.
     * <p>
     * In SOAP 1.2 the <i>env:actor</i> attribute is replaced by the <i>env:role</i>
     * attribute, but with essentially the same semantics.
     *
     * @param actor a <code>String</code> giving the URI of the <code>actor</code> / <code>role</code>
     *        for which to search
     * @return an <code>Iterator</code> object over all the
     *         <code>SOAPHeaderElement</code> objects that contain the specified
     *          <code>actor</code> / <code>role</code>
     * @see #extractHeaderElements
     * @see SOAPConstants#URI_SOAP_ACTOR_NEXT
     */
    public Iterator examineHeaderElements(String actor);

    /**
     * Returns an <code>Iterator</code> over all the <code>SOAPHeaderElement</code> objects
     * in this <code>SOAPHeader</code> object
     * that have the specified <i>actor</i> and detaches them
     * from this <code>SOAPHeader</code> object.
     * <P>
     * This method allows an actor to process the parts of the
     * <code>SOAPHeader</code> object that apply to it and to remove
     * them before passing the message on to the next actor.
     * <p>
     * In SOAP 1.2 the <i>env:actor</i> attribute is replaced by the <i>env:role</i>
     * attribute, but with essentially the same semantics.
     *
     * @param actor a <code>String</code> giving the URI of the <code>actor</code> / <code>role</code>
     *        for which to search
     * @return an <code>Iterator</code> object over all the
     *         <code>SOAPHeaderElement</code> objects that contain the specified
     *          <code>actor</code> / <code>role</code>
     *
     * @see #examineHeaderElements
     * @see SOAPConstants#URI_SOAP_ACTOR_NEXT
     */
    public Iterator extractHeaderElements(String actor);

    /**
     * Creates a new NotUnderstood <code>SOAPHeaderElement</code> object initialized
     * with the specified name and adds it to this <code>SOAPHeader</code> object.
     * This operation is supported only by SOAP 1.2.
     *
     * @param name a <code>QName</code> object with the name of the
     *        <code>SOAPHeaderElement</code> object that was not understood.
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs.
     * @exception UnsupportedOperationException if this is a SOAP 1.1 Header.
     * @since SAAJ 1.3
     */
    public SOAPHeaderElement addNotUnderstoodHeaderElement(QName name)
        throws SOAPException;

    /**
     * Creates a new Upgrade <code>SOAPHeaderElement</code> object initialized
     * with the specified List of supported SOAP URIs and adds it to this
     * <code>SOAPHeader</code> object.
     * This operation is supported on both SOAP 1.1 and SOAP 1.2 header.
     *
     * @param supportedSOAPURIs an <code>Iterator</code> object with the URIs of SOAP
     *          versions supported.
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs.
     * @since SAAJ 1.3
     */
    public SOAPHeaderElement addUpgradeHeaderElement(Iterator supportedSOAPURIs)
        throws SOAPException;

    /**
     * Creates a new Upgrade <code>SOAPHeaderElement</code> object initialized
     * with the specified array of supported SOAP URIs and adds it to this
     * <code>SOAPHeader</code> object.
     * This operation is supported on both SOAP 1.1 and SOAP 1.2 header.
     *
     * @param  supportedSoapUris an array of the URIs of SOAP versions supported.
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs.
     * @since SAAJ 1.3
     */
    public SOAPHeaderElement addUpgradeHeaderElement(String[] supportedSoapUris)
        throws SOAPException;

    /**
     * Creates a new Upgrade <code>SOAPHeaderElement</code> object initialized
     * with the specified supported SOAP URI and adds it to this
     * <code>SOAPHeader</code> object.
     * This operation is supported on both SOAP 1.1 and SOAP 1.2 header.
     *
     * @param supportedSoapUri the URI of SOAP the version that is supported.
     * @return the new <code>SOAPHeaderElement</code> object that was
     *          inserted into this <code>SOAPHeader</code> object
     * @exception SOAPException if a SOAP error occurs.
     * @since SAAJ 1.3
     */
    public SOAPHeaderElement addUpgradeHeaderElement(String supportedSoapUri)
        throws SOAPException;

    /**
     * Returns an <code>Iterator</code> over all the <code>SOAPHeaderElement</code> objects
     * in this <code>SOAPHeader</code> object.
     *
     * @return an <code>Iterator</code> object over all the
     *          <code>SOAPHeaderElement</code> objects contained by this
     *          <code>SOAPHeader</code>
     * @see #extractAllHeaderElements
     *
     * @since SAAJ 1.2
     */
    public Iterator examineAllHeaderElements();

    /**
     * Returns an <code>Iterator</code> over all the <code>SOAPHeaderElement</code> objects
     * in this <code>SOAPHeader</code> object and detaches them
     * from this <code>SOAPHeader</code> object.
     *
     * @return an <code>Iterator</code> object over all the
     *          <code>SOAPHeaderElement</code> objects contained by this
     *          <code>SOAPHeader</code>
     *
     * @see #examineAllHeaderElements
     *
     * @since SAAJ 1.2
     */
    public Iterator extractAllHeaderElements();

}
