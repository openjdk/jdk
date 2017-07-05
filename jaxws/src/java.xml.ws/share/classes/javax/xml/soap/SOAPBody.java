/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Locale;

import org.w3c.dom.Document;

import javax.xml.namespace.QName;

/**
 * An object that represents the contents of the SOAP body
 * element in a SOAP message. A SOAP body element consists of XML data
 * that affects the way the application-specific content is processed.
 * <P>
 * A {@code SOAPBody} object contains {@code SOAPBodyElement}
 * objects, which have the content for the SOAP body.
 * A {@code SOAPFault} object, which carries status and/or
 * error information, is an example of a {@code SOAPBodyElement} object.
 *
 * @see SOAPFault
 * @since 1.6
 */
public interface SOAPBody extends SOAPElement {

    /**
     * Creates a new {@code SOAPFault} object and adds it to
     * this {@code SOAPBody} object. The new {@code SOAPFault} will
     * have default values set for the mandatory child elements. The type of
     * the {@code SOAPFault} will be a SOAP 1.1 or a SOAP 1.2 {@code SOAPFault}
     * depending on the {@code protocol} specified while creating the
     * {@code MessageFactory} instance.
     * <p>
     * A {@code SOAPBody} may contain at most one {@code SOAPFault}
     * child element.
     *
     * @return the new {@code SOAPFault} object
     * @exception SOAPException if there is a SOAP error
     */
    public SOAPFault addFault() throws SOAPException;


    /**
     * Creates a new {@code SOAPFault} object and adds it to
     * this {@code SOAPBody} object. The type of the
     * {@code SOAPFault} will be a SOAP 1.1  or a SOAP 1.2
     * {@code SOAPFault} depending on the {@code protocol}
     * specified while creating the {@code MessageFactory} instance.
     * <p>
     * For SOAP 1.2 the {@code faultCode} parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the {@code faultString} parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the {@code faultCode} parameter is the value of the {@code faultcode}
     * element and the {@code faultString} parameter is the value of the {@code faultstring}
     * element.
     * <p>
     * A {@code SOAPBody} may contain at most one {@code SOAPFault}
     * child element.
     *
     * @param faultCode a {@code Name} object giving the fault
     *         code to be set; must be one of the fault codes defined in the Version
     *         of SOAP specification in use
     * @param faultString a {@code String} giving an explanation of
     *         the fault
     * @param locale a {@link java.util.Locale} object indicating
     *         the native language of the {@code faultString}
     * @return the new {@code SOAPFault} object
     * @exception SOAPException if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @since 1.6, SAAJ 1.2
     */
    public SOAPFault addFault(Name faultCode, String faultString, Locale locale) throws SOAPException;

    /**
     * Creates a new {@code SOAPFault} object and adds it to this
     * {@code SOAPBody} object. The type of the {@code SOAPFault}
     * will be a SOAP 1.1 or a SOAP 1.2 {@code SOAPFault} depending on
     * the {@code protocol} specified while creating the {@code MessageFactory}
     * instance.
     * <p>
     * For SOAP 1.2 the {@code faultCode} parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the {@code faultString} parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the {@code faultCode} parameter is the value of the {@code faultcode}
     * element and the {@code faultString} parameter is the value of the {@code faultstring}
     * element.
     * <p>
     * A {@code SOAPBody} may contain at most one {@code SOAPFault}
     * child element.
     *
     * @param faultCode
     *            a {@code QName} object giving the fault code to be
     *            set; must be one of the fault codes defined in the version
     *            of SOAP specification in use.
     * @param faultString
     *            a {@code String} giving an explanation of the fault
     * @param locale
     *            a {@link java.util.Locale Locale} object indicating the
     *            native language of the {@code faultString}
     * @return the new {@code SOAPFault} object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @see SOAPBody#addFault(Name faultCode, String faultString, Locale locale)
     *
     * @since 1.6, SAAJ 1.3
     */
    public SOAPFault addFault(QName faultCode, String faultString, Locale locale)
        throws SOAPException;

    /**
     * Creates a new  {@code SOAPFault} object and adds it to this
     * {@code SOAPBody} object. The type of the {@code SOAPFault}
     * will be a SOAP 1.1 or a SOAP 1.2 {@code SOAPFault} depending on
     * the {@code protocol} specified while creating the {@code MessageFactory}
     * instance.
     * <p>
     * For SOAP 1.2 the {@code faultCode} parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the {@code faultString} parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the {@code faultCode} parameter is the value of the <i>faultcode</i>
     * element and the {@code faultString} parameter is the value of the <i>faultstring</i>
     * element.
     * <p>
     * In case of a SOAP 1.2 fault, the default value for the mandatory {@code xml:lang}
     * attribute on the <i>Fault/Reason/Text</i> element will be set to
     * {@code java.util.Locale.getDefault()}
     * <p>
     * A {@code SOAPBody} may contain at most one {@code SOAPFault}
     * child element.
     *
     * @param faultCode
     *            a {@code Name} object giving the fault code to be set;
     *            must be one of the fault codes defined in the version of SOAP
     *            specification in use
     * @param faultString
     *            a {@code String} giving an explanation of the fault
     * @return the new {@code SOAPFault} object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @since 1.6, SAAJ 1.2
     */
    public SOAPFault addFault(Name faultCode, String faultString)
        throws SOAPException;

