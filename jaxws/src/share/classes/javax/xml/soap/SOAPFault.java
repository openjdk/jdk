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
 * $Id: SOAPFault.java,v 1.20 2006/02/14 04:40:57 vj135062 Exp $
 * $Revision: 1.20 $
 * $Date: 2006/02/14 04:40:57 $
 */


package javax.xml.soap;

import java.util.Iterator;
import java.util.Locale;

import javax.xml.namespace.QName;

/**
 * An element in the <code>SOAPBody</code> object that contains
 * error and/or status information. This information may relate to
 * errors in the <code>SOAPMessage</code> object or to problems
 * that are not related to the content in the message itself. Problems
 * not related to the message itself are generally errors in
 * processing, such as the inability to communicate with an upstream
 * server.
 * <P>
 * Depending on the <code>protocol</code> specified while creating the
 * <code>MessageFactory</code> instance,  a <code>SOAPFault</code> has
 * sub-elements as defined in the SOAP 1.1/SOAP 1.2 specification.
 */
public interface SOAPFault extends SOAPBodyElement {

    /**
     * Sets this <code>SOAPFault</code> object with the given fault code.
     *
     * <P> Fault codes, which give information about the fault, are defined
     * in the SOAP 1.1 specification. A fault code is mandatory and must
     * be of type <code>Name</code>. This method provides a convenient
     * way to set a fault code. For example,
     *
     * <PRE>
     * SOAPEnvelope se = ...;
     * // Create a qualified name in the SOAP namespace with a localName
     * // of "Client". Note that prefix parameter is optional and is null
     * // here which causes the implementation to use an appropriate prefix.
     * Name qname = se.createName("Client", null,
     *                            SOAPConstants.URI_NS_SOAP_ENVELOPE);
     * SOAPFault fault = ...;
     * fault.setFaultCode(qname);
     * </PRE>
     * It is preferable to use this method over {@link #setFaultCode(String)}.
     *
     * @param faultCodeQName a <code>Name</code> object giving the fault
     * code to be set. It must be namespace qualified.
     * @see #getFaultCodeAsName
     *
     * @exception SOAPException if there was an error in adding the
     *            <i>faultcode</i> element to the underlying XML tree.
     *
     * @since SAAJ 1.2
     */
    public void setFaultCode(Name faultCodeQName) throws SOAPException;

    /**
     * Sets this <code>SOAPFault</code> object with the given fault code.
     *
     * It is preferable to use this method over {@link #setFaultCode(Name)}.
     *
     * @param faultCodeQName a <code>QName</code> object giving the fault
     * code to be set. It must be namespace qualified.
     * @see #getFaultCodeAsQName
     *
     * @exception SOAPException if there was an error in adding the
     *            <code>faultcode</code> element to the underlying XML tree.
     *
     * @see #setFaultCode(Name)
     * @see #getFaultCodeAsQName()
     *
     * @since SAAJ 1.3
     */
    public void setFaultCode(QName faultCodeQName) throws SOAPException;

    /**
     * Sets this <code>SOAPFault</code> object with the give fault code.
     * <P>
     * Fault codes, which given information about the fault, are defined in
     * the SOAP 1.1 specification. This element is mandatory in SOAP 1.1.
     * Because the fault code is required to be a QName it is preferable to
     * use the {@link #setFaultCode(Name)} form of this method.
     *
     * @param faultCode a <code>String</code> giving the fault code to be set.
     *         It must be of the form "prefix:localName" where the prefix has
     *         been defined in a namespace declaration.
     * @see #setFaultCode(Name)
     * @see #getFaultCode
     * @see SOAPElement#addNamespaceDeclaration
     *
     * @exception SOAPException if there was an error in adding the
     *            <code>faultCode</code> to the underlying XML tree.
     */
    public void setFaultCode(String faultCode) throws SOAPException;

    /**
     * Gets the mandatory SOAP 1.1 fault code for this
     * <code>SOAPFault</code> object as a SAAJ <code>Name</code> object.
     * The SOAP 1.1 specification requires the value of the "faultcode"
     * element to be of type QName. This method returns the content of the
     * element as a QName in the form of a SAAJ Name object. This method
     * should be used instead of the <code>getFaultCode</code> method since
     * it allows applications to easily access the namespace name without
     * additional parsing.
     *
     * @return a <code>Name</code> representing the faultcode
     * @see #setFaultCode(Name)
     *
     * @since SAAJ 1.2
     */
    public Name getFaultCodeAsName();


    /**
     * Gets the fault code for this
     * <code>SOAPFault</code> object as a <code>QName</code> object.
     *
     * @return a <code>QName</code> representing the faultcode
     *
     * @see #setFaultCode(QName)
     *
     * @since SAAJ 1.3
     */
    public QName getFaultCodeAsQName();

