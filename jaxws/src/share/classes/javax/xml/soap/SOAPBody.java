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
 * $Id: SOAPBody.java,v 1.17 2005/06/22 10:24:11 vj135062 Exp $
 * $Revision: 1.17 $
 * $Date: 2005/06/22 10:24:11 $
 */


package javax.xml.soap;

import java.util.Locale;

import org.w3c.dom.Document;

import javax.xml.namespace.QName;

/**
 * An object that represents the contents of the SOAP body
 * element in a SOAP message. A SOAP body element consists of XML data
 * that affects the way the application-specific content is processed.
 * <P>
 * A <code>SOAPBody</code> object contains <code>SOAPBodyElement</code>
 * objects, which have the content for the SOAP body.
 * A <code>SOAPFault</code> object, which carries status and/or
 * error information, is an example of a <code>SOAPBodyElement</code> object.
 *
 * @see SOAPFault
 */
public interface SOAPBody extends SOAPElement {

    /**
     * Creates a new <code>SOAPFault</code> object and adds it to
     * this <code>SOAPBody</code> object. The new <code>SOAPFault</code> will
     * have default values set for the mandatory child elements. The type of
     * the <code>SOAPFault</code> will be a SOAP 1.1 or a SOAP 1.2 <code>SOAPFault</code>
     * depending on the <code>protocol</code> specified while creating the
     * <code>MessageFactory</code> instance.
     * <p>
     * A <code>SOAPBody</code> may contain at most one <code>SOAPFault</code>
     * child element.
     *
     * @return the new <code>SOAPFault</code> object
     * @exception SOAPException if there is a SOAP error
     */
    public SOAPFault addFault() throws SOAPException;


    /**
     * Creates a new <code>SOAPFault</code> object and adds it to
     * this <code>SOAPBody</code> object. The type of the
     * <code>SOAPFault</code> will be a SOAP 1.1  or a SOAP 1.2
     * <code>SOAPFault</code> depending on the <code>protocol</code>
     * specified while creating the <code>MessageFactory</code> instance.
     * <p>
     * For SOAP 1.2 the <code>faultCode</code> parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the <code>faultString</code> parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the <code>faultCode</code> parameter is the value of the <code>faultcode</code>
     * element and the <code>faultString</code> parameter is the value of the <code>faultstring</code>
     * element.
     * <p>
     * A <code>SOAPBody</code> may contain at most one <code>SOAPFault</code>
     * child element.
     *
     * @param faultCode a <code>Name</code> object giving the fault
     *         code to be set; must be one of the fault codes defined in the Version
     *         of SOAP specification in use
     * @param faultString a <code>String</code> giving an explanation of
     *         the fault
     * @param locale a {@link java.util.Locale} object indicating
     *         the native language of the <code>faultString</code>
     * @return the new <code>SOAPFault</code> object
     * @exception SOAPException if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @since SAAJ 1.2
     */
    public SOAPFault addFault(Name faultCode, String faultString, Locale locale) throws SOAPException;

    /**
     * Creates a new <code>SOAPFault</code> object and adds it to this
     * <code>SOAPBody</code> object. The type of the <code>SOAPFault</code>
     * will be a SOAP 1.1 or a SOAP 1.2 <code>SOAPFault</code> depending on
     * the <code>protocol</code> specified while creating the <code>MessageFactory</code>
     * instance.
     * <p>
     * For SOAP 1.2 the <code>faultCode</code> parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the <code>faultString</code> parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the <code>faultCode</code> parameter is the value of the <code>faultcode</code>
     * element and the <code>faultString</code> parameter is the value of the <code>faultstring</code>
     * element.
     * <p>
     * A <code>SOAPBody</code> may contain at most one <code>SOAPFault</code>
     * child element.
     *
     * @param faultCode
     *            a <code>QName</code> object giving the fault code to be
     *            set; must be one of the fault codes defined in the version
     *            of SOAP specification in use.
     * @param faultString
     *            a <code>String</code> giving an explanation of the fault
     * @param locale
     *            a {@link java.util.Locale Locale} object indicating the
     *            native language of the <code>faultString</code>
     * @return the new <code>SOAPFault</code> object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @see SOAPBody#addFault(Name faultCode, String faultString, Locale locale)
     *
     * @since SAAJ 1.3
     */
    public SOAPFault addFault(QName faultCode, String faultString, Locale locale)
        throws SOAPException;

    /**
     * Creates a new  <code>SOAPFault</code> object and adds it to this
     * <code>SOAPBody</code> object. The type of the <code>SOAPFault</code>
     * will be a SOAP 1.1 or a SOAP 1.2 <code>SOAPFault</code> depending on
     * the <code>protocol</code> specified while creating the <code>MessageFactory</code>
     * instance.
     * <p>
     * For SOAP 1.2 the <code>faultCode</code> parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the <code>faultString</code> parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the <code>faultCode</code> parameter is the value of the <i>faultcode</i>
     * element and the <code>faultString</code> parameter is the value of the <i>faultstring</i>
     * element.
     * <p>
     * In case of a SOAP 1.2 fault, the default value for the mandatory <code>xml:lang</code>
     * attribute on the <i>Fault/Reason/Text</i> element will be set to
     * <code>java.util.Locale.getDefault()</code>
     * <p>
     * A <code>SOAPBody</code> may contain at most one <code>SOAPFault</code>
     * child element.
     *
     * @param faultCode
     *            a <code>Name</code> object giving the fault code to be set;
     *            must be one of the fault codes defined in the version of SOAP
     *            specification in use
     * @param faultString
     *            a <code>String</code> giving an explanation of the fault
     * @return the new <code>SOAPFault</code> object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @since SAAJ 1.2
     */
    public SOAPFault addFault(Name faultCode, String faultString)
        throws SOAPException;

