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
import java.io.OutputStream;
import java.io.IOException;

import java.util.Iterator;

import javax.activation.DataHandler;

/**
 * The root class for all SOAP messages. As transmitted on the "wire", a SOAP
 * message is an XML document or a MIME message whose first body part is an
 * XML/SOAP document.
 * <P>
 * A {@code SOAPMessage} object consists of a SOAP part and optionally
 * one or more attachment parts. The SOAP part for a {@code SOAPMessage}
 * object is a {@code SOAPPart} object, which contains information used
 * for message routing and identification, and which can contain
 * application-specific content. All data in the SOAP Part of a message must be
 * in XML format.
 * <P>
 * A new {@code SOAPMessage} object contains the following by default:
 * <UL>
 *   <LI>A {@code SOAPPart} object
 *   <LI>A {@code SOAPEnvelope} object
 *   <LI>A {@code SOAPBody} object
 *   <LI>A {@code SOAPHeader} object
 * </UL>
 * The SOAP part of a message can be retrieved by calling the method {@code SOAPMessage.getSOAPPart()}.
 * The {@code SOAPEnvelope} object is retrieved from the {@code SOAPPart}
 * object, and the {@code SOAPEnvelope} object is used to retrieve the
 * {@code SOAPBody} and {@code SOAPHeader} objects.
 *
 * <pre>{@code
 *     SOAPPart sp = message.getSOAPPart();
 *     SOAPEnvelope se = sp.getEnvelope();
 *     SOAPBody sb = se.getBody();
 *     SOAPHeader sh = se.getHeader();
 * }</pre>
 *
 * <P>
 * In addition to the mandatory {@code SOAPPart} object, a {@code SOAPMessage}
 * object may contain zero or more {@code AttachmentPart} objects, each
 * of which contains application-specific data. The {@code SOAPMessage}
 * interface provides methods for creating {@code AttachmentPart}
 * objects and also for adding them to a {@code SOAPMessage} object. A
 * party that has received a {@code SOAPMessage} object can examine its
 * contents by retrieving individual attachment parts.
 * <P>
 * Unlike the rest of a SOAP message, an attachment is not required to be in
 * XML format and can therefore be anything from simple text to an image file.
 * Consequently, any message content that is not in XML format must be in an
 * {@code AttachmentPart} object.
 * <P>
 * A {@code MessageFactory} object may create {@code SOAPMessage}
 * objects with behavior that is specialized to a particular implementation or
 * application of SAAJ. For instance, a {@code MessageFactory} object
 * may produce {@code SOAPMessage} objects that conform to a particular
 * Profile such as ebXML. In this case a {@code MessageFactory} object
 * might produce {@code SOAPMessage} objects that are initialized with
 * ebXML headers.
 * <P>
 * In order to ensure backward source compatibility, methods that are added to
 * this class after version 1.1 of the SAAJ specification are all concrete
 * instead of abstract and they all have default implementations. Unless
 * otherwise noted in the JavaDocs for those methods the default
 * implementations simply throw an {@code UnsupportedOperationException}
 * and the SAAJ implementation code must override them with methods that
 * provide the specified behavior. Legacy client code does not have this
 * restriction, however, so long as there is no claim made that it conforms to
 * some later version of the specification than it was originally written for.
 * A legacy class that extends the SOAPMessage class can be compiled and/or run
 * against succeeding versions of the SAAJ API without modification. If such a
 * class was correctly implemented then it will continue to behave correctly
 * relative to the version of the specification against which it was written.
 *
 * @see MessageFactory
 * @see AttachmentPart
 * @since 1.6
 */
public abstract class SOAPMessage {

    /**
     * Specifies the character type encoding for the SOAP Message. Valid values
     * include "utf-8" and "utf-16". See vendor documentation for additional
     * supported values. The default is "utf-8".
     *
     * @see SOAPMessage#setProperty(String, Object) SOAPMessage.setProperty
     * @since 1.6, SAAJ 1.2
     */
    public static final String CHARACTER_SET_ENCODING =
        "javax.xml.soap.character-set-encoding";

