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


/**
 * A point-to-point connection that a client can use for sending messages
 * directly to a remote party (represented by a URL, for instance).
 * <p>
 * The SOAPConnection class is optional. Some implementations may
 * not implement this interface in which case the call to
 * <code>SOAPConnectionFactory.newInstance()</code> (see below) will
 * throw an <code>UnsupportedOperationException</code>.
 * <p>
 * A client can obtain a <code>SOAPConnection</code> object using a
 * {@link SOAPConnectionFactory} object as in the following example:
 * <PRE>
 *      SOAPConnectionFactory factory = SOAPConnectionFactory.newInstance();
 *      SOAPConnection con = factory.createConnection();
 * </PRE>
 * A <code>SOAPConnection</code> object can be used to send messages
 * directly to a URL following the request/response paradigm.  That is,
 * messages are sent using the method <code>call</code>, which sends the
 * message and then waits until it gets a reply.
 */
public abstract class SOAPConnection {

    /**
     * Sends the given message to the specified endpoint and blocks until
     * it has returned the response.
     *
     * @param request the <code>SOAPMessage</code> object to be sent
     * @param to an <code>Object</code> that identifies
     *         where the message should be sent. It is required to
     *         support Objects of type
     *         <code>java.lang.String</code>,
     *         <code>java.net.URL</code>, and when JAXM is present
     *         <code>javax.xml.messaging.URLEndpoint</code>
     *
     * @return the <code>SOAPMessage</code> object that is the response to the
     *         message that was sent
     * @throws SOAPException if there is a SOAP error
     */
    public abstract SOAPMessage call(SOAPMessage request,
                                     Object to) throws SOAPException;

    /**
     * Gets a message from a specific endpoint and blocks until it receives,
     *
     * @param to an <code>Object</code> that identifies where
     *                  the request should be sent. Objects of type
     *                 <code>java.lang.String</code> and
     *                 <code>java.net.URL</code> must be supported.
     *
     * @return the <code>SOAPMessage</code> object that is the response to the
     *                  get message request
     * @throws SOAPException if there is a SOAP error
     * @since SAAJ 1.3
     */
    public SOAPMessage get(Object to)
                                throws SOAPException {
        throw new UnsupportedOperationException("All subclasses of SOAPConnection must override get()");
    }

    /**
     * Closes this <code>SOAPConnection</code> object.
     *
     * @throws SOAPException if there is a SOAP error
     */
    public abstract void close()
        throws SOAPException;
}
