/*
 * Copyright (c) 2004, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;

import javax.xml.namespace.QName;

/**
 * An element in the {@code SOAPBody} object that contains
 * error and/or status information. This information may relate to
 * errors in the {@code SOAPMessage} object or to problems
 * that are not related to the content in the message itself. Problems
 * not related to the message itself are generally errors in
 * processing, such as the inability to communicate with an upstream
 * server.
 * <P>
 * Depending on the {@code protocol} specified while creating the
 * {@code MessageFactory} instance,  a {@code SOAPFault} has
 * sub-elements as defined in the SOAP 1.1/SOAP 1.2 specification.
 *
 * @since 1.6
 */
public interface SOAPFault extends SOAPBodyElement {

    /**
     * Sets this {@code SOAPFault} object with the given fault code.
     *
     * <P> Fault codes, which give information about the fault, are defined
     * in the SOAP 1.1 specification. A fault code is mandatory and must
     * be of type {@code Name}. This method provides a convenient
     * way to set a fault code. For example,
     *
     * <pre>{@code
     * SOAPEnvelope se = ...;
     * // Create a qualified name in the SOAP namespace with a localName
     * // of "Client". Note that prefix parameter is optional and is null
     * // here which causes the implementation to use an appropriate prefix.
     * Name qname = se.createName("Client", null,
     *                            SOAPConstants.URI_NS_SOAP_ENVELOPE);
     * SOAPFault fault = ...;
     * fault.setFaultCode(qname);
     * }</pre>
     * It is preferable to use this method over {@link #setFaultCode(String)}.
     *
     * @param faultCodeQName a {@code Name} object giving the fault
     * code to be set. It must be namespace qualified.
     * @see #getFaultCodeAsName
     *
     * @exception SOAPException if there was an error in adding the
     *            <i>faultcode</i> element to the underlying XML tree.
     *
     * @since 1.6, SAAJ 1.2
     */
    public void setFaultCode(Name faultCodeQName) throws SOAPException;

    /**
     * Sets this {@code SOAPFault} object with the given fault code.
     *
     * It is preferable to use this method over {@link #setFaultCode(Name)}.
     *
     * @param faultCodeQName a {@code QName} object giving the fault
     * code to be set. It must be namespace qualified.
     * @see #getFaultCodeAsQName
     *
     * @exception SOAPException if there was an error in adding the
     *            {@code faultcode} element to the underlying XML tree.
     *
     * @see #setFaultCode(Name)
     * @see #getFaultCodeAsQName()
     *
     * @since 1.6, SAAJ 1.3
     */
    public void setFaultCode(QName faultCodeQName) throws SOAPException;

    /**
     * Sets this {@code SOAPFault} object with the give fault code.
     * <P>
     * Fault codes, which given information about the fault, are defined in
     * the SOAP 1.1 specification. This element is mandatory in SOAP 1.1.
     * Because the fault code is required to be a QName it is preferable to
     * use the {@link #setFaultCode(Name)} form of this method.
     *
     * @param faultCode a {@code String} giving the fault code to be set.
     *         It must be of the form "prefix:localName" where the prefix has
     *         been defined in a namespace declaration.
     * @see #setFaultCode(Name)
     * @see #getFaultCode
     * @see SOAPElement#addNamespaceDeclaration
     *
     * @exception SOAPException if there was an error in adding the
     *            {@code faultCode} to the underlying XML tree.
     */
    public void setFaultCode(String faultCode) throws SOAPException;

    /**
     * Gets the mandatory SOAP 1.1 fault code for this
     * {@code SOAPFault} object as a SAAJ {@code Name} object.
     * The SOAP 1.1 specification requires the value of the "faultcode"
     * element to be of type QName. This method returns the content of the
     * element as a QName in the form of a SAAJ Name object. This method
     * should be used instead of the {@code getFaultCode} method since
     * it allows applications to easily access the namespace name without
     * additional parsing.
     *
     * @return a {@code Name} representing the faultcode
     * @see #setFaultCode(Name)
     *
     * @since 1.6, SAAJ 1.2
     */
    public Name getFaultCodeAsName();


    /**
     * Gets the fault code for this
     * {@code SOAPFault} object as a {@code QName} object.
     *
     * @return a {@code QName} representing the faultcode
     *
     * @see #setFaultCode(QName)
     *
     * @since 1.6, SAAJ 1.3
     */
    public QName getFaultCodeAsQName();

    /**
     * Gets the Subcodes for this {@code SOAPFault} as an iterator over
     * {@code QNames}.
     *
     * @return an {@code Iterator} that accesses a sequence of
     *      {@code QNames}. This {@code Iterator} should not support
     *      the optional {@code remove} method. The order in which the
     *      Subcodes are returned reflects the hierarchy of Subcodes present
     *      in the fault from top to bottom.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since 1.6, SAAJ 1.3
     */
    public Iterator<QName> getFaultSubcodes();

