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
 * A factory for creating {@code SOAPConnection} objects. Implementation of this class
 * is optional. If {@code SOAPConnectionFactory.newInstance()} throws an
 * UnsupportedOperationException then the implementation does not support the
 * SAAJ communication infrastructure. Otherwise {@link SOAPConnection} objects
 * can be created by calling {@code createConnection()} on the newly
 * created {@code SOAPConnectionFactory} object.
 *
 * @since 1.6
 */
public abstract class SOAPConnectionFactory {

    /**
     * A constant representing the default value for a {@code SOAPConnection}
     * object. The default is the point-to-point SOAP connection.
     */
    private static final String DEFAULT_SOAP_CONNECTION_FACTORY
            = "com.sun.xml.internal.messaging.saaj.client.p2p.HttpSOAPConnectionFactory";

    /**
     * Creates an instance of the default
     * {@code SOAPConnectionFactory} object.
     *
     * This method uses the lookup procedure specified in {@link javax.xml.soap} to locate and load the
     * {@link javax.xml.soap.SOAPConnectionFactory} class.
     *
     * @return a new instance of a default
     *         {@code SOAPConnectionFactory} object
     *
     * @exception SOAPException if there was an error creating the
     *            {@code SOAPConnectionFactory}
     *
     * @exception UnsupportedOperationException if newInstance is not
     * supported.
     */
    public static SOAPConnectionFactory newInstance()
        throws SOAPException, UnsupportedOperationException
    {
        try {
            return FactoryFinder.find(
                    SOAPConnectionFactory.class,
                    DEFAULT_SOAP_CONNECTION_FACTORY,
                    true);
        } catch (Exception ex) {
            throw new SOAPException("Unable to create SOAP connection factory: "
                                    +ex.getMessage());
        }
    }

    /**
     * Create a new {@code SOAPConnection}.
     *
     * @return the new {@code SOAPConnection} object.
     *
     * @exception SOAPException if there was an exception creating the
     * {@code SOAPConnection} object.
     */
    public abstract SOAPConnection createConnection()
        throws SOAPException;
}
