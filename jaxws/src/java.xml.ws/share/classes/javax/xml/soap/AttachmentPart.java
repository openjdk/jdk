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

import java.io.InputStream;
import java.util.Iterator;

import javax.activation.DataHandler;

/**
 * A single attachment to a {@code SOAPMessage} object. A {@code SOAPMessage}
 * object may contain zero, one, or many {@code AttachmentPart} objects.
 * Each {@code AttachmentPart} object consists of two parts,
 * application-specific content and associated MIME headers. The
 * MIME headers consists of name/value pairs that can be used to
 * identify and describe the content.
 * <p>
 * An {@code AttachmentPart} object must conform to certain standards.
 * <OL>
 * <LI>It must conform to <a href="http://www.ietf.org/rfc/rfc2045.txt">
 *     MIME [RFC2045] standards</a>
 * <LI>It MUST contain content
 * <LI>The header portion MUST include the following header:
 *  <UL>
 *   <LI>{@code Content-Type}<br>
 *       This header identifies the type of data in the content of an
 *       {@code AttachmentPart} object and MUST conform to [RFC2045].
 *       The following is an example of a Content-Type header:
 *       <PRE>
 *       Content-Type:  application/xml
 *       </PRE>
 *       The following line of code, in which {@code ap} is an
 *       {@code AttachmentPart} object, sets the header shown in
 *       the previous example.
 *       <PRE>
 *       ap.setMimeHeader("Content-Type", "application/xml");
 *       </PRE>
 *  </UL>
 * </OL>
 * <p>
 * There are no restrictions on the content portion of an {@code
 * AttachmentPart} object. The content may be anything from a
 * simple plain text object to a complex XML document or image file.
 *
 * <p>
 * An {@code AttachmentPart} object is created with the method
 * {@code SOAPMessage.createAttachmentPart}. After setting its MIME headers,
 *  the {@code AttachmentPart} object is added to the message
 * that created it with the method {@code SOAPMessage.addAttachmentPart}.
 *
 * <p>
 * The following code fragment, in which {@code m} is a
 * {@code SOAPMessage} object and {@code contentStringl} is a
 * {@code String}, creates an instance of {@code AttachmentPart},
 * sets the {@code AttachmentPart} object with some content and
 * header information, and adds the {@code AttachmentPart} object to
 * the {@code SOAPMessage} object.
 * <PRE>
 *     AttachmentPart ap1 = m.createAttachmentPart();
 *     ap1.setContent(contentString1, "text/plain");
 *     m.addAttachmentPart(ap1);
 * </PRE>
 *
 *
 * <p>
 * The following code fragment creates and adds a second
 * {@code AttachmentPart} instance to the same message. {@code jpegData}
 * is a binary byte buffer representing the jpeg file.
 * <PRE>
 *     AttachmentPart ap2 = m.createAttachmentPart();
 *     byte[] jpegData =  ...;
 *     ap2.setContent(new ByteArrayInputStream(jpegData), "image/jpeg");
 *     m.addAttachmentPart(ap2);
 * </PRE>
 * <p>
 * The {@code getContent} method retrieves the contents and header from
 * an {@code AttachmentPart} object. Depending on the
 * {@code DataContentHandler} objects present, the returned
 * {@code Object} can either be a typed Java object corresponding
 * to the MIME type or an {@code InputStream} object that contains the
 * content as bytes.
 * <PRE>
 *     String content1 = ap1.getContent();
 *     java.io.InputStream content2 = ap2.getContent();
 * </PRE>
 *
 * The method {@code clearContent} removes all the content from an
 * {@code AttachmentPart} object but does not affect its header information.
 * <PRE>
 *     ap1.clearContent();
 * </PRE>
 *
 * @since 1.6
 */

public abstract class AttachmentPart {
    /**
     * Returns the number of bytes in this {@code AttachmentPart}
     * object.
     *
     * @return the size of this {@code AttachmentPart} object in bytes
     *         or -1 if the size cannot be determined
     * @exception SOAPException if the content of this attachment is
     *            corrupted of if there was an exception while trying
     *            to determine the size.
     */
    public abstract int getSize() throws SOAPException;

