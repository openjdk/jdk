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

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

/**
 * {@code SOAPFactory} is a factory for creating various objects
 * that exist in the SOAP XML tree.

 * {@code SOAPFactory} can be
 * used to create XML fragments that will eventually end up in the
 * SOAP part. These fragments can be inserted as children of the
 * {@link SOAPHeaderElement} or {@link SOAPBodyElement} or
 * {@link SOAPEnvelope} or other {@link SOAPElement} objects.
 *
 * {@code SOAPFactory} also has methods to create
 * {@code javax.xml.soap.Detail} objects as well as
 * {@code java.xml.soap.Name} objects.
 *
 * @since 1.6
 */
public abstract class SOAPFactory {

    /**
     * Class name of default {@code SOAPFactory} implementation.
     */
    private static final String DEFAULT_SOAP_FACTORY
            = "com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPFactory1_1Impl";

    /**
     * Creates a {@code SOAPElement} object from an existing DOM
     * {@code Element}. If the DOM {@code Element} that is passed in
     * as an argument is already a {@code SOAPElement} then this method
     * must return it unmodified without any further work. Otherwise, a new
     * {@code SOAPElement} is created and a deep copy is made of the
     * {@code domElement} argument. The concrete type of the return value
     * will depend on the name of the {@code domElement} argument. If any
     * part of the tree rooted in {@code domElement} violates SOAP rules, a
     * {@code SOAPException} will be thrown.
     *
     * @param domElement the {@code Element} to be copied.
     *
     * @return a new {@code SOAPElement} that is a copy of {@code domElement}.
     *
     * @exception SOAPException if there is an error in creating the
     *            {@code SOAPElement} object
     *
     * @since 1.6, SAAJ 1.3
     */
    public SOAPElement createElement(Element domElement) throws SOAPException {
        throw new UnsupportedOperationException("createElement(org.w3c.dom.Element) must be overridden by all subclasses of SOAPFactory.");
    }

    /**
     * Creates a {@code SOAPElement} object initialized with the
     * given {@code Name} object. The concrete type of the return value
     * will depend on the name given to the new {@code SOAPElement}. For
     * instance, a new {@code SOAPElement} with the name
     * "{http://www.w3.org/2003/05/soap-envelope}Envelope" would cause a
     * {@code SOAPEnvelope} that supports SOAP 1.2 behavior to be created.
     *
     * @param name a {@code Name} object with the XML name for
     *             the new element
     *
     * @return the new {@code SOAPElement} object that was
     *         created
     *
     * @exception SOAPException if there is an error in creating the
     *            {@code SOAPElement} object
     * @see SOAPFactory#createElement(javax.xml.namespace.QName)
     */
    public abstract SOAPElement createElement(Name name) throws SOAPException;

    /**
     * Creates a {@code SOAPElement} object initialized with the
     * given {@code QName} object. The concrete type of the return value
     * will depend on the name given to the new {@code SOAPElement}. For
     * instance, a new {@code SOAPElement} with the name
     * "{http://www.w3.org/2003/05/soap-envelope}Envelope" would cause a
     * {@code SOAPEnvelope} that supports SOAP 1.2 behavior to be created.
     *
     * @param qname a {@code QName} object with the XML name for
     *             the new element
     *
     * @return the new {@code SOAPElement} object that was
     *         created
     *
     * @exception SOAPException if there is an error in creating the
     *            {@code SOAPElement} object
     * @see SOAPFactory#createElement(Name)
     * @since 1.6, SAAJ 1.3
     */
    public  SOAPElement createElement(QName qname) throws SOAPException {
        throw new UnsupportedOperationException("createElement(QName) must be overridden by all subclasses of SOAPFactory.");
    }

    /**
     * Creates a {@code SOAPElement} object initialized with the
     * given local name.
     *
     * @param localName a {@code String} giving the local name for
     *             the new element
     *
     * @return the new {@code SOAPElement} object that was
     *         created
     *
     * @exception SOAPException if there is an error in creating the
     *            {@code SOAPElement} object
     */
    public abstract SOAPElement createElement(String localName)
        throws SOAPException;


    /**
     * Creates a new {@code SOAPElement} object with the given
     * local name, prefix and uri. The concrete type of the return value
     * will depend on the name given to the new {@code SOAPElement}. For
     * instance, a new {@code SOAPElement} with the name
     * "{http://www.w3.org/2003/05/soap-envelope}Envelope" would cause a
     * {@code SOAPEnvelope} that supports SOAP 1.2 behavior to be created.
     *
     * @param localName a {@code String} giving the local name
     *                  for the new element
     * @param prefix the prefix for this {@code SOAPElement}
     * @param uri a {@code String} giving the URI of the
     *            namespace to which the new element belongs
     * @return the new {@code SOAPElement} object that was
     *         created
     *
     * @exception SOAPException if there is an error in creating the
     *            {@code SOAPElement} object
     */
    public abstract SOAPElement createElement(
        String localName,
        String prefix,
        String uri)
        throws SOAPException;

