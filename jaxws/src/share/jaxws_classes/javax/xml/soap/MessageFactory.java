/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * A factory for creating <code>SOAPMessage</code> objects.
 * <P>
 * A SAAJ client can create a <code>MessageFactory</code> object
 * using the method <code>newInstance</code>, as shown in the following
 * lines of code.
 * <PRE>
 *       MessageFactory mf = MessageFactory.newInstance();
 *       MessageFactory mf12 = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
 * </PRE>
 * <P>
 * All <code>MessageFactory</code> objects, regardless of how they are
 * created, will produce <code>SOAPMessage</code> objects that
 * have the following elements by default:
 * <UL>
 *  <LI>A <code>SOAPPart</code> object
 *  <LI>A <code>SOAPEnvelope</code> object
 *  <LI>A <code>SOAPBody</code> object
 *  <LI>A <code>SOAPHeader</code> object
 * </UL>
 * In some cases, specialized MessageFactory objects may be obtained that produce messages
 * prepopulated with additional entries in the <code>SOAPHeader</code> object and the
 * <code>SOAPBody</code> object.
 * The content of a new <code>SOAPMessage</code> object depends on which of the two
 * <code>MessageFactory</code> methods is used to create it.
 * <UL>
 *  <LI><code>createMessage()</code> <BR>
 *      This is the method clients would normally use to create a request message.
 *  <LI><code>createMessage(MimeHeaders, java.io.InputStream)</code> -- message has
 *       content from the <code>InputStream</code> object and headers from the
 *       <code>MimeHeaders</code> object <BR>
 *        This method can be used internally by a service implementation to
 *        create a message that is a response to a request.
 * </UL>
 */
public abstract class MessageFactory {

    static final String DEFAULT_MESSAGE_FACTORY
        = "com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl";

    static private final String MESSAGE_FACTORY_PROPERTY
        = "javax.xml.soap.MessageFactory";

    /**
     * Creates a new <code>MessageFactory</code> object that is an instance
     * of the default implementation (SOAP 1.1),
     *
     * This method uses the following ordered lookup procedure to determine the MessageFactory implementation class to load:
     * <UL>
     *  <LI> Use the javax.xml.soap.MessageFactory system property.
     *  <LI> Use the properties file "lib/jaxm.properties" in the JRE directory. This configuration file is in standard
     * java.util.Properties format and contains the fully qualified name of the implementation class with the key being the
     * system property defined above.
     *  <LI> Use the Services API (as detailed in the JAR specification), if available, to determine the classname. The Services API
     * will look for a classname in the file META-INF/services/javax.xml.soap.MessageFactory in jars available to the runtime.
     *  <LI> Use the SAAJMetaFactory instance to locate the MessageFactory implementation class.
     * </UL>

     *
     * @return a new instance of a <code>MessageFactory</code>
     *
     * @exception SOAPException if there was an error in creating the
     *            default implementation of the
     *            <code>MessageFactory</code>.
     * @see SAAJMetaFactory
     */

    public static MessageFactory newInstance() throws SOAPException {


        try {
            MessageFactory factory = (MessageFactory) FactoryFinder.find(
                    MESSAGE_FACTORY_PROPERTY,
                    DEFAULT_MESSAGE_FACTORY,
                    false);
                FactoryFinder.find(MESSAGE_FACTORY_PROPERTY,
                        DEFAULT_MESSAGE_FACTORY, false);

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
     * Creates a new <code>MessageFactory</code> object that is an instance
     * of the specified implementation.  May be a dynamic message factory,
     * a SOAP 1.1 message factory, or a SOAP 1.2 message factory. A dynamic
     * message factory creates messages based on the MIME headers specified
     * as arguments to the <code>createMessage</code> method.
     *
     * This method uses the SAAJMetaFactory to locate the implementation class
     * and create the MessageFactory instance.
     *
     * @return a new instance of a <code>MessageFactory</code>
     *
     * @param protocol  a string constant representing the class of the
     *                   specified message factory implementation. May be
     *                   either <code>DYNAMIC_SOAP_PROTOCOL</code>,
     *                   <code>DEFAULT_SOAP_PROTOCOL</code> (which is the same
     *                   as) <code>SOAP_1_1_PROTOCOL</code>, or
     *                   <code>SOAP_1_2_PROTOCOL</code>.
     *
     * @exception SOAPException if there was an error in creating the
     *            specified implementation of  <code>MessageFactory</code>.
     * @see SAAJMetaFactory
     * @since SAAJ 1.3
     */
    public static MessageFactory newInstance(String protocol) throws SOAPException {
        return SAAJMetaFactory.getInstance().newMessageFactory(protocol);
    }

    /**
     * Creates a new <code>SOAPMessage</code> object with the default
     * <code>SOAPPart</code>, <code>SOAPEnvelope</code>, <code>SOAPBody</code>,
     * and <code>SOAPHeader</code> objects. Profile-specific message factories
     * can choose to prepopulate the <code>SOAPMessage</code> object with
     * profile-specific headers.
     * <P>
     * Content can be added to this message's <code>SOAPPart</code> object, and
     * the message can be sent "as is" when a message containing only a SOAP part
     * is sufficient. Otherwise, the <code>SOAPMessage</code> object needs
     * to create one or more <code>AttachmentPart</code> objects and
     * add them to itself. Any content that is not in XML format must be
     * in an <code>AttachmentPart</code> object.
     *
     * @return a new <code>SOAPMessage</code> object
     * @exception SOAPException if a SOAP error occurs
     * @exception UnsupportedOperationException if the protocol of this
     *      <code>MessageFactory</code> instance is <code>DYNAMIC_SOAP_PROTOCOL</code>
     */
    public abstract SOAPMessage createMessage()
        throws SOAPException;

    /**
     * Internalizes the contents of the given <code>InputStream</code> object into a
     * new <code>SOAPMessage</code> object and returns the <code>SOAPMessage</code>
     * object.
     *
     * @param in the <code>InputStream</code> object that contains the data
     *           for a message
     * @param headers the transport-specific headers passed to the
     *        message in a transport-independent fashion for creation of the
     *        message
     * @return a new <code>SOAPMessage</code> object containing the data from
     *         the given <code>InputStream</code> object
     *
     * @exception IOException if there is a problem in reading data from
     *            the input stream
     *
     * @exception SOAPException may be thrown if the message is invalid
     *
     * @exception IllegalArgumentException if the <code>MessageFactory</code>
     *      requires one or more MIME headers to be present in the
     *      <code>headers</code> parameter and they are missing.
     *      <code>MessageFactory</code> implementations for
     *      <code>SOAP_1_1_PROTOCOL</code> or
     *      <code>SOAP_1_2_PROTOCOL</code> must not throw
     *      <code>IllegalArgumentException</code> for this reason.
     */
    public abstract SOAPMessage createMessage(MimeHeaders headers,
                                              InputStream in)
        throws IOException, SOAPException;
}