    /**
     * Clears out the content of this {@code AttachmentPart} object.
     * The MIME header portion is left untouched.
     */
    public abstract void clearContent();

    /**
     * Gets the content of this {@code AttachmentPart} object as a Java
     * object. The type of the returned Java object depends on (1) the
     * {@code DataContentHandler} object that is used to interpret the bytes
     * and (2) the {@code Content-Type} given in the header.
     * <p>
     * For the MIME content types "text/plain", "text/html" and "text/xml", the
     * {@code DataContentHandler} object does the conversions to and
     * from the Java types corresponding to the MIME types.
     * For other MIME types,the {@code DataContentHandler} object
     * can return an {@code InputStream} object that contains the content data
     * as raw bytes.
     * <p>
     * A SAAJ-compliant implementation must, as a minimum, return a
     * {@code java.lang.String} object corresponding to any content
     * stream with a {@code Content-Type} value of
     * {@code text/plain}, a
     * {@code javax.xml.transform.stream.StreamSource} object corresponding to a
     * content stream with a {@code Content-Type} value of
     * {@code text/xml}, a {@code java.awt.Image} object
     * corresponding to a content stream with a
     * {@code Content-Type} value of {@code image/gif} or
     * {@code image/jpeg}.  For those content types that an
     * installed {@code DataContentHandler} object does not understand, the
     * {@code DataContentHandler} object is required to return a
     * {@code java.io.InputStream} object with the raw bytes.
     *
     * @return a Java object with the content of this {@code AttachmentPart}
     *         object
     *
     * @exception SOAPException if there is no content set into this
     *            {@code AttachmentPart} object or if there was a data
     *            transformation error
     */
    public abstract Object getContent() throws SOAPException;

    /**
     * Gets the content of this {@code AttachmentPart} object as an
     * InputStream as if a call had been made to {@code getContent} and no
     * {@code DataContentHandler} had been registered for the
     * {@code content-type} of this {@code AttachmentPart}.
     *<p>
     * Note that reading from the returned InputStream would result in consuming
     * the data in the stream. It is the responsibility of the caller to reset
     * the InputStream appropriately before calling a Subsequent API. If a copy
     * of the raw attachment content is required then the {@link #getRawContentBytes} API
     * should be used instead.
     *
     * @return an {@code InputStream} from which the raw data contained by
     *      the {@code AttachmentPart} can be accessed.
     *
     * @throws SOAPException if there is no content set into this
     *      {@code AttachmentPart} object or if there was a data
     *      transformation error.
     *
     * @since 1.6, SAAJ 1.3
     * @see #getRawContentBytes
     */
    public abstract InputStream getRawContent() throws SOAPException;

    /**
     * Gets the content of this {@code AttachmentPart} object as a
     * byte[] array as if a call had been made to {@code getContent} and no
     * {@code DataContentHandler} had been registered for the
     * {@code content-type} of this {@code AttachmentPart}.
     *
     * @return a {@code byte[]} array containing the raw data of the
     *      {@code AttachmentPart}.
     *
     * @throws SOAPException if there is no content set into this
     *      {@code AttachmentPart} object or if there was a data
     *      transformation error.
     *
     * @since 1.6, SAAJ 1.3
     */
    public abstract byte[] getRawContentBytes() throws SOAPException;

    /**
     * Returns an {@code InputStream} which can be used to obtain the
     * content of {@code AttachmentPart}  as Base64 encoded
     * character data, this method would base64 encode the raw bytes
     * of the attachment and return.
     *
     * @return an {@code InputStream} from which the Base64 encoded
     *       {@code AttachmentPart} can be read.
     *
     * @throws SOAPException if there is no content set into this
     *      {@code AttachmentPart} object or if there was a data
     *      transformation error.
     *
     * @since 1.6, SAAJ 1.3
     */
    public abstract InputStream getBase64Content() throws SOAPException;

