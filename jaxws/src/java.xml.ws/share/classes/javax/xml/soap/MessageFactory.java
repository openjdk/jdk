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


import java.io.IOException;
import java.io.InputStream;

/**
 * A factory for creating {@code SOAPMessage} objects.
 * <P>
 * A SAAJ client can create a {@code MessageFactory} object
 * using the method {@code newInstance}, as shown in the following
 * lines of code.
 * <pre>{@code
 *       MessageFactory mf = MessageFactory.newInstance();
 *       MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
 * }</pre>
 * <P>
 * All {@code MessageFactory} objects, regardless of how they are
 * created, will produce {@code SOAPMessage} objects that
 * have the following elements by default:
 * <UL>
 *  <LI>A {@code SOAPPart} object
 *  <LI>A {@code SOAPEnvelope} object
 *  <LI>A {@code SOAPBody} object
 *  <LI>A {@code SOAPHeader} object
 * </UL>
 * In some cases, specialized MessageFactory objects may be obtained that produce messages
 * prepopulated with additional entries in the {@code SOAPHeader} object and the
 * {@code SOAPBody} object.
 * The content of a new {@code SOAPMessage} object depends on which of the two
 * {@code MessageFactory} methods is used to create it.
 * <UL>
 *  <LI>{@code createMessage()} <BR>
 *      This is the method clients would normally use to create a request message.
 *  <LI>{@code createMessage(MimeHeaders, java.io.InputStream)} -- message has
 *       content from the {@code InputStream} object and headers from the
 *       {@code MimeHeaders} object <BR>
 *        This method can be used internally by a service implementation to
 *        create a message that is a response to a request.
 * </UL>
 *
 * @since 1.6
 */
public abstract class MessageFactory {

    private static final String DEFAULT_MESSAGE_FACTORY
        = "com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl";

    /**
     * Creates a new {@code MessageFactory} object that is an instance
     * of the default implementation (SOAP 1.1).
     *
     * This method uses the lookup procedure specified in {@link javax.xml.soap} to locate and load the
     * {@link javax.xml.soap.MessageFactory} class.
     *
     * @return a new instance of a {@code MessageFactory}
     *
     * @exception SOAPException if there was an error in creating the
     *            default implementation of the
     *            {@code MessageFactory}.
     * @see SAAJMetaFactory
     */

    public static MessageFactory newInstance() throws SOAPException {


        try {
            MessageFactory factory = (MessageFactory) FactoryFinder.find(
                    MessageFactory.class,
                    DEFAULT_MESSAGE_FACTORY,
                    false);

            if (factory != null) {
                return factory;
            }
            return newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);

        } catch (Exception ex) {
            throw new SOAPException(
                    "Unable to create message factory for SOAP: "
                                    +ex.getMessage());
        }

    }

    /**
     * Creates a new {@code MessageFactory} object that is an instance
     * of the specified implementation.  May be a dynamic message factory,
     * a SOAP 1.1 message factory, or a SOAP 1.2 message factory. A dynamic
     * message factory creates messages based on the MIME headers specified
     * as arguments to the {@code createMessage} method.
     *
     * This method uses the SAAJMetaFactory to locate the implementation class
     * and create the MessageFactory instance.
     *
     * @return a new instance of a {@code MessageFactory}
     *
     * @param protocol  a string constant representing the class of the
     *                   specified message factory implementation. May be
     *                   either {@code DYNAMIC_SOAP_PROTOCOL},
     *                   {@code DEFAULT_SOAP_PROTOCOL} (which is the same
     *                   as) {@code SOAP_1_1_PROTOCOL}, or
     *                   {@code SOAP_1_2_PROTOCOL}.
     *
     * @exception SOAPException if there was an error in creating the
     *            specified implementation of  {@code MessageFactory}.
     * @see SAAJMetaFactory
     * @since 1.6, SAAJ 1.3
     */
    public static MessageFactory newInstance(String protocol) throws SOAPException {
        return SAAJMetaFactory.getInstance().newMessageFactory(protocol);
    }

    /**
     * Creates a new {@code SOAPMessage} object with the default
     * {@code SOAPPart}, {@code SOAPEnvelope}, {@code SOAPBody},
     * and {@code SOAPHeader} objects. Profile-specific message factories
     * can choose to prepopulate the {@code SOAPMessage} object with
     * profile-specific headers.
     * <P>
     * Content can be added to this message's {@code SOAPPart} object, and
     * the message can be sent "as is" when a message containing only a SOAP part
     * is sufficient. Otherwise, the {@code SOAPMessage} object needs
     * to create one or more {@code AttachmentPart} objects and
     * add them to itself. Any content that is not in XML format must be
     * in an {@code AttachmentPart} object.
     *
     * @return a new {@code SOAPMessage} object
     * @exception SOAPException if a SOAP error occurs
     * @exception UnsupportedOperationException if the protocol of this
     *      {@code MessageFactory} instance is {@code DYNAMIC_SOAP_PROTOCOL}
     */
    public abstract SOAPMessage createMessage()
        throws SOAPException;

    /**
     * Internalizes the contents of the given {@code InputStream} object into a
     * new {@code SOAPMessage} object and returns the {@code SOAPMessage}
     * object.
     *
     * @param in the {@code InputStream} object that contains the data
     *           for a message
     * @param headers the transport-specific headers passed to the
     *        message in a transport-independent fashion for creation of the
     *        message
     * @return a new {@code SOAPMessage} object containing the data from
     *         the given {@code InputStream} object
     *
     * @exception IOException if there is a problem in reading data from
     *            the input stream
     *
     * @exception SOAPException may be thrown if the message is invalid
     *
     * @exception IllegalArgumentException if the {@code MessageFactory}
     *      requires one or more MIME headers to be present in the
     *      {@code headers} parameter and they are missing.
     *      {@code MessageFactory} implementations for
     *      {@code SOAP_1_1_PROTOCOL} or
     *      {@code SOAP_1_2_PROTOCOL} must not throw
     *      {@code IllegalArgumentException} for this reason.
     */
    public abstract SOAPMessage createMessage(MimeHeaders headers,
                                              InputStream in)
        throws IOException, SOAPException;
}