    /**
     * Gets the Subcodes for this <code>SOAPFault</code> as an iterator over
     * <code>QNames</code>.
     *
     * @return an <code>Iterator</code> that accesses a sequence of
     *      <code>QNames</code>. This <code>Iterator</code> should not support
     *      the optional <code>remove</code> method. The order in which the
     *      Subcodes are returned reflects the hierarchy of Subcodes present
     *      in the fault from top to bottom.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since SAAJ 1.3
     */
    public Iterator getFaultSubcodes();

    /**
     * Removes any Subcodes that may be contained by this
     * <code>SOAPFault</code>. Subsequent calls to
     * <code>getFaultSubcodes</code> will return an empty iterator until a call
     * to <code>appendFaultSubcode</code> is made.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since SAAJ 1.3
     */
    public void removeAllFaultSubcodes();

    /**
     * Adds a Subcode to the end of the sequence of Subcodes contained by this
     * <code>SOAPFault</code>. Subcodes, which were introduced in SOAP 1.2, are
     * represented by a recursive sequence of subelements rooted in the
     * mandatory Code subelement of a SOAP Fault.
     *
     * @param subcode a QName containing the Value of the Subcode.
     *
     * @exception SOAPException if there was an error in setting the Subcode
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since SAAJ 1.3
     */
    public void appendFaultSubcode(QName subcode) throws SOAPException;

    /**
     * Gets the fault code for this <code>SOAPFault</code> object.
     *
     * @return a <code>String</code> with the fault code
     * @see #getFaultCodeAsName
     * @see #setFaultCode
     */
    public String getFaultCode();

    /**
     * Sets this <code>SOAPFault</code> object with the given fault actor.
     * <P>
     * The fault actor is the recipient in the message path who caused the
     * fault to happen.
     * <P>
     * If this <code>SOAPFault</code> supports SOAP 1.2 then this call is
     * equivalent to {@link #setFaultRole(String)}
     *
     * @param faultActor a <code>String</code> identifying the actor that
     *        caused this <code>SOAPFault</code> object
     * @see #getFaultActor
     *
     * @exception SOAPException if there was an error in adding the
     *            <code>faultActor</code> to the underlying XML tree.
     */
    public void setFaultActor(String faultActor) throws SOAPException;

    /**
     * Gets the fault actor for this <code>SOAPFault</code> object.
     * <P>
     * If this <code>SOAPFault</code> supports SOAP 1.2 then this call is
     * equivalent to {@link #getFaultRole()}
     *
     * @return a <code>String</code> giving the actor in the message path
     *         that caused this <code>SOAPFault</code> object
     * @see #setFaultActor
     */
    public String getFaultActor();

    /**
     * Sets the fault string for this <code>SOAPFault</code> object
     * to the given string.
     * <P>
     * If this
     * <code>SOAPFault</code> is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>
     *      addFaultReasonText(faultString, Locale.getDefault());
     * </pre>
     *
     * @param faultString a <code>String</code> giving an explanation of
     *        the fault
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in adding the
     *            <code>faultString</code> to the underlying XML tree.
     */
    public void setFaultString(String faultString) throws SOAPException;

    /**
     * Sets the fault string for this <code>SOAPFault</code> object
     * to the given string and localized to the given locale.
     * <P>
     * If this
     * <code>SOAPFault</code> is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>
     *      addFaultReasonText(faultString, locale);
     * </pre>
     *
     * @param faultString a <code>String</code> giving an explanation of
     *         the fault
     * @param locale a {@link java.util.Locale Locale} object indicating
     *         the native language of the <code>faultString</code>
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in adding the
     *            <code>faultString</code> to the underlying XML tree.
     *
     * @since SAAJ 1.2
     */
    public void setFaultString(String faultString, Locale locale)
        throws SOAPException;

    /**
     * Gets the fault string for this <code>SOAPFault</code> object.
     * <P>
     * If this
     * <code>SOAPFault</code> is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>
     *    String reason = null;
     *    try {
     *        reason = (String) getFaultReasonTexts().next();
     *    } catch (SOAPException e) {}
     *    return reason;
     * </pre>
     *
     * @return a <code>String</code> giving an explanation of
     *        the fault
     * @see #setFaultString(String)
     * @see #setFaultString(String, Locale)
     */
    public String getFaultString();

    /**
     * Gets the locale of the fault string for this <code>SOAPFault</code>
     * object.
     * <P>
     * If this
     * <code>SOAPFault</code> is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>
     *    Locale locale = null;
     *    try {
     *        locale = (Locale) getFaultReasonLocales().next();
     *    } catch (SOAPException e) {}
     *    return locale;
     * </pre>
     *
     * @return a <code>Locale</code> object indicating the native language of
     *          the fault string or <code>null</code> if no locale was specified
     * @see #setFaultString(String, Locale)
     *
     * @since SAAJ 1.2
     */
    public Locale getFaultStringLocale();