    /**
     * Creates a new <code>SOAPFault</code> object and adds it to this <code>SOAPBody</code>
     * object. The type of the <code>SOAPFault</code>
     * will be a SOAP 1.1 or a SOAP 1.2 <code>SOAPFault</code> depending on
     * the <code>protocol</code> specified while creating the <code>MessageFactory</code>
     * instance.
     * <p>
     * For SOAP 1.2 the <code>faultCode</code> parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the <code>faultString</code> parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the <code>faultCode</code> parameter is the value of the <i>faultcode</i>
     * element and the <code>faultString</code> parameter is the value of the <i>faultstring</i>
     * element.
     * <p>
     * In case of a SOAP 1.2 fault, the default value for the mandatory <code>xml:lang</code>
     * attribute on the <i>Fault/Reason/Text</i> element will be set to
     * <code>java.util.Locale.getDefault()</code>
     * <p>
     * A <code>SOAPBody</code> may contain at most one <code>SOAPFault</code>
     * child element
     *
     * @param faultCode
     *            a <code>QName</code> object giving the fault code to be
     *            set; must be one of the fault codes defined in the version
     *            of  SOAP specification in use
     * @param faultString
     *            a <code>String</code> giving an explanation of the fault
     * @return the new <code>SOAPFault</code> object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @see SOAPBody#addFault(Name faultCode, String faultString)
     * @since SAAJ 1.3
     */
    public SOAPFault addFault(QName faultCode, String faultString)
        throws SOAPException;

    /**
     * Indicates whether a <code>SOAPFault</code> object exists in this
     * <code>SOAPBody</code> object.
     *
     * @return <code>true</code> if a <code>SOAPFault</code> object exists
     *         in this <code>SOAPBody</code> object; <code>false</code>
     *         otherwise
     */
    public boolean hasFault();

    /**
     * Returns the <code>SOAPFault</code> object in this <code>SOAPBody</code>
     * object.
     *
     * @return the <code>SOAPFault</code> object in this <code>SOAPBody</code>
     *         object if present, null otherwise.
     */
    public SOAPFault getFault();

    /**
     * Creates a new <code>SOAPBodyElement</code> object with the specified
     * name and adds it to this <code>SOAPBody</code> object.
     *
     * @param name
     *            a <code>Name</code> object with the name for the new <code>SOAPBodyElement</code>
     *            object
     * @return the new <code>SOAPBodyElement</code> object
     * @exception SOAPException
     *                if a SOAP error occurs
     * @see SOAPBody#addBodyElement(javax.xml.namespace.QName)
     */
    public SOAPBodyElement addBodyElement(Name name) throws SOAPException;


    /**
     * Creates a new <code>SOAPBodyElement</code> object with the specified
     * QName and adds it to this <code>SOAPBody</code> object.
     *
     * @param qname
     *            a <code>QName</code> object with the qname for the new
     *            <code>SOAPBodyElement</code> object
     * @return the new <code>SOAPBodyElement</code> object
     * @exception SOAPException
     *                if a SOAP error occurs
     * @see SOAPBody#addBodyElement(Name)
     * @since SAAJ 1.3
     */
    public SOAPBodyElement addBodyElement(QName qname) throws SOAPException;

    /**
     * Adds the root node of the DOM <code>{@link org.w3c.dom.Document}</code>
     * to this <code>SOAPBody</code> object.
     * <p>
     * Calling this method invalidates the <code>document</code> parameter.
     * The client application should discard all references to this <code>Document</code>
     * and its contents upon calling <code>addDocument</code>. The behavior
     * of an application that continues to use such references is undefined.
     *
     * @param document
     *            the <code>Document</code> object whose root node will be
     *            added to this <code>SOAPBody</code>.
     * @return the <code>SOAPBodyElement</code> that represents the root node
     *         that was added.
     * @exception SOAPException
     *                if the <code>Document</code> cannot be added
     * @since SAAJ 1.2
     */
    public SOAPBodyElement addDocument(org.w3c.dom.Document document)
        throws SOAPException;

    /**
     * Creates a new DOM <code>{@link org.w3c.dom.Document}</code> and sets
     * the first child of this <code>SOAPBody</code> as it's document
     * element. The child <code>SOAPElement</code> is removed as part of the
     * process.
     *
     * @return the <code>{@link org.w3c.dom.Document}</code> representation
     *         of the <code>SOAPBody</code> content.
     *
     * @exception SOAPException
     *                if there is not exactly one child <code>SOAPElement</code> of the <code>
     *              <code>SOAPBody</code>.
     *
     * @since SAAJ 1.3
     */
    public org.w3c.dom.Document extractContentAsDocument()
        throws SOAPException;
}
