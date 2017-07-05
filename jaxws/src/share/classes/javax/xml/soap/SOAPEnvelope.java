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
 * $Id: SOAPEnvelope.java,v 1.7 2006/03/30 00:59:41 ofung Exp $
 * $Revision: 1.7 $
 * $Date: 2006/03/30 00:59:41 $
 */


package javax.xml.soap;


/**
 * The container for the SOAPHeader and SOAPBody portions of a
 * <code>SOAPPart</code> object. By default, a <code>SOAPMessage</code>
 * object is created with a <code>SOAPPart</code> object that has a
 * <code>SOAPEnvelope</code> object. The <code>SOAPEnvelope</code> object
 * by default has an empty <code>SOAPBody</code> object and an empty
 * <code>SOAPHeader</code> object.  The <code>SOAPBody</code> object is
 * required, and the <code>SOAPHeader</code> object, though
 * optional, is used in the majority of cases. If the
 * <code>SOAPHeader</code> object is not needed, it can be deleted,
 * which is shown later.
 * <P>
 * A client can access the <code>SOAPHeader</code> and <code>SOAPBody</code>
 * objects by calling the methods <code>SOAPEnvelope.getHeader</code> and
 * <code>SOAPEnvelope.getBody</code>. The
 * following  lines of code use these two methods after starting with
 * the <code>SOAPMessage</code>
 * object <i>message</i> to get the <code>SOAPPart</code> object <i>sp</i>,
 * which is then used to get the <code>SOAPEnvelope</code> object <i>se</i>.
 *
 * <PRE>
 *     SOAPPart sp = message.getSOAPPart();
 *     SOAPEnvelope se = sp.getEnvelope();
 *     SOAPHeader sh = se.getHeader();
 *     SOAPBody sb = se.getBody();
 * </PRE>
 * <P>
 * It is possible to change the body or header of a <code>SOAPEnvelope</code>
 * object by retrieving the current one, deleting it, and then adding
 * a new body or header. The <code>javax.xml.soap.Node</code> method
 * <code>deleteNode</code> deletes the XML element (node) on which it is
 * called.  For example, the following line of code deletes the
 * <code>SOAPBody</code> object that is retrieved by the method <code>getBody</code>.
 * <PRE>
 *      se.getBody().detachNode();
 * </PRE>
 * To create a <code>SOAPHeader</code> object to replace the one that was removed,
 * a client uses
 * the method <code>SOAPEnvelope.addHeader</code>, which creates a new header and
 * adds it to the <code>SOAPEnvelope</code> object. Similarly, the method
 * <code>addBody</code> creates a new <code>SOAPBody</code> object and adds
 * it to the <code>SOAPEnvelope</code> object. The following code fragment
 * retrieves the current header, removes it, and adds a new one. Then
 * it retrieves the current body, removes it, and adds a new one.
 *
 * <PRE>
 *     SOAPPart sp = message.getSOAPPart();
 *     SOAPEnvelope se = sp.getEnvelope();
 *     se.getHeader().detachNode();
 *     SOAPHeader sh = se.addHeader();
 *     se.getBody().detachNode();
 *     SOAPBody sb = se.addBody();
 * </PRE>
 * It is an error to add a <code>SOAPBody</code> or <code>SOAPHeader</code>
 * object if one already exists.
 * <P>
 * The <code>SOAPEnvelope</code> interface provides three methods for creating
 * <code>Name</code> objects. One method creates <code>Name</code> objects with
 * a local name, a namespace prefix, and a namesapce URI. The second method creates
 * <code>Name</code> objects with a local name and a namespace prefix, and the third
 * creates <code>Name</code> objects with just a local name.  The following line of
 * code, in which <i>se</i> is a <code>SOAPEnvelope</code> object, creates a new
 * <code>Name</code> object with all three.
 * <PRE>
 *     Name name = se.createName("GetLastTradePrice", "WOMBAT",
 *                                "http://www.wombat.org/trader");
 * </PRE>
 */