    /**
     * Removes any Subcodes that may be contained by this
     * {@code SOAPFault}. Subsequent calls to
     * {@code getFaultSubcodes} will return an empty iterator until a call
     * to {@code appendFaultSubcode} is made.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since 1.6, SAAJ 1.3
     */
    public void removeAllFaultSubcodes();

    /**
     * Adds a Subcode to the end of the sequence of Subcodes contained by this
     * {@code SOAPFault}. Subcodes, which were introduced in SOAP 1.2, are
     * represented by a recursive sequence of subelements rooted in the
     * mandatory Code subelement of a SOAP Fault.
     *
     * @param subcode a QName containing the Value of the Subcode.
     *
     * @exception SOAPException if there was an error in setting the Subcode
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Subcode.
     *
     * @since 1.6, SAAJ 1.3
     */
    public void appendFaultSubcode(QName subcode) throws SOAPException;

    /**
     * Gets the fault code for this {@code SOAPFault} object.
     *
     * @return a {@code String} with the fault code
     * @see #getFaultCodeAsName
     * @see #setFaultCode
     */
    public String getFaultCode();

    /**
     * Sets this {@code SOAPFault} object with the given fault actor.
     * <P>
     * The fault actor is the recipient in the message path who caused the
     * fault to happen.
     * <P>
     * If this {@code SOAPFault} supports SOAP 1.2 then this call is
     * equivalent to {@link #setFaultRole(String)}
     *
     * @param faultActor a {@code String} identifying the actor that
     *        caused this {@code SOAPFault} object
     * @see #getFaultActor
     *
     * @exception SOAPException if there was an error in adding the
     *            {@code faultActor} to the underlying XML tree.
     */
    public void setFaultActor(String faultActor) throws SOAPException;

    /**
     * Gets the fault actor for this {@code SOAPFault} object.
     * <P>
     * If this {@code SOAPFault} supports SOAP 1.2 then this call is
     * equivalent to {@link #getFaultRole()}
     *
     * @return a {@code String} giving the actor in the message path
     *         that caused this {@code SOAPFault} object
     * @see #setFaultActor
     */
    public String getFaultActor();

    /**
     * Sets the fault string for this {@code SOAPFault} object
     * to the given string.
     * <P>
     * If this
     * {@code SOAPFault} is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>{@code
     *      addFaultReasonText(faultString, Locale.getDefault());
     * }</pre>
     *
     * @param faultString a {@code String} giving an explanation of
     *        the fault
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in adding the
     *            {@code faultString} to the underlying XML tree.
     */
    public void setFaultString(String faultString) throws SOAPException;

    /**
     * Sets the fault string for this {@code SOAPFault} object
     * to the given string and localized to the given locale.
     * <P>
     * If this
     * {@code SOAPFault} is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>{@code
     *      addFaultReasonText(faultString, locale);
     * }</pre>
     *
     * @param faultString a {@code String} giving an explanation of
     *         the fault
     * @param locale a {@link java.util.Locale Locale} object indicating
     *         the native language of the {@code faultString}
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in adding the
     *            {@code faultString} to the underlying XML tree.
     *
     * @since 1.6, SAAJ 1.2
     */
    public void setFaultString(String faultString, Locale locale)
        throws SOAPException;

    /**
     * Gets the fault string for this {@code SOAPFault} object.
     * <P>
     * If this
     * {@code SOAPFault} is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>{@code
     *    String reason = null;
     *    try {
     *        reason = (String) getFaultReasonTexts().next();
     *    } catch (SOAPException e) {}
     *    return reason;
     * }</pre>
     *
     * @return a {@code String} giving an explanation of
     *        the fault
     * @see #setFaultString(String)
     * @see #setFaultString(String, Locale)
     */
    public String getFaultString();

    /**
     * Gets the locale of the fault string for this {@code SOAPFault}
     * object.
     * <P>
     * If this
     * {@code SOAPFault} is part of a message that supports SOAP 1.2 then
     * this call is equivalent to:
     * <pre>{@code
     *    Locale locale = null;
     *    try {
     *        locale = (Locale) getFaultReasonLocales().next();
     *    } catch (SOAPException e) {}
     *    return locale;
     * }</pre>
     *
     * @return a {@code Locale} object indicating the native language of
     *          the fault string or {@code null} if no locale was specified
     * @see #setFaultString(String, Locale)
     *
     * @since 1.6, SAAJ 1.2
     */
    public Locale getFaultStringLocale();

    /**
     * Returns true if this {@code SOAPFault} has a {@code Detail}
     * subelement and false otherwise. Equivalent to
     * {@code (getDetail()!=null)}.
     *
     * @return true if this {@code SOAPFault} has a {@code Detail}
     * subelement and false otherwise.
     *
     * @since 1.6, SAAJ 1.3
     */
    public boolean hasDetail();