    /**
     * Specifies whether the SOAP Message will contain an XML declaration when
     * it is sent. The only valid values are "true" and "false". The default is
     * "false".
     *
     * @see SOAPMessage#setProperty(String, Object) SOAPMessage.setProperty
     * @since 1.6, SAAJ 1.2
     */
    public static final String WRITE_XML_DECLARATION =
        "javax.xml.soap.write-xml-declaration";

    /**
     * Sets the description of this {@code SOAPMessage} object's
     * content with the given description.
     *
     * @param description a {@code String} describing the content of this
     *         message
     * @see #getContentDescription
     */
    public abstract void setContentDescription(String description);

    /**
     * Retrieves a description of this {@code SOAPMessage} object's
     * content.
     *
     * @return a {@code String} describing the content of this
     *         message or {@code null} if no description has been set
     * @see #setContentDescription
     */
    public abstract String getContentDescription();

    /**
     * Gets the SOAP part of this {@code SOAPMessage} object.
     * <p>
     * {@code SOAPMessage} object contains one or more attachments, the
     * SOAP Part must be the first MIME body part in the message.
     *
     * @return the {@code SOAPPart} object for this {@code SOAPMessage}
     * object
     */
    public abstract SOAPPart getSOAPPart();

    /**
     * Gets the SOAP Body contained in this {@code SOAPMessage} object.
     *
     * @return the {@code SOAPBody} object contained by this {@code SOAPMessage}
     * object
     * @throws SOAPException if the SOAP Body does not exist or cannot be retrieved
     * @since 1.6, SAAJ 1.2
     */
    public SOAPBody getSOAPBody() throws SOAPException {
        throw new UnsupportedOperationException("getSOAPBody must be overridden by all subclasses of SOAPMessage");
    }

    /**
     * Gets the SOAP Header contained in this {@code SOAPMessage} object.
     *
     * @return the {@code SOAPHeader} object contained
     *         by this {@code SOAPMessage} object
     * @exception SOAPException
     *               if the SOAP Header does not exist or cannot be retrieved
     * @since 1.6, SAAJ 1.2
     */
    public SOAPHeader getSOAPHeader() throws SOAPException {
        throw new UnsupportedOperationException("getSOAPHeader must be overridden by all subclasses of SOAPMessage");
    }

    /**
     * Removes all {@code AttachmentPart} objects that have been added
     * to this {@code SOAPMessage} object.
     * <p>
     * This method does not touch the SOAP part.
     */
    public abstract void removeAllAttachments();

    /**
     * Gets a count of the number of attachments in this message. This count
     * does not include the SOAP part.
     *
     * @return the number of {@code AttachmentPart} objects that are
     * part of this {@code SOAPMessage} object
     */
    public abstract int countAttachments();

    /**
     * Retrieves all the {@code AttachmentPart} objects that are part of
     * this {@code SOAPMessage} object.
     *
     * @return an iterator over all the attachments in this message
     */
    public abstract Iterator<AttachmentPart> getAttachments();

    /**
     * Retrieves all the {@code AttachmentPart} objects that have header
     * entries that match the specified headers. Note that a returned
     * attachment could have headers in addition to those specified.
     *
     * @param headers a {@code MimeHeaders} object containing the MIME
     *                headers for which to search
     * @return an iterator over all attachments that have a header that matches
     * one of the given headers
     */
    public abstract Iterator<AttachmentPart> getAttachments(MimeHeaders headers);

    /**
     * Removes all the {@code AttachmentPart} objects that have header
     * entries that match the specified headers. Note that the removed
     * attachment could have headers in addition to those specified.
     *
     * @param headers
     *           a {@code MimeHeaders} object containing the MIME
     *           headers for which to search
     * @since 1.6, SAAJ 1.3
     */
    public abstract void removeAttachments(MimeHeaders headers);