public interface SOAPEnvelope extends SOAPElement {

    /**
     * Creates a new <code>Name</code> object initialized with the
     * given local name, namespace prefix, and namespace URI.
     * <P>
     * This factory method creates <code>Name</code> objects for use in
     * the SOAP/XML document.
     *
     * @param localName a <code>String</code> giving the local name
     * @param prefix a <code>String</code> giving the prefix of the namespace
     * @param uri a <code>String</code> giving the URI of the namespace
     * @return a <code>Name</code> object initialized with the given
     *         local name, namespace prefix, and namespace URI
     * @throws SOAPException if there is a SOAP error
     */
    public abstract Name createName(String localName, String prefix,
                                    String uri)
        throws SOAPException;

    /**
     * Creates a new <code>Name</code> object initialized with the
     * given local name.
     * <P>
     * This factory method creates <code>Name</code> objects for use in
     * the SOAP/XML document.
     *
     * @param localName a <code>String</code> giving the local name
     * @return a <code>Name</code> object initialized with the given
     *         local name
     * @throws SOAPException if there is a SOAP error
     */
    public abstract Name createName(String localName)
        throws SOAPException;

    /**
     * Returns the <code>SOAPHeader</code> object for
     * this <code>SOAPEnvelope</code> object.
     * <P>
     * A new <code>SOAPMessage</code> object is by default created with a
     * <code>SOAPEnvelope</code> object that contains an empty
     * <code>SOAPHeader</code> object.  As a result, the method
     * <code>getHeader</code> will always return a <code>SOAPHeader</code>
     * object unless the header has been removed and a new one has not
     * been added.
     *
     * @return the <code>SOAPHeader</code> object or <code>null</code> if
     *         there is none
     * @exception SOAPException if there is a problem obtaining the
     *            <code>SOAPHeader</code> object
     */
    public SOAPHeader getHeader() throws SOAPException;

    /**
     * Returns the <code>SOAPBody</code> object associated with this
     * <code>SOAPEnvelope</code> object.
     * <P>
     * A new <code>SOAPMessage</code> object is by default created with a
     * <code>SOAPEnvelope</code> object that contains an empty
     * <code>SOAPBody</code> object.  As a result, the method
     * <code>getBody</code> will always return a <code>SOAPBody</code>
     * object unless the body has been removed and a new one has not
     * been added.
     *
     * @return the <code>SOAPBody</code> object for this
     *         <code>SOAPEnvelope</code> object or <code>null</code>
     *         if there is none
     * @exception SOAPException if there is a problem obtaining the
     *            <code>SOAPBody</code> object
     */
    public SOAPBody getBody() throws SOAPException;
    /**
     * Creates a <code>SOAPHeader</code> object and sets it as the
     * <code>SOAPHeader</code> object for this <code>SOAPEnvelope</code>
     * object.
     * <P>
     * It is illegal to add a header when the envelope already
     * contains a header.  Therefore, this method should be called
     * only after the existing header has been removed.
     *
     * @return the new <code>SOAPHeader</code> object
     *
     * @exception SOAPException if this
     *            <code>SOAPEnvelope</code> object already contains a
     *            valid <code>SOAPHeader</code> object
     */
    public SOAPHeader addHeader() throws SOAPException;
    /**
     * Creates a <code>SOAPBody</code> object and sets it as the
     * <code>SOAPBody</code> object for this <code>SOAPEnvelope</code>
     * object.
     * <P>
     * It is illegal to add a body when the envelope already
     * contains a body. Therefore, this method should be called
     * only after the existing body has been removed.
     *
     * @return the new <code>SOAPBody</code> object
     *
     * @exception SOAPException if this
     *            <code>SOAPEnvelope</code> object already contains a
     *            valid <code>SOAPBody</code> object
     */
    public SOAPBody addBody() throws SOAPException;
}