    /**
     * Returns true if this <code>SOAPFault</code> has a <code>Detail</code>
     * subelement and false otherwise. Equivalent to
     * <code>(getDetail()!=null)</code>.
     *
     * @return true if this <code>SOAPFault</code> has a <code>Detail</code>
     * subelement and false otherwise.
     *
     * @since SAAJ 1.3
     */
    public boolean hasDetail();

    /**
     * Returns the optional detail element for this <code>SOAPFault</code>
     * object.
     * <P>
     * A <code>Detail</code> object carries application-specific error
     * information, the scope of the error information is restricted to
     * faults in the <code>SOAPBodyElement</code> objects if this is a
     * SOAP 1.1 Fault.
     *
     * @return a <code>Detail</code> object with application-specific
     *         error information if present, null otherwise
     */
    public Detail getDetail();

    /**
     * Creates an optional <code>Detail</code> object and sets it as the
     * <code>Detail</code> object for this <code>SOAPFault</code>
     * object.
     * <P>
     * It is illegal to add a detail when the fault already
     * contains a detail. Therefore, this method should be called
     * only after the existing detail has been removed.
     *
     * @return the new <code>Detail</code> object
     *
     * @exception SOAPException if this
     *            <code>SOAPFault</code> object already contains a
     *            valid <code>Detail</code> object
     */
    public Detail addDetail() throws SOAPException;

    /**
     * Returns an <code>Iterator</code> over a distinct sequence of
     * <code>Locale</code>s for which there are associated Reason Text items.
     * Any of these <code>Locale</code>s can be used in a call to
     * <code>getFaultReasonText</code> in order to obtain a localized version
     * of the Reason Text string.
     *
     * @return an <code>Iterator</code> over a sequence of <code>Locale</code>
     *      objects for which there are associated Reason Text items.
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason locales.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since SAAJ 1.3
     */
    public Iterator getFaultReasonLocales() throws SOAPException;

    /**
     * Returns an <code>Iterator</code> over a sequence of
     * <code>String</code> objects containing all of the Reason Text items for
     * this <code>SOAPFault</code>.
     *
     * @return an <code>Iterator</code> over env:Fault/env:Reason/env:Text items.
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason texts.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since SAAJ 1.3
     */
    public Iterator getFaultReasonTexts() throws SOAPException;

    /**
     * Returns the Reason Text associated with the given <code>Locale</code>.
     * If more than one such Reason Text exists the first matching Text is
     * returned
     *
     * @param locale -- the <code>Locale</code> for which a localized
     *      Reason Text is desired
     *
     * @return the Reason Text associated with <code>locale</code>
     *
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason text for the specified locale .
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since SAAJ 1.3
     */
    public String getFaultReasonText(Locale locale) throws SOAPException;

    /**
     * Appends or replaces a Reason Text item containing the specified
     * text message and an <i>xml:lang</i> derived from
     * <code>locale</code>. If a Reason Text item with this
     * <i>xml:lang</i> already exists its text value will be replaced
     * with <code>text</code>.
     * The <code>locale</code> parameter should not be <code>null</code>
     * <P>
     * Code sample:
     *
     * <PRE>
     * SOAPFault fault = ...;
     * fault.addFaultReasonText("Version Mismatch", Locale.ENGLISH);
     * </PRE>
     *
     * @param text -- reason message string
     * @param locale -- Locale object representing the locale of the message
     *
     * @exception SOAPException if there was an error in adding the Reason text
     * or the <code>locale</code> passed was <code>null</code>.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since SAAJ 1.3
     */
    public void addFaultReasonText(String text, java.util.Locale locale)
        throws SOAPException;

    /**
     * Returns the optional Node element value for this
     * <code>SOAPFault</code> object. The Node element is
     * optional in SOAP 1.2.
     *
     * @return Content of the env:Fault/env:Node element as a String
     * or <code>null</code> if none
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Node.
     *
     * @since SAAJ 1.3
     */
    public String getFaultNode();

    /**
     * Creates or replaces any existing Node element value for
     * this <code>SOAPFault</code> object. The Node element
     * is optional in SOAP 1.2.
     *
     * @exception SOAPException  if there was an error in setting the
     *            Node for this  <code>SOAPFault</code> object.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Node.
     *
     *
     * @since SAAJ 1.3
     */
    public void setFaultNode(String uri) throws SOAPException;

    /**
     * Returns the optional Role element value for this
     * <code>SOAPFault</code> object. The Role element is
     * optional in SOAP 1.2.
     *
     * @return Content of the env:Fault/env:Role element as a String
     * or <code>null</code> if none
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since SAAJ 1.3
     */
    public String getFaultRole();

    /**
     * Creates or replaces any existing Role element value for
     * this <code>SOAPFault</code> object. The Role element
     * is optional in SOAP 1.2.
     *
     * @param uri - the URI of the Role
     *
     * @exception SOAPException  if there was an error in setting the
     *            Role for this  <code>SOAPFault</code> object.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since SAAJ 1.3
     */
    public void setFaultRole(String uri) throws SOAPException;

}