    /**
     * Sets the content of this attachment part to that of the given
     * {@code Object} and sets the value of the {@code Content-Type}
     * header to the given type. The type of the
     * {@code Object} should correspond to the value given for the
     * {@code Content-Type}. This depends on the particular
     * set of {@code DataContentHandler} objects in use.
     *
     *
     * @param object the Java object that makes up the content for
     *               this attachment part
     * @param contentType the MIME string that specifies the type of
     *                  the content
     *
     * @exception IllegalArgumentException may be thrown if the contentType
     *            does not match the type of the content object, or if there
     *            was no {@code DataContentHandler} object for this
     *            content object
     *
     * @see #getContent
     */
    public abstract void setContent(Object object, String contentType);

    /**
     * Sets the content of this attachment part to that contained by the
     * {@code InputStream} {@code content} and sets the value of the
     * {@code Content-Type} header to the value contained in
     * {@code contentType}.
     * <P>
     *  A subsequent call to getSize() may not be an exact measure
     *  of the content size.
     *
     * @param content the raw data to add to the attachment part
     * @param contentType the value to set into the {@code Content-Type}
     * header
     *
     * @exception SOAPException if an there is an error in setting the content
     * @exception NullPointerException if {@code content} is null
     * @since 1.6, SAAJ 1.3
     */
    public abstract void setRawContent(InputStream content, String contentType) throws SOAPException;

    /**
     * Sets the content of this attachment part to that contained by the
     * {@code byte[]} array {@code content} and sets the value of the
     * {@code Content-Type} header to the value contained in
     * {@code contentType}.
     *
     * @param content the raw data to add to the attachment part
     * @param contentType the value to set into the {@code Content-Type}
     * header
     * @param offset the offset in the byte array of the content
     * @param len the number of bytes that form the content
     *
     * @exception SOAPException if an there is an error in setting the content
     * or content is null
     * @since 1.6, SAAJ 1.3
     */
    public abstract void setRawContentBytes(
        byte[] content, int offset, int len,  String contentType)
        throws SOAPException;


    /**
     * Sets the content of this attachment part from the Base64 source
     * {@code InputStream}  and sets the value of the
     * {@code Content-Type} header to the value contained in
     * {@code contentType}, This method would first decode the base64
     * input and write the resulting raw bytes to the attachment.
     * <P>
     *  A subsequent call to getSize() may not be an exact measure
     *  of the content size.
     *
     * @param content the base64 encoded data to add to the attachment part
     * @param contentType the value to set into the {@code Content-Type}
     * header
     *
     * @exception SOAPException if an there is an error in setting the content
     * @exception NullPointerException if {@code content} is null
     *
     * @since 1.6, SAAJ 1.3
     */
    public abstract void setBase64Content(
        InputStream content, String contentType) throws SOAPException;


    /**
     * Gets the {@code DataHandler} object for this {@code AttachmentPart}
     * object.
     *
     * @return the {@code DataHandler} object associated with this
     *         {@code AttachmentPart} object
     *
     * @exception SOAPException if there is no data in
     * this {@code AttachmentPart} object
     */
    public abstract DataHandler getDataHandler()
        throws SOAPException;

    /**
     * Sets the given {@code DataHandler} object as the data handler
     * for this {@code AttachmentPart} object. Typically, on an incoming
     * message, the data handler is automatically set. When
     * a message is being created and populated with content, the
     * {@code setDataHandler} method can be used to get data from
     * various data sources into the message.
     *
     * @param dataHandler the {@code DataHandler} object to be set
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified {@code DataHandler} object
     */
    public abstract void setDataHandler(DataHandler dataHandler);