    /**
     * Returns an {@code AttachmentPart} object that is associated with an
     * attachment that is referenced by this {@code SOAPElement} or
     * {@code null} if no such attachment exists. References can be made
     * via an {@code href} attribute as described in
     * <a href="http://www.w3.org/TR/SOAP-attachments#SOAPReferenceToAttachements">SOAP Messages with Attachments</a>,
     * or via a single {@code Text} child node containing a URI as
     * described in the WS-I Attachments Profile 1.0 for elements of schema
     * type <a href="http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html">ref:swaRef</a>.
     * These two mechanisms must be supported.
     * The support for references via {@code href} attribute also implies that
     * this method should also be supported on an element that is an
     * <i>xop:Include</i> element (
     * <a href="http://www.w3.org/2000/xp/Group/3/06/Attachments/XOP.html">XOP</a>).
     * other reference mechanisms may be supported by individual
     * implementations of this standard. Contact your vendor for details.
     *
     * @param  element The {@code SOAPElement} containing the reference to an Attachment
     * @return the referenced {@code AttachmentPart} or null if no such
     *          {@code AttachmentPart} exists or no reference can be
     *          found in this {@code SOAPElement}.
     * @throws SOAPException if there is an error in the attempt to access the
     *          attachment
     *
     * @since 1.6, SAAJ 1.3
     */
    public abstract AttachmentPart getAttachment(SOAPElement element) throws SOAPException;


    /**
     * Adds the given {@code AttachmentPart} object to this {@code SOAPMessage}
     * object. An {@code AttachmentPart} object must be created before
     * it can be added to a message.
     *
     * @param attachmentPart
     *           an {@code attachmentPart} object that is to become part
     *           of this {@code SOAPMessage} object
     * @exception IllegalArgumentException
     *               if there was a problem with the specified {@code attachmentPart}
     *               object
     */
    public abstract void addAttachmentPart(AttachmentPart attachmentPart);

    /**
     * Creates a new empty {@code AttachmentPart} object. Note that the
     * method {@code addAttachmentPart} must be called with this new
     * {@code AttachmentPart} object as the parameter in order for it to
     * become an attachment to this {@code SOAPMessage} object.
     *
     * @return a new {@code AttachmentPart} object that can be populated
     *         and added to this {@code SOAPMessage} object
     */
    public abstract AttachmentPart createAttachmentPart();

    /**
     * Creates an {@code AttachmentPart} object and populates it using
     * the given {@code DataHandler} object.
     *
     * @param dataHandler
     *           the {@code javax.activation.DataHandler} object that
     *           will generate the content for this {@code SOAPMessage}
     *           object
     * @return a new {@code AttachmentPart} object that contains data
     *         generated by the given {@code DataHandler} object
     * @exception IllegalArgumentException
     *               if there was a problem with the specified {@code DataHandler}
     *               object
     * @see javax.activation.DataHandler
     * @see javax.activation.DataContentHandler
     */
    public AttachmentPart createAttachmentPart(DataHandler dataHandler) {
        AttachmentPart attachment = createAttachmentPart();
        attachment.setDataHandler(dataHandler);
        return attachment;
    }

    /**
     * Returns all the transport-specific MIME headers for this {@code SOAPMessage}
     * object in a transport-independent fashion.
     *
     * @return a {@code MimeHeaders} object containing the {@code MimeHeader}
     *         objects
     */
    public abstract MimeHeaders getMimeHeaders();

    /**
     * Creates an {@code AttachmentPart} object and populates it with
     * the specified data of the specified content type. The type of the
     * {@code Object} should correspond to the value given for the
     * {@code Content-Type}.
     *
     * @param content
     *           an {@code Object} containing the content for the
     *           {@code AttachmentPart} object to be created
     * @param contentType
     *           a {@code String} object giving the type of content;
     *           examples are "text/xml", "text/plain", and "image/jpeg"
     * @return a new {@code AttachmentPart} object that contains the
     *         given data
     * @exception IllegalArgumentException
     *               may be thrown if the contentType does not match the type
     *               of the content object, or if there was no
     *               {@code DataContentHandler} object for the given
     *               content object
     * @see javax.activation.DataHandler
     * @see javax.activation.DataContentHandler
     */
    public AttachmentPart createAttachmentPart(
        Object content,
        String contentType) {
        AttachmentPart attachment = createAttachmentPart();
        attachment.setContent(content, contentType);
        return attachment;
    }