    /**
     * Creates a new {@code Detail} object which serves as a container
     * for {@code DetailEntry} objects.
     * <P>
     * This factory method creates {@code Detail} objects for use in
     * situations where it is not practical to use the {@code SOAPFault}
     * abstraction.
     *
     * @return a {@code Detail} object
     * @throws SOAPException if there is a SOAP error
     * @throws UnsupportedOperationException if the protocol specified
     *         for the SOAPFactory was {@code DYNAMIC_SOAP_PROTOCOL}
     */
    public abstract Detail createDetail() throws SOAPException;

    /**
     *Creates a new {@code SOAPFault} object initialized with the given {@code reasonText}
     *  and {@code faultCode}
     *@param reasonText the ReasonText/FaultString for the fault
     *@param faultCode the FaultCode for the fault
     *@return a {@code SOAPFault} object
     *@throws SOAPException if there is a SOAP error
     *@since 1.6, SAAJ 1.3
     */
    public abstract SOAPFault createFault(String reasonText, QName faultCode) throws SOAPException;

    /**
     *Creates a new default {@code SOAPFault} object
     *@return a {@code SOAPFault} object
     *@throws SOAPException if there is a SOAP error
     *@since 1.6, SAAJ 1.3
     */
    public abstract SOAPFault createFault() throws SOAPException;

    /**
     * Creates a new {@code Name} object initialized with the
     * given local name, namespace prefix, and namespace URI.
     * <P>
     * This factory method creates {@code Name} objects for use in
     * situations where it is not practical to use the {@code SOAPEnvelope}
     * abstraction.
     *
     * @param localName a {@code String} giving the local name
     * @param prefix a {@code String} giving the prefix of the namespace
     * @param uri a {@code String} giving the URI of the namespace
     * @return a {@code Name} object initialized with the given
     *         local name, namespace prefix, and namespace URI
     * @throws SOAPException if there is a SOAP error
     */
    public abstract Name createName(
        String localName,
        String prefix,
        String uri)
        throws SOAPException;

    /**
     * Creates a new {@code Name} object initialized with the
     * given local name.
     * <P>
     * This factory method creates {@code Name} objects for use in
     * situations where it is not practical to use the {@code SOAPEnvelope}
     * abstraction.
     *
     * @param localName a {@code String} giving the local name
     * @return a {@code Name} object initialized with the given
     *         local name
     * @throws SOAPException if there is a SOAP error
     */
    public abstract Name createName(String localName) throws SOAPException;

    /**
     * Creates a new {@code SOAPFactory} object that is an instance of
     * the default implementation (SOAP 1.1).
     *
     * This method uses the lookup procedure specified in {@link javax.xml.soap} to locate and load the
     * {@link javax.xml.soap.SOAPFactory} class.
     *
     * @return a new instance of a {@code SOAPFactory}
     *
     * @exception SOAPException if there was an error creating the
     *            default {@code SOAPFactory}
     * @see SAAJMetaFactory
     */
    public static SOAPFactory newInstance()
        throws SOAPException
    {
        try {
            SOAPFactory factory = FactoryFinder.find(
                    SOAPFactory.class,
                    DEFAULT_SOAP_FACTORY,
                    false);
            if (factory != null) return factory;

            // leave it on SAAJMetaFactory
            return newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        } catch (Exception ex) {
            throw new SOAPException(
                "Unable to create SOAP Factory: " + ex.getMessage());
        }

    }

    /**
     * Creates a new {@code SOAPFactory} object that is an instance of
     * the specified implementation, this method uses the SAAJMetaFactory to
     * locate the implementation class and create the SOAPFactory instance.
     *
     * @return a new instance of a {@code SOAPFactory}
     *
     * @param protocol  a string constant representing the protocol of the
     *                   specified SOAP factory implementation. May be
     *                   either {@code DYNAMIC_SOAP_PROTOCOL},
     *                   {@code DEFAULT_SOAP_PROTOCOL} (which is the same
     *                   as) {@code SOAP_1_1_PROTOCOL}, or
     *                   {@code SOAP_1_2_PROTOCOL}.
     *
     * @exception SOAPException if there was an error creating the
     *            specified {@code SOAPFactory}
     * @see SAAJMetaFactory
     * @since 1.6, SAAJ 1.3
     */
    public static SOAPFactory newInstance(String protocol)
        throws SOAPException {
            return SAAJMetaFactory.getInstance().newSOAPFactory(protocol);
    }
}