    /**
     * Gets the value of the MIME header whose name is "Content-ID".
     *
     * @return a {@code String} giving the value of the
     *          "Content-ID" header or {@code null} if there
     *          is none
     * @see #setContentId
     */
    public String getContentId() {
        String[] values = getMimeHeader("Content-ID");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Gets the value of the MIME header whose name is "Content-Location".
     *
     * @return a {@code String} giving the value of the
     *          "Content-Location" header or {@code null} if there
     *          is none
     */
    public String getContentLocation() {
        String[] values = getMimeHeader("Content-Location");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Gets the value of the MIME header whose name is "Content-Type".
     *
     * @return a {@code String} giving the value of the
     *          "Content-Type" header or {@code null} if there
     *          is none
     */
    public String getContentType() {
        String[] values = getMimeHeader("Content-Type");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    /**
     * Sets the MIME header whose name is "Content-ID" with the given value.
     *
     * @param contentId a {@code String} giving the value of the
     *          "Content-ID" header
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified {@code contentId} value
     * @see #getContentId
     */
    public void setContentId(String contentId)
    {
        setMimeHeader("Content-ID", contentId);
    }


    /**
     * Sets the MIME header whose name is "Content-Location" with the given value.
     *
     *
     * @param contentLocation a {@code String} giving the value of the
     *          "Content-Location" header
     * @exception IllegalArgumentException if there was a problem with
     *            the specified content location
     */
    public void setContentLocation(String contentLocation)
    {
        setMimeHeader("Content-Location", contentLocation);
    }

    /**
     * Sets the MIME header whose name is "Content-Type" with the given value.
     *
     * @param contentType a {@code String} giving the value of the
     *          "Content-Type" header
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified content type
     */
    public void setContentType(String contentType)
    {
        setMimeHeader("Content-Type", contentType);
    }

    /**
     * Removes all MIME headers that match the given name.
     *
     * @param header the string name of the MIME header/s to
     *               be removed
     */
    public abstract void removeMimeHeader(String header);

    /**
     * Removes all the MIME header entries.
     */
    public abstract void removeAllMimeHeaders();


    /**
     * Gets all the values of the header identified by the given
     * {@code String}.
     *
     * @param name the name of the header; example: "Content-Type"
     * @return a {@code String} array giving the value for the
     *         specified header
     * @see #setMimeHeader
     */
    public abstract String[] getMimeHeader(String name);


    /**
     * Changes the first header entry that matches the given name
     * to the given value, adding a new header if no existing header
     * matches. This method also removes all matching headers but the first. <p>
     *
     * Note that RFC822 headers can only contain US-ASCII characters.
     *
     * @param   name    a {@code String} giving the name of the header
     *                  for which to search
     * @param   value   a {@code String} giving the value to be set for
     *                  the header whose name matches the given name
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void setMimeHeader(String name, String value);


    /**
     * Adds a MIME header with the specified name and value to this
     * {@code AttachmentPart} object.
     * <p>
     * Note that RFC822 headers can contain only US-ASCII characters.
     *
     * @param   name    a {@code String} giving the name of the header
     *                  to be added
     * @param   value   a {@code String} giving the value of the header
     *                  to be added
     *
     * @exception IllegalArgumentException if there was a problem with
     *            the specified mime header name or value
     */
    public abstract void addMimeHeader(String name, String value);

    /**
     * Retrieves all the headers for this {@code AttachmentPart} object
     * as an iterator over the {@code MimeHeader} objects.
     *
     * @return  an {@code Iterator} object with all of the Mime
     *          headers for this {@code AttachmentPart} object
     */
    public abstract Iterator<MimeHeader> getAllMimeHeaders();

    /**
     * Retrieves all {@code MimeHeader} objects that match a name in
     * the given array.
     *
     * @param names a {@code String} array with the name(s) of the
     *        MIME headers to be returned
     * @return  all of the MIME headers that match one of the names in the
     *           given array as an {@code Iterator} object
     */
    public abstract Iterator<MimeHeader> getMatchingMimeHeaders(String[] names);

    /**
     * Retrieves all {@code MimeHeader} objects whose name does
     * not match a name in the given array.
     *
     * @param names a {@code String} array with the name(s) of the
     *        MIME headers not to be returned
     * @return  all of the MIME headers in this {@code AttachmentPart} object
     *          except those that match one of the names in the
     *           given array.  The nonmatching MIME headers are returned as an
     *           {@code Iterator} object.
     */
    public abstract Iterator<MimeHeader> getNonMatchingMimeHeaders(String[] names);
}