    /**
     * Updates this {@code SOAPMessage} object with all the changes that
     * have been made to it. This method is called automatically when
     * {@link SOAPMessage#writeTo(OutputStream)} is  called. However, if
     * changes are made to a message that was received or to one that has
     * already been sent, the method {@code saveChanges} needs to be
     * called explicitly in order to save the changes. The method {@code saveChanges}
     * also generates any changes that can be read back (for example, a
     * MessageId in profiles that support a message id). All MIME headers in a
     * message that is created for sending purposes are guaranteed to have
     * valid values only after {@code saveChanges} has been called.
     * <P>
     * In addition, this method marks the point at which the data from all
     * constituent {@code AttachmentPart} objects are pulled into the
     * message.
     *
     * @exception SOAPException if there was a problem saving
     *            changes to this message.
     */
    public abstract void saveChanges() throws SOAPException;

    /**
     * Indicates whether this {@code SOAPMessage} object needs to have
     * the method {@code saveChanges} called on it.
     *
     * @return {@code true} if {@code saveChanges} needs to be
     *         called; {@code false} otherwise.
     */
    public abstract boolean saveRequired();

    /**
     * Writes this {@code SOAPMessage} object to the given output
     * stream. The externalization format is as defined by the SOAP 1.1 with
     * Attachments specification.
     * <P>
     * If there are no attachments, just an XML stream is written out. For
     * those messages that have attachments, {@code writeTo} writes a
     * MIME-encoded byte stream.
     * <P>
     * Note that this method does not write the transport-specific MIME Headers
     * of the Message
     *
     * @param out
     *           the {@code OutputStream} object to which this {@code SOAPMessage}
     *           object will be written
     * @exception IOException
     *               if an I/O error occurs
     * @exception SOAPException
     *               if there was a problem in externalizing this SOAP message
     */
    public abstract void writeTo(OutputStream out)
        throws SOAPException, IOException;

    /**
     * Associates the specified value with the specified property. If there was
     * already a value associated with this property, the old value is
     * replaced.
     * <p>
     * The valid property names include
     * {@link SOAPMessage#WRITE_XML_DECLARATION}  and
     * {@link SOAPMessage#CHARACTER_SET_ENCODING}. All of these standard SAAJ
     * properties are prefixed by "javax.xml.soap". Vendors may also add
     * implementation specific properties. These properties must be prefixed
     * with package names that are unique to the vendor.
     * <p>
     * Setting the property {@code WRITE_XML_DECLARATION} to {@code "true"}
     * will cause an XML Declaration to be written out at the start of the SOAP
     * message. The default value of "false" suppresses this declaration.
     * <p>
     * The property {@code CHARACTER_SET_ENCODING} defaults to the value
     * {@code "utf-8"} which causes the SOAP message to be encoded using
     * UTF-8. Setting {@code CHARACTER_SET_ENCODING} to {@code "utf-16"}
     * causes the SOAP message to be encoded using UTF-16.
     * <p>
     * Some implementations may allow encodings in addition to UTF-8 and
     * UTF-16. Refer to your vendor's documentation for details.
     *
     * @param property
     *           the property with which the specified value is to be
     *           associated.
     * @param value
     *           the value to be associated with the specified property
     * @exception SOAPException
     *               if the property name is not recognized.
     * @since 1.6, SAAJ 1.2
     */
    public void setProperty(String property, Object value)
        throws SOAPException {
            throw new UnsupportedOperationException("setProperty must be overridden by all subclasses of SOAPMessage");
    }

    /**
     * Retrieves value of the specified property.
     *
     * @param property
     *           the name of the property to retrieve
     * @return the value associated with the named property or {@code null}
     *         if no such property exists.
     * @exception SOAPException
     *               if the property name is not recognized.
     * @since 1.6, SAAJ 1.2
     */
    public Object getProperty(String property) throws SOAPException {
        throw new UnsupportedOperationException("getProperty must be overridden by all subclasses of SOAPMessage");
    }
}