    /**
     * Creates a new {@code SOAPFault} object and adds it to this {@code SOAPBody}
     * object. The type of the {@code SOAPFault}
     * will be a SOAP 1.1 or a SOAP 1.2 {@code SOAPFault} depending on
     * the {@code protocol} specified while creating the {@code MessageFactory}
     * instance.
     * <p>
     * For SOAP 1.2 the {@code faultCode} parameter is the value of the
     * <i>Fault/Code/Value</i> element  and the {@code faultString} parameter
     * is the value of the <i>Fault/Reason/Text</i> element. For SOAP 1.1
     * the {@code faultCode} parameter is the value of the <i>faultcode</i>
     * element and the {@code faultString} parameter is the value of the <i>faultstring</i>
     * element.
     * <p>
     * In case of a SOAP 1.2 fault, the default value for the mandatory {@code xml:lang}
     * attribute on the <i>Fault/Reason/Text</i> element will be set to
     * {@code java.util.Locale.getDefault()}
     * <p>
     * A {@code SOAPBody} may contain at most one {@code SOAPFault}
     * child element
     *
     * @param faultCode
     *            a {@code QName} object giving the fault code to be
     *            set; must be one of the fault codes defined in the version
     *            of  SOAP specification in use
     * @param faultString
     *            a {@code String} giving an explanation of the fault
     * @return the new {@code SOAPFault} object
     * @exception SOAPException
     *                if there is a SOAP error
     * @see SOAPFault#setFaultCode
     * @see SOAPFault#setFaultString
     * @see SOAPBody#addFault(Name faultCode, String faultString)
     * @since 1.6, SAAJ 1.3
     */
    public SOAPFault addFault(QName faultCode, String faultString)
        throws SOAPException;

    /**
     * Indicates whether a {@code SOAPFault} object exists in this
     * {@code SOAPBody} object.
     *
     * @return {@code true} if a {@code SOAPFault} object exists
     *         in this {@code SOAPBody} object; {@code false}
     *         otherwise
     */
    public boolean hasFault();

    /**
     * Returns the {@code SOAPFault} object in this {@code SOAPBody}
     * object.
     *
     * @return the {@code SOAPFault} object in this {@code SOAPBody}
     *         object if present, null otherwise.
     */
    public SOAPFault getFault();

    /**
     * Creates a new {@code SOAPBodyElement} object with the specified
     * name and adds it to this {@code SOAPBody} object.
     *
     * @param name
     *            a {@code Name} object with the name for the new {@code SOAPBodyElement}
     *            object
     * @return the new {@code SOAPBodyElement} object
     * @exception SOAPException
     *                if a SOAP error occurs
     * @see SOAPBody#addBodyElement(javax.xml.namespace.QName)
     */
    public SOAPBodyElement addBodyElement(Name name) throws SOAPException;


    /**
     * Creates a new {@code SOAPBodyElement} object with the specified
     * QName and adds it to this {@code SOAPBody} object.
     *
     * @param qname
     *            a {@code QName} object with the qname for the new
     *            {@code SOAPBodyElement} object
     * @return the new {@code SOAPBodyElement} object
     * @exception SOAPException
     *                if a SOAP error occurs
     * @see SOAPBody#addBodyElement(Name)
     * @since 1.6, SAAJ 1.3
     */
    public SOAPBodyElement addBodyElement(QName qname) throws SOAPException;

    /**
     * Adds the root node of the DOM {@link org.w3c.dom.Document}
     * to this {@code SOAPBody} object.
     * <p>
     * Calling this method invalidates the {@code document} parameter.
     * The client application should discard all references to this {@code Document}
     * and its contents upon calling {@code addDocument}. The behavior
     * of an application that continues to use such references is undefined.
     *
     * @param document
     *            the {@code Document} object whose root node will be
     *            added to this {@code SOAPBody}.
     * @return the {@code SOAPBodyElement} that represents the root node
     *         that was added.
     * @exception SOAPException
     *                if the {@code Document} cannot be added
     * @since 1.6, SAAJ 1.2
     */
    public SOAPBodyElement addDocument(org.w3c.dom.Document document)
        throws SOAPException;

    /**
     * Creates a new DOM {@link org.w3c.dom.Document} and sets
     * the first child of this {@code SOAPBody} as it's document
     * element. The child {@code SOAPElement} is removed as part of the
     * process.
     *
     * @return the {@link org.w3c.dom.Document} representation
     *         of the {@code SOAPBody} content.
     *
     * @exception SOAPException
     *            if there is not exactly one child {@code SOAPElement} of the
     *            {@code SOAPBody}.
     *
     * @since 1.6, SAAJ 1.3
     */
    public org.w3c.dom.Document extractContentAsDocument()
        throws SOAPException;
}