    /**
     * Returns the optional detail element for this {@code SOAPFault}
     * object.
     * <P>
     * A {@code Detail} object carries application-specific error
     * information, the scope of the error information is restricted to
     * faults in the {@code SOAPBodyElement} objects if this is a
     * SOAP 1.1 Fault.
     *
     * @return a {@code Detail} object with application-specific
     *         error information if present, null otherwise
     */
    public Detail getDetail();

    /**
     * Creates an optional {@code Detail} object and sets it as the
     * {@code Detail} object for this {@code SOAPFault}
     * object.
     * <P>
     * It is illegal to add a detail when the fault already
     * contains a detail. Therefore, this method should be called
     * only after the existing detail has been removed.
     *
     * @return the new {@code Detail} object
     *
     * @exception SOAPException if this
     *            {@code SOAPFault} object already contains a
     *            valid {@code Detail} object
     */
    public Detail addDetail() throws SOAPException;

    /**
     * Returns an {@code Iterator} over a distinct sequence of
     * {@code Locale}s for which there are associated Reason Text items.
     * Any of these {@code Locale}s can be used in a call to
     * {@code getFaultReasonText} in order to obtain a localized version
     * of the Reason Text string.
     *
     * @return an {@code Iterator} over a sequence of {@code Locale}
     *      objects for which there are associated Reason Text items.
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason locales.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since 1.6, SAAJ 1.3
     */
    public Iterator<Locale> getFaultReasonLocales() throws SOAPException;

    /**
     * Returns an {@code Iterator} over a sequence of
     * {@code String} objects containing all of the Reason Text items for
     * this {@code SOAPFault}.
     *
     * @return an {@code Iterator} over env:Fault/env:Reason/env:Text items.
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason texts.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since 1.6, SAAJ 1.3
     */
    public Iterator<String> getFaultReasonTexts() throws SOAPException;

    /**
     * Returns the Reason Text associated with the given {@code Locale}.
     * If more than one such Reason Text exists the first matching Text is
     * returned
     *
     * @param locale -- the {@code Locale} for which a localized
     *      Reason Text is desired
     *
     * @return the Reason Text associated with {@code locale}
     *
     * @see #getFaultString
     *
     * @exception SOAPException if there was an error in retrieving
     * the  fault Reason text for the specified locale .
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since 1.6, SAAJ 1.3
     */
    public String getFaultReasonText(Locale locale) throws SOAPException;

    /**
     * Appends or replaces a Reason Text item containing the specified
     * text message and an <i>xml:lang</i> derived from
     * {@code locale}. If a Reason Text item with this
     * <i>xml:lang</i> already exists its text value will be replaced
     * with {@code text}.
     * The {@code locale} parameter should not be {@code null}
     * <P>
     * Code sample:
     *
     * <pre>{@code
     * SOAPFault fault = ...;
     * fault.addFaultReasonText("Version Mismatch", Locale.ENGLISH);
     * }</pre>
     *
     * @param text -- reason message string
     * @param locale -- Locale object representing the locale of the message
     *
     * @exception SOAPException if there was an error in adding the Reason text
     * or the {@code locale} passed was {@code null}.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Reason.
     *
     * @since 1.6, SAAJ 1.3
     */
    public void addFaultReasonText(String text, java.util.Locale locale)
        throws SOAPException;

    /**
     * Returns the optional Node element value for this
     * {@code SOAPFault} object. The Node element is
     * optional in SOAP 1.2.
     *
     * @return Content of the env:Fault/env:Node element as a String
     * or {@code null} if none
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Node.
     *
     * @since 1.6, SAAJ 1.3
     */
    public String getFaultNode();

    /**
     * Creates or replaces any existing Node element value for
     * this {@code SOAPFault} object. The Node element
     * is optional in SOAP 1.2.
     *
     * @param uri the URI of the Node
     *
     * @exception SOAPException  if there was an error in setting the
     *            Node for this  {@code SOAPFault} object.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Node.
     *
     *
     * @since 1.6, SAAJ 1.3
     */
    public void setFaultNode(String uri) throws SOAPException;

    /**
     * Returns the optional Role element value for this
     * {@code SOAPFault} object. The Role element is
     * optional in SOAP 1.2.
     *
     * @return Content of the env:Fault/env:Role element as a String
     * or {@code null} if none
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since 1.6, SAAJ 1.3
     */
    public String getFaultRole();

    /**
     * Creates or replaces any existing Role element value for
     * this {@code SOAPFault} object. The Role element
     * is optional in SOAP 1.2.
     *
     * @param uri the URI of the Role
     *
     * @exception SOAPException  if there was an error in setting the
     *            Role for this  {@code SOAPFault} object.
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since 1.6, SAAJ 1.3
     */
    public void setFaultRole(String uri) throws SOAPException;

}
