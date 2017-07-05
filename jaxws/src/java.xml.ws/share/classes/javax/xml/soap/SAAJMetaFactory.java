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

/**
* The access point for the implementation classes of the factories defined in the
* SAAJ API. The {@code newInstance} methods defined on factories {@link SOAPFactory} and
* {@link MessageFactory} in SAAJ 1.3 defer to instances of this class to do the actual object creation.
* The implementations of {@code newInstance()} methods (in SOAPFactory and MessageFactory)
* that existed in SAAJ 1.2 have been updated to also delegate to the SAAJMetaFactory when the SAAJ 1.2
* defined lookup fails to locate the Factory implementation class name.
*
* <p>
* SAAJMetaFactory is a service provider interface and it uses similar lookup mechanism as other SAAJ factories
* to get actual instance:
*
* <ul>
*  <li>If a system property with name {@code javax.xml.soap.SAAJMetaFactory} exists then its value is assumed
*  to be the fully qualified name of the implementation class. This phase of the look up enables per-JVM
*  override of the SAAJ implementation.
*  <li>If a system property with name {@code javax.xml.soap.MetaFactory} exists then its value is assumed
*  to be the fully qualified name of the implementation class. This property, defined by previous specifications
 * (up to 1.3), is still supported, but it is strongly recommended to migrate to new property
 * {@code javax.xml.soap.SAAJMetaFactory}.
*  <li>Use the configuration file "jaxm.properties". The file is in standard {@link java.util.Properties} format
*  and typically located in the {@code conf} directory of the Java installation. It contains the fully qualified
*  name of the implementation class with key {@code javax.xml.soap.SAAJMetaFactory}. If no such property is defined,
 * again, property with key {@code javax.xml.soap.MetaFactory} is used. It is strongly recommended to migrate to
 * new property {@code javax.xml.soap.SAAJMetaFactory}.
*  <li> Use the service-provider loading facilities, defined by the {@link java.util.ServiceLoader} class,
*  to attempt to locate and load an implementation of the service using the {@linkplain
*  java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}.
*  <li> Finally, if all the steps above fail, platform default implementation is used.
* </ul>
*
* <p>
* There are no public methods on this
* class.
*
* @author SAAJ RI Development Team
* @since 1.6, SAAJ 1.3
*/
public abstract class SAAJMetaFactory {

    private static final String META_FACTORY_DEPRECATED_CLASS_PROPERTY =
            "javax.xml.soap.MetaFactory";

    private static final String DEFAULT_META_FACTORY_CLASS =
            "com.sun.xml.internal.messaging.saaj.soap.SAAJMetaFactoryImpl";

    /**
     * Creates a new instance of a concrete {@code SAAJMetaFactory} object.
     * The SAAJMetaFactory is an SPI, it pulls the creation of the other factories together into a
     * single place. Changing out the SAAJMetaFactory has the effect of changing out the entire SAAJ
     * implementation. Service providers provide the name of their {@code SAAJMetaFactory}
     * implementation.
     *
     * This method uses the lookup procedure specified in {@link javax.xml.soap} to locate and load the
     * {@link javax.xml.soap.SAAJMetaFactory} class.
     *
     * @return a concrete {@code SAAJMetaFactory} object
     * @exception SOAPException if there is an error in creating the {@code SAAJMetaFactory}
     */
    static SAAJMetaFactory getInstance() throws SOAPException {
            try {
                return FactoryFinder.find(
                        SAAJMetaFactory.class,
                        DEFAULT_META_FACTORY_CLASS,
                        true,
                        META_FACTORY_DEPRECATED_CLASS_PROPERTY);

            } catch (Exception e) {
                throw new SOAPException(
                    "Unable to create SAAJ meta-factory" + e.getMessage());
            }
    }

    protected SAAJMetaFactory() { }

     /**
      * Creates a {@code MessageFactory} object for
      * the given {@code String} protocol.
      *
      * @param protocol a {@code String} indicating the protocol
      * @return a {@link MessageFactory}, not null
      * @exception SOAPException if there is an error in creating the
      *            MessageFactory
      * @see SOAPConstants#SOAP_1_1_PROTOCOL
      * @see SOAPConstants#SOAP_1_2_PROTOCOL
      * @see SOAPConstants#DYNAMIC_SOAP_PROTOCOL
      */
    protected abstract MessageFactory newMessageFactory(String protocol)
        throws SOAPException;

     /**
      * Creates a {@code SOAPFactory} object for
      * the given {@code String} protocol.
      *
      * @param protocol a {@code String} indicating the protocol
      * @return a {@link SOAPFactory}, not null
      * @exception SOAPException if there is an error in creating the
      *            SOAPFactory
      * @see SOAPConstants#SOAP_1_1_PROTOCOL
      * @see SOAPConstants#SOAP_1_2_PROTOCOL
      * @see SOAPConstants#DYNAMIC_SOAP_PROTOCOL
      */
    protected abstract SOAPFactory newSOAPFactory(String protocol)
        throws SOAPException;
}
